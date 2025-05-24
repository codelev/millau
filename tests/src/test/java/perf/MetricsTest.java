package perf;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsTest {
    // app
    static final String APP_NAME = "app";
    static final int APP_PORT = 9000;

    // proxy
    static final int PROXY_PORT = 8080;
    static final int HEALTHCHECK_PORT = 9100;

    // Docker
    GenericContainer<?> appContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    String apiUrl(String path) {
        return String.format("http://%s:%d%s", proxyContainer.getHost(), proxyContainer.getMappedPort(PROXY_PORT), path);
    }

    String metricsUrl() {
        return String.format("http://%s:%d/metrics", proxyContainer.getHost(), proxyContainer.getMappedPort(HEALTHCHECK_PORT));
    }

    String healthcheckUrl() {
        return String.format("http://%s:%d/", proxyContainer.getHost(), proxyContainer.getMappedPort(HEALTHCHECK_PORT));
    }

    @BeforeAll
    void setup() throws IOException {
        appContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_NAME,
                        "millau.enabled", "true",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/"
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appContainer.start();

        proxyContainer = new GenericContainer<>(DockerImageName.parse(proxyImage()))
                .withExposedPorts(PROXY_PORT, HEALTHCHECK_PORT)
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
        if (appContainer != null) {
            appContainer.stop();
        }
    }

    String proxyImage() {
        return "codelev/millau:latest";
    }

    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Test
    void get() throws Exception {
        assertMetrics(List.of(
                "millau_ingress_open_connections{port=\":443\",protocol=\"https\"} 0",
                "millau_ingress_open_connections{port=\":8080\",protocol=\"http\"} 0"
                ));
        assertHealthcheck();

        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(@NotNull ClientHttpResponse response) {
                return false;
            }
            @Override
            public void handleError(@NotNull ClientHttpResponse response) {
            }
        });
        
        // proxied 200
        ResponseEntity<String> response = client.exchange(apiUrl("/rest/echo"), HttpMethod.GET, null, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertMetrics(List.of(
                "millau_ingress_open_connections{port=\":443\",protocol=\"https\"} 0",
                "millau_ingress_open_connections{port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_requests_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 1",
                "millau_ingress_requests_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_responses_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 306",
                
                "millau_lb_request_duration_seconds_count{method=\"GET\",service=\"app\"} 1",
                "millau_lb_requests_bytes_total{method=\"GET\",service=\"app\"} 0",
                "millau_lb_responses_bytes_total{method=\"GET\",service=\"app\"} 306",
                "millau_lb_status{service=\"app\"} 1",
                "millau_lb_successful_requests_total{code=\"200\",method=\"GET\",service=\"app\"} 1"
        ));
        assertHealthcheck();

        // proxied 404
        response = client.exchange(apiUrl("/rest/none"), HttpMethod.GET, null, String.class);
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertMetrics(List.of(
                "millau_ingress_open_connections{port=\":443\",protocol=\"https\"} 0",
                "millau_ingress_open_connections{port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_requests_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 2",
                "millau_ingress_requests_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_responses_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 404",
                
                "millau_lb_request_duration_seconds_count{method=\"GET\",service=\"app\"} 2",
                "millau_lb_requests_bytes_total{method=\"GET\",service=\"app\"} 0",
                "millau_lb_responses_bytes_total{method=\"GET\",service=\"app\"} 404",
                "millau_lb_status{service=\"app\"} 1",
                "millau_lb_successful_requests_total{code=\"200\",method=\"GET\",service=\"app\"} 1",
                "millau_lb_successful_requests_total{code=\"404\",method=\"GET\",service=\"app\"} 1"
        ));
        assertHealthcheck();

        // not proxied
        appContainer.stop();
        response = client.exchange(apiUrl("/rest/none"), HttpMethod.GET, null, String.class);
        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertMetrics(List.of(
                "millau_ingress_open_connections{port=\":443\",protocol=\"https\"} 0",
                "millau_ingress_open_connections{port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_requests_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 3",
                "millau_ingress_requests_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 0",
                "millau_ingress_responses_bytes_total{method=\"GET\",port=\":8080\",protocol=\"http\"} 404",

                "millau_lb_request_duration_seconds_count{method=\"GET\",service=\"app\"} 2",
                "millau_lb_requests_bytes_total{method=\"GET\",service=\"app\"} 0",
                "millau_lb_responses_bytes_total{method=\"GET\",service=\"app\"} 404",
                "millau_lb_status{service=\"app\"} 0",
                "millau_lb_successful_requests_total{code=\"200\",method=\"GET\",service=\"app\"} 1",
                "millau_lb_successful_requests_total{code=\"404\",method=\"GET\",service=\"app\"} 1",
                "millau_lb_failed_requests_total{method=\"GET\",service=\"app\"} 1"
        ));
        assertHealthcheck();
    }

    private void assertMetrics(List<String> expectations) {
        RestTemplate client = new RestTemplate();
        ResponseEntity<String> response = client.exchange(metricsUrl(), HttpMethod.GET, null, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        for (String expectation: expectations) {
            assertTrue(response.getBody().contains(expectation), response.getBody());
        }
    }

    private void assertHealthcheck() {
        RestTemplate client = new RestTemplate();
        ResponseEntity<String> response = client.exchange(healthcheckUrl(), HttpMethod.GET, null, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("up", response.getBody());
    }
}
