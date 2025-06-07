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
class FallbackSwarmTest {
    static final String STACK_NAME = "fallback";
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
    void downscaled() throws Exception {
        RestTemplate client = new RestTemplate();
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("main\n", response.getBody());
        }

        downscaleService();

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("fallback\n", response.getBody());
        }
    }

    @Test
    void removed() throws Exception {
        RestTemplate client = new RestTemplate();
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("main\n", response.getBody());            
        }

        removeService();

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = client.exchange(url(), HttpMethod.GET, null, String.class);
            assertEquals(200, response.getStatusCode().value());
            assertEquals("fallback\n", response.getBody());
        }
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

    private void downscaleService() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "service", "scale", STACK_NAME + "_main=0");
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
        Thread.sleep(2_000);
    }

    private void removeService() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("docker", "service", "rm", STACK_NAME + "_main");
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
        Thread.sleep(2_000);
    }
}
