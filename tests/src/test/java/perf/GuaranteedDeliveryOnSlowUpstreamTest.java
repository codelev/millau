package perf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuaranteedDeliveryOnSlowUpstreamTest {
    // app
    private static final Pattern INSTANCE_ID = Pattern.compile("ID:\\s([a-f0-9\\-]{36})", Pattern.CASE_INSENSITIVE);

    static final String APP_A_NAME = "app-a";
    static final String APP_B_NAME = "app-b";
    static final int APP_PORT = 9000;
    static final String APP_ENDPOINT = "/rest/echo";

    // proxy
    static final int PROXY_PORT = 8080;

    // Docker
    GenericContainer<?> appAContainer;
    GenericContainer<?> appBContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    String url() {
        return String.format("http://%s:%d%s", proxyContainer.getHost(), proxyContainer.getMappedPort(PROXY_PORT), APP_ENDPOINT);
    }

    @BeforeAll
    void setup() throws IOException {
        appAContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_A_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_A_NAME,
                        "millau.enabled", "true",
                        "millau.timeout", "2",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/"
                ))
                .withEnv("DELAY", "1")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appAContainer.start();

        appBContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_B_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_B_NAME,
                        "millau.enabled", "true",
                        "millau.timeout", "2",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/"
                ))
                .withEnv("DELAY", "3")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appBContainer.start();

        proxyContainer = new GenericContainer<>(DockerImageName.parse(proxyImage()))
                .withExposedPorts(PROXY_PORT)
                .withNetwork(NETWORK)
                .withEnv("HTTP", ":" + PROXY_PORT)
                .withEnv("LOGGING", "DEBUG")
                .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_ONLY)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        proxyContainer.start();
    }

    @AfterAll
    void tearDown() {
        if (proxyContainer != null) {
            proxyContainer.stop();
        }
        if (appAContainer != null) {
            appAContainer.stop();
        }
        if (appBContainer != null) {
            appBContainer.stop();
        }
    }

    String proxyImage() {
        return "codelev/millau:test";
    }

    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Test
    void get() {
        int requests = 10;
        ConcurrentHashMap<String, Integer> stats = new ConcurrentHashMap<>(2);
        RestTemplate client = new RestTemplate();
        for (int i = 0; i < requests; i++) {
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            stats.compute(getInstanceId(response.getBody()), (k, v) -> v == null ? 1 : v + 1);
        }

        int responses = stats.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(requests, responses);
        assertEquals(1, stats.size()); // 1 responding upstream
    }

    private String getInstanceId(String responseBody) {
        Matcher matcher = INSTANCE_ID.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
