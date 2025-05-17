package perf;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MTLSTest {
    // app
    private static final Pattern INSTANCE_ID = Pattern.compile("ID:\\s([a-f0-9\\-]{36})", Pattern.CASE_INSENSITIVE);

    static final String APP_NAME = "app";
    static final int APP_PORT = 9000;
    static final String APP_ENDPOINT = "/rest/echo";
    static final String TLS_KEY = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRQytmbWdPa3F0dnVFejEKSGRNdTVmZVhyZ2xCUEhRNExwaVFPWmRZOFVrNFA1akdyKysvV3NhYzl5SDFMYW9KMUhhSHZMZmplbkRzMkhZWQpCTTZOd2ZycXNIRmdvWlZIMGJpTmVZTmt5aEVkdGFMdmY0RUZGZWlPWjZGc0cwMDFjRldUYlVvRUx0QVphbzNhCjFRald4ZzFXT241cFJlaHRpbk1nR0VsbFlpT1VBMGxvaENyc3JNVlhBczF2am5mZTIzNlNFMWQ1MXlnTEFHZjMKS1k4YjE5ZGVCS2dzejczNnJ4QnRaTzAzWStlSk5IZERYakRFUHJ5cnZ3bmRBY0lOeDdQbE1KRjlDNEhPcXhwRgpOdmhXeFlkMEd5dTBES24rUXZobWI2SHBSY0d2bnNVU3VMV0pqa09RMS9WQXNZMWtYNSttWDVudjFRVnhkbmtmCmhITVlncFdwQWdNQkFBRUNnZ0VBQXl6Y09aSDE1WmM3cDQ4YWduUnFEdWpKdkUzVzNVcDVpdkVYbkZYc2VoNVkKZGZxVExOOWU0b3lFQXpBMXEyQkcxK240KzRPMXc4Tk10OVFzcnRRNVVoVytCNmtkQzM2ZkJobDVuczUzYzE3cwo1L042SWQvNEF6RkJheW5raVpKTHg2d2VTT3hkUEc4NFFJQllqcEpTamNuTW5aOEJGczhrWWJnc1VRV0JXeDBECmgzRi9uZFI3WVVSVThMRGEzUlZGd0NoUWNDZ0I3U2Zkb2QrVEJibFVteEI0YUVTMkVRRHZPY0tKTU1hQ3VGWEwKellGeEdCaTh4UThTNTEyMU9JaVlzTkNCVnNrSFl4Wll6UWszdjdkd29iM1JRTDhuWUQ2LzZ3SEVnOGtjTW1BcgppcHNZcHNZY1VXbktoMWtQYzA2MnAvaW9udGFobWxOTGJnbU8wOGpQQVFLQmdRRHVGUEdLM2FKeWN3K3d5UU95CnBaMjYyT0xTdmUydS9uaTBrWWNGaEdTWjh2N0w5OEdCdG9ZN0E2ZjhiZVZaSW9BT3hBYzhqeXFEUDFtTDFlUisKT2x1clk1ekxVWU9VTjcxcGU1K3Y0UXpXb3hRdFN2eE43azN6Y1Rnd1Qva0FnZHQ5c2Yxd0ZOZkVWcG5QR0x6eQpEdEY2eWpLbWxNUDc4bUV1ZWRQaGRMbHdjUUtCZ1FETTFKandSTks4Qjd0Sjk4U0dKdTRzTUNEcGZJaTNITXVwCi9aVmtRcmkzZDRUbjhGMUZ0UGxCSkxWbi94YlA2ZWxXa2N1MGtzTm45eXFLSEZmbXg5ZGJzbEovQUZNMDVic3gKRXB2aE9PTkNsWlhtUi9qOWJ2cUMvK0g0OStCTm9EbXdwdUxReUNZcU9YZWYxOC9FbGpKSjZlMmgwOG9wUExHRQpjL2Jia08rVXVRS0JnUUNJMFdSVnB6U1pqT0h4ZURNMTBOTXA4MFcyVWd0clN6WFdudUwzR3JRdGZHVk1sZDZRClNuSXRLOWEzeS9mSEYzcDhBYzhlMEM5Z0tXR3VhSWJjdTNDK3Q0bjlsYVNGNHRwbzZmQkV4Skg2THRHRGpkb2MKZHR3NUVGRjRBaDFZVzBmbG5nbkZCVlZSc1dyR2hyTGVjQUdXRE9pNnJqZEtiR25JcVo1SHJDcDlJUUtCZ0NEWQpVbkhzeDFJSmQxbmtGaXBnNGI1S05XemJZRnprakRBRkRzaVYvbUxGRXBYU3NGSTJNK1hqU2dlVUd3ZFovZVc0CjJVNXFYbFUwaUNpL1pNVUg5SnVxbTVucjVtdk1EdGxPbjVwYzhleENhbGdUNEhSYk1HYURPNndkcTJVbk5Ua0QKZWNsNjNzdlVqVDhmYnh5WjdSUjNJM2pZcWtrMGgwNnkvYm0xb3dWWkFvR0FQRXg5QWxpOTl1RXAybnFzT2dBSQpHT1F5SXdiT21LQzVYY0lrbFpNdWF1WENCU2hLZVNaZHZXZ3ExRlRvQXU2Y2JYRUpNRXlqK3l1QlIxMnFIa25pCm1vcm9xUTJ4ZnJRVUNOVnBKSjMrdzZEVzNqV2NPTVJGaFlvZDVDQmlvOXBWZWcxOTYwMVcwR2hhR1c3Qm11cDYKK1J4M3lPbXZNZ2pSRXc0OVVhRk9GNDQ9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    static final String TLS_CERT = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURPVENDQWlFQ0ZIU200RG11cWp2VjdaYUNsT1NXcVE5QWZiRjBNQTBHQ1NxR1NJYjNEUUVCQ3dVQU1Ga3gKQ3pBSkJnTlZCQVlUQWxWVE1STXdFUVlEVlFRSURBcFRiMjFsTFZOMFlYUmxNU0V3SHdZRFZRUUtEQmhKYm5SbApjbTVsZENCWGFXUm5hWFJ6SUZCMGVTQk1kR1F4RWpBUUJnTlZCQU1NQ1d4dlkyRnNhRzl6ZERBZUZ3MHlOVEExCk1EWXdOalUwTkRWYUZ3MHlOakExTURZd05qVTBORFZhTUZreEN6QUpCZ05WQkFZVEFsVlRNUk13RVFZRFZRUUkKREFwVGIyMWxMVk4wWVhSbE1TRXdId1lEVlFRS0RCaEpiblJsY201bGRDQlhhV1JuYVhSeklGQjBlU0JNZEdReApFakFRQmdOVkJBTU1DV3h2WTJGc2FHOXpkRENDQVNJd0RRWUpLb1pJaHZjTkFRRUJCUUFEZ2dFUEFEQ0NBUW9DCmdnRUJBTDUrYUE2U3EyKzRUUFVkMHk3bDk1ZXVDVUU4ZERndW1KQTVsMWp4U1RnL21NYXY3NzlheHB6M0lmVXQKcWduVWRvZTh0K042Y096WWRoZ0V6bzNCK3Vxd2NXQ2hsVWZSdUkxNWcyVEtFUjIxb3U5L2dRVVY2STVub1d3YgpUVFZ3VlpOdFNnUXUwQmxxamRyVkNOYkdEVlk2Zm1sRjZHMktjeUFZU1dWaUk1UURTV2lFS3V5c3hWY0N6VytPCmQ5N2JmcElUVjNuWEtBc0FaL2Nwanh2WDExNEVxQ3pQdmZxdkVHMWs3VGRqNTRrMGQwTmVNTVErdkt1L0NkMEIKd2czSHMrVXdrWDBMZ2M2ckdrVTIrRmJGaDNRYks3UU1xZjVDK0dadm9lbEZ3YStleFJLNHRZbU9RNURYOVVDeApqV1JmbjZaZm1lL1ZCWEYyZVIrRWN4aUNsYWtDQXdFQUFUQU5CZ2txaGtpRzl3MEJBUXNGQUFPQ0FRRUFpdWhsCkxISXlGNDE0S2RBR1Q4OW5ESk5sS0V3bFAvKzF2V3NSUkU5TmJ2Y2wrNWJJaHRqTkpTNnA3UWoreDJXMG9PeEUKWU9jS0J1Q0djc3plWTQ1MjRCa2hpQmlLdXJSSUpUK0pVSHo3aHJrQ3hkK2Y1RTRRMUJlZVJwR2V0dW1qT290Ygo3cmlwblhoNUtYcDFYVGl4ZXBnWEZPRkZ6RDVDR2lTUFBFeTdpS2hTY1FiNlNjQ3BvTEh2anR1VFhVYnUyNmlvCnEvbE5seVJpNGZjaWIxTWpSZlZ3WkN0V25zMUtEei9vVVlwZWNtYnVFWFF1UmluRllDR3lpSlcvdmRlUjhkWXAKaHZTaHpFQWlxL2N3NXJiWHQvamNrV2JJRHZMWWhIdjF4NUdsNStJenozRkN1L1JjeW5qUmhjWWpFZGZyVGtDYwpGajJ6T056cHYzKzA5WGxiVFE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==";

    // proxy
    static final int PROXY_PORT = 8443;

    // Docker
    GenericContainer<?> appAContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    String url() {
        return String.format("https://%s:%d%s", proxyContainer.getHost(), proxyContainer.getMappedPort(PROXY_PORT), APP_ENDPOINT);
    }

    @BeforeAll
    void setup() throws IOException {
        appAContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_NAME,
                        "millau.enabled", "true",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/",
                        "millau.hosts", "localhost",
                        "millau.cert", TLS_CERT,
                        "millau.key", TLS_KEY
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appAContainer.start();

        proxyContainer = new GenericContainer<>(DockerImageName.parse(proxyImage()))
                .withExposedPorts(PROXY_PORT)
                .withNetwork(NETWORK)
                .withEnv("HTTPS", ":" + PROXY_PORT)
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
    }

    String proxyImage() {
        return "codelev/millau:latest";
    }

    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Test
    void get() throws Exception {
        int requests = 10;
        ConcurrentHashMap<String, Integer> stats = new ConcurrentHashMap<>(2);
        HttpClient client = client();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url()))
                .GET()
                .build();
        for (int i = 0; i < requests; i++) {
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertNotNull(response.body());
            stats.compute(getInstanceId(response.body()), (k, v) -> v == null ? 1 : v + 1);
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

    public static HttpClient client() throws Exception {
        byte[] certPem = Base64.getDecoder().decode(TLS_CERT);
        X509Certificate cert = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certPem));
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", cert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParams)
                .build();
    }
}
