package perf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScaleSwarmTest {
    // app
    private static final Pattern INSTANCE_ID = Pattern.compile("ID:\\s([a-f0-9\\-]{36})", Pattern.CASE_INSENSITIVE);

    static final String STACK_NAME = "lb";
    static final String APP_ENDPOINT = "/rest/echo";
    static final int PROXY_PORT = 8080;

    String url() {
        return String.format("http://localhost:%d%s", PROXY_PORT, APP_ENDPOINT);
    }

    @BeforeAll
    void setup() throws Exception {
        deployStack();
    }

    @AfterAll
    void tearDown() throws Exception {
        removeStack();
    }

    @Test
    void get() throws Exception {
        int requests = 15;
        ConcurrentHashMap<String, Integer> stats = new ConcurrentHashMap<>(2);
        RestTemplate client = new RestTemplate();
        for (int i = 0; i < requests; i++) {
            if (i == requests / 3) {
                setReplicas(3);
            }
            if (i == requests / 6) {
                setReplicas(1);
            }
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            stats.compute(getInstanceId(response.getBody()), (k, v) -> v == null ? 1 : v + 1);
        }

        int responses = stats.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(requests, responses);
        assertEquals(4, stats.size(), "" + stats); // 4 responding upstreams
    }

    private String getInstanceId(String responseBody) {
        Matcher matcher = INSTANCE_ID.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void deployStack() throws Exception {
        URL resource = getClass().getClassLoader().getResource("docker-compose." + STACK_NAME + ".yml");
        Path path = Paths.get(resource.getPath());
        ProcessBuilder pb = new ProcessBuilder("docker", "stack", "deploy", "-c", path.toString(), STACK_NAME);
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        Thread.sleep(10_000);
    }

    private void removeStack() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "stack", "rm", STACK_NAME);
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();
        Thread.sleep(5_000);
    }

    private void setReplicas(int replicas) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "service", "scale", STACK_NAME + "_echo=" + replicas);
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
        Thread.sleep(2_000);
    }
}
