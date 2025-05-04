package perf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class PerfTest {
    @Value("${rest.get.sequential:500}")
    private int getSequential;
    @Value("${rest.get.parallel:100}")
    private int getParallel;
    @Value("${rest.post.sequential:500}")
    private int postSequential;
    @Value("${rest.post.parallel:100}")
    private int postParallel;

    // POST body in bytes
    private static final byte[] BODY = new byte[5_000_000];

    // app
    private static final RestTemplate CLIENT = new RestTemplate();

    static final String APP_NAME = "app";
    static final int APP_PORT = 9000;
    static final String APP_ENDPOINT = "/rest/echo";

    // proxy
    static final int PROXY_PORT = 8080;

    // Docker
    GenericContainer<?> appContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    abstract String proxyImage();

    abstract String appImage();

    abstract String proxyConfigFile();

    abstract String proxyConfig();

    String url() {
        return String.format("http://%s:%d%s", proxyContainer.getHost(), proxyContainer.getMappedPort(PROXY_PORT), APP_ENDPOINT);
    }

    @BeforeAll
    void setup() throws IOException {
        new Random().nextBytes(BODY);

        appContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_NAME,
                        "millau.enabled", "true",
                        "millau.port", "" + APP_PORT
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appContainer.start();

        if (!appImage().equals(proxyImage())) {
            proxyContainer = new GenericContainer<>(DockerImageName.parse(proxyImage()))
                    .withExposedPorts(PROXY_PORT)
                    .withNetwork(NETWORK)
                    .withEnv("HTTP", ":" + PROXY_PORT)
                    .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_ONLY);

            if (proxyConfig() != null) {
                String configFile = "target/config";
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write(proxyConfig());
                }
                proxyContainer.withCopyFileToContainer(MountableFile.forHostPath(configFile), proxyConfigFile());

            }

            proxyContainer.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
            proxyContainer.start();
        }
    }

    @AfterAll
    void tearDown() {
        if (proxyContainer != null) {
            proxyContainer.stop();
        }
        if (appContainer != null) {
            appContainer.stop();
        }
    }

    @Test
    void echo() throws Exception {
        ResponseEntity<String> response = CLIENT.exchange(url(), HttpMethod.GET, null, String.class);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void headers() {
        if (proxyContainer == null) {
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("cf-connecting-ip", "100.100.100.100");
        ResponseEntity<String> response = CLIENT.exchange(url(), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertNotNull(response.getBody());
        String ip = proxyContainer.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getGateway();
        assertTrue(response.getBody().contains("host: localhost"), response.getBody());
        assertTrue(response.getBody().contains(String.format("x-forwarded-for: %s%n", ip)), response.getBody());
        assertTrue(response.getBody().contains("x-forwarded-proto: http\n"), response.getBody());
        assertTrue(response.getBody().contains("cf-connecting-ip: 100.100.100.100\n"), response.getBody());
    }

    @Test
    void get() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<byte[]> entity = new HttpEntity<>(null, headers);
        sequential(HttpMethod.GET, entity, getSequential);
        parallel(HttpMethod.GET, entity, getParallel);
    }

    @Test
    void post() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/octet-stream");
        HttpEntity<byte[]> entity = new HttpEntity<>(BODY, headers);
        sequential(HttpMethod.POST, entity, postSequential);
        parallel(HttpMethod.POST, entity, postParallel);
    }

    private void sequential(HttpMethod method, HttpEntity<byte[]> entity, int requests) throws Exception {
        AtomicLongArray statsMicro = new AtomicLongArray(requests);
        for (int i = 0; i < requests; i++) {
            long start = System.nanoTime();
            ResponseEntity<String> response = CLIENT.exchange(url(), method, entity, String.class);
            long end = System.nanoTime();
            assertEquals(200, response.getStatusCode().value());
            statsMicro.set(i, (end - start) / 1_000);
        }
        report(statsMicro, method, entity, "Sequential");
    }

    private void parallel(HttpMethod method, HttpEntity<byte[]> entity, int requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        AtomicLongArray statsMicro = new AtomicLongArray(requests);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    long start = System.nanoTime();
                    ResponseEntity<String> response = CLIENT.exchange(url(), method, entity, String.class);
                    long end = System.nanoTime();
                    assertEquals(200, response.getStatusCode().value());
                    statsMicro.set(index, (end - start) / 1_000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, executor);
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        report(statsMicro, method, entity, "Parallel");
    }

    private void report(AtomicLongArray statsMicro, HttpMethod method, HttpEntity<byte[]> entity, String title) {
        double longestMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .max()
                .orElseThrow();
        double shortestMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .min()
                .orElseThrow();
        double averageMicro = IntStream.range(0, statsMicro.length())
                .mapToLong(statsMicro::get)
                .average()
                .orElseThrow();

        long bodySize = 0;
        if (method.equals(HttpMethod.POST) && entity.getBody() != null) {
            bodySize = entity.getBody().length;
        }
        String contentType = entity.getHeaders().get("Content-Type").getFirst();

        StringBuilder result = new StringBuilder();
        result.append(String.format("### %s %s requests: %s%n%n", title, method, proxyImage()));
        result.append(String.format("`%s %s %s %dMb` x %d%n%n", method, url(), contentType, bodySize / 1_000_000, statsMicro.length()));
        result.append("| Metric               | Value         |\n");
        result.append("|----------------------|---------------|\n");
        result.append(String.format("| Longest response     | %.2f ms       |%n", longestMicro / 1_000));
        result.append(String.format("| Shortest response    | %.2f ms       |%n", shortestMicro / 1_000));
        result.append(String.format("| Average response     | %.2f ms       |%n", averageMicro / 1_000));
        System.out.println(result);
    }
}
