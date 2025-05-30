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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
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
class TLSTest {
    // app
    private static final Pattern INSTANCE_ID = Pattern.compile("ID:\\s([a-f0-9\\-]{36})", Pattern.CASE_INSENSITIVE);

    static final String APP_NAME = "app";
    static final int APP_PORT = 9000;
    static final String APP_ENDPOINT = "/rest/echo";
    static final String WILDCARD_TLS_KEY = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRQzYwVVRQK2YraWF5TDcKbFViRGRwQUkrM2dmZXplbjBOSVI4R0tkVDVYOTdKOGM2enBQK3QyTksyN1JSaXhyam40amJhb0lHRzM3UEJlRQpVOE1vbmw2SFRDcXloaWwyMFcyaUpKaHRXbGhENXdGU1Z4am90V0FaVUhXNWFCZEp4WFltNnZXSGZnYm83ZCt6CjZaMFFIVWo2S2pxbU1LY1I1YVNhVW0zSXB4dHBvUUFpYloyUkNiRHo4U3pzNVRwMEFpU3dEaVNtVUIyQ3B0c3oKUFpXd3FsVHNlLzArWmRzK1pJYUowZ2UycVNHeTNjZ0FLdlVicXk0SGNSWXRvT0pQNDg5THk5bi9DQlZscmd5ego1aThiYmNtR1hlMUlEeUkzY2pVZDh3MzBOWGVOd2RFUGgzTXdaVDdkRXBDRjRMeTJmSnVaY0gzcDlxU0Y1dkhUCkU4VWxSdVdWQWdNQkFBRUNnZ0VBSHg4cktVR0N6R1l3TlB5c2hmWDdyejlqUnMxU1czN09iQ1loYTRiOVhBaWUKQmlXS3VKVzRjR0xXcW43WCtoQUtJL0ZLTk9DSysrSktJYVdKbXJ2dFQvbEltS2FBaEZhRnZzLzlZNlE4RlkzSApldFhmSks5SWc5Y1RVWkNOWnFFQ1dwL2tTTTdlTEZJcVpQQ3JWV3FuaEdOaUUxc0NEaGwxVnk4WnNtWmlTdkY1CldTMEYvMVNTV3BrNlFLUTZQd0pEalV6d2FMWTRQUlpXK1NYYnBhdWZ0d3UzY1h4SFc5bndQeWNhTnh4SzRVNFEKSi9uWmFIQ1VGelRDOHhERlNBeCt0OUhMS2FyVHFsY3pVRDFCWmxJSlllZjVoMWpxajR1TFd4cEhSSWZQRGliZwp5Y2s3MEhaSzBlYndQTktnNnRwK0NYa3NNSUJvU0FjQmZvMlk2eldxMlFLQmdRQzdtaldsU1NDVC9RTnk3UFIyClRDd0NHN0swaWFpc2ZWNWRLU1EwcEszRmZSRVBIMTd2OGUyWkRXZTRBdjByQS9LdHdhSjhnZXRoQUI2TkNRM2MKc0RNSHNWQmlTMmlCcVQ5SElweTgvQTE0NmZEbzRvZkJaaWNWTUpiUnp2WWsvaDVWdnRwSWtpOUljUEhEZ3hQNApUdW5SMkgrZlVQODJjLy9KRHh1YWJnUkdIUUtCZ1FEKzdjeHgyY3dDZ1Z1d2tLT1BidGZCNXRSK3pmRVpUbFZ3CnF5SHl3NXVYOHVGOVdWSE4zTXJHajJIbkJsVHhOalFJWWIxKzZEM3RZVmZMME11TURrUGM4NDRwZGxlVDhYT3YKdGFFNFowVDdQbTZLOXBwdEVHL0ZQb2RJWWszbU9lUUFHLzUyNGxNc0dLV2hlcmRqZ0FPbGFuV2xhOVVsNi9zUQo3ZDV4NjUrajJRS0JnQldjdUYxb1JVYUhPV1IrQTlPMUJzVGZhQUVBY3R3Y1BVakNPcGVOK1M3dEw0L3NiMFY1Ck80UU1WVXlQbTViZkI1QVk1SjB2WW14MFZSQ3VZRmh5UEE3QTBKL2lUQkhUNjZSSVNvSkREMUpFc1NwREhweWIKSmhsODFZTXFNcFVrYVY0N2RHNHoyUnFoV0ZqTHV2czZMQm11dFZVblFaK0dVWXRhSURYMFFxM0pBb0dBSEkwdApsS0s4QXB2U1ZSZ2QrWGFFbTZicXJia0xBN2FPUXl3bmhUVDdQQzFycThwUkt5bExYS202WVZHSU9ldkVNQndpClNSQmh2ekJqME9QMXFCNEE0OEl6YmRsZlBhYVJPbUN4U2N2bklleUFIUGc1bTNWM3p6T05tMEhIVDcyMEYzOEwKSk8xOE96Z1hkTnAxcDZNeXhWZ0REUi9pbzNpbWllTGFReEFNdFNFQ2dZRUF1VWl1NmtuV0RtdFR4REp1dWlVTAplOGJHVEpWcDF3bkgrYWlaMG9TMHVMbFIzd2g1NVY5WjFJSjVvTG1SN0RKVUxzY0srNlZWalg4QVRTY2RnTUR4CldKdTdVNjk4NFc2VXloSWFuUTdIVi8yR080NXlPeEs0UytFNThlSW1rOGhkdnJVSkNtZGY4d0hjODMydC8wd00KSVI2cGVTd1pnaEoxckgrSTIyeVEvN1k9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    static final String WILDCARD_TLS_CERT = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURQVENDQWlVQ0ZHNXJRMmNILzlIWmZBMEdKLzVZVXFFSWdGN1FNQTBHQ1NxR1NJYjNEUUVCQ3dVQU1Gc3gKQ3pBSkJnTlZCQVlUQWtSRk1STXdFUVlEVlFRSURBcFRiMjFsTFZOMFlYUmxNU0V3SHdZRFZRUUtEQmhKYm5SbApjbTVsZENCWGFXUm5hWFJ6SUZCMGVTQk1kR1F4RkRBU0JnTlZCQU1NQ3lvdWJHOWpZV3hvYjNOME1CNFhEVEkxCk1EVXpNREUxTWpBME4xb1hEVEkyTURVek1ERTFNakEwTjFvd1d6RUxNQWtHQTFVRUJoTUNSRVV4RXpBUkJnTlYKQkFnTUNsTnZiV1V0VTNSaGRHVXhJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MApaREVVTUJJR0ExVUVBd3dMS2k1c2IyTmhiR2h2YzNRd2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJEd0F3CmdnRUtBb0lCQVFDNjBVVFArZitpYXlMN2xVYkRkcEFJKzNnZmV6ZW4wTklSOEdLZFQ1WDk3SjhjNnpwUCt0Mk4KSzI3UlJpeHJqbjRqYmFvSUdHMzdQQmVFVThNb25sNkhUQ3F5aGlsMjBXMmlKSmh0V2xoRDV3RlNWeGpvdFdBWgpVSFc1YUJkSnhYWW02dldIZmdibzdkK3o2WjBRSFVqNktqcW1NS2NSNWFTYVVtM0lweHRwb1FBaWJaMlJDYkR6CjhTenM1VHAwQWlTd0RpU21VQjJDcHRzelBaV3dxbFRzZS8wK1pkcytaSWFKMGdlMnFTR3kzY2dBS3ZVYnF5NEgKY1JZdG9PSlA0ODlMeTluL0NCVmxyZ3l6NWk4YmJjbUdYZTFJRHlJM2NqVWQ4dzMwTlhlTndkRVBoM013WlQ3ZApFcENGNEx5MmZKdVpjSDNwOXFTRjV2SFRFOFVsUnVXVkFnTUJBQUV3RFFZSktvWklodmNOQVFFTEJRQURnZ0VCCkFJTTJOaWw2TDVSTnUwdmh3eGUxbDdVbTdxNWdnNUkrSkt2SEVSTzNVZkFsS2JNT1diYzdFb3A0c1lhVkJuSHIKQ1JFNENjTUNxV2cveHlVdDEzSys5c3o2cFlxenh6OEpGWC9hUXB5SmZVcDVzaFBKTzl2ejlma2p1WU5STE1hWQp1dlZ5c2FyNlZOZ1RqeFhlYVh5enA1amFRTkRmN0Jid2xRdExYcDk0cUJ1ekVCSFBzZGNibzBuL2RLeFFaUlY3Ck0yQS9sQk5kcTJpUElaaHNUeXBzUzZjKzhXaGJ6LzUralBYMlFRYTl6WTYyZ0lOSkhQb3ltb29qSmEwWUJlbWcKbEFjWWo1eHJ0TXZGbU9ia3JaYXdXbS9ZVFJDUFRRejRKMjlQRDUwYndETFZ0VDFUM2dOTERTVXFPL3BhY3BwVgp6NWFWTk8vVmE1NzZNQ3pnNmVNN25oST0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=";

    // proxy
    static final int PROXY_PORT = 8443;

    // Docker
    GenericContainer<?> appAContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    String url() {
        return String.format("https://sub.localhost:%d%s", proxyContainer.getMappedPort(PROXY_PORT), APP_ENDPOINT);
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
                        "millau.cert", WILDCARD_TLS_CERT,
                        "millau.key", WILDCARD_TLS_KEY
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
        return "codelev/millau:test";
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
        // certificate
        X509Certificate cert = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(WILDCARD_TLS_CERT)));
        String pemKey = new String(Base64.getDecoder().decode(WILDCARD_TLS_KEY));
        String privateKeyPEM = pemKey
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPEM));
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);

        // keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, null, new java.security.cert.Certificate[]{cert});
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", cert);
        KeyManagerFactory keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManager.init(keyStore, null);
        TrustManagerFactory trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManager.init(trustStore);
        
        // SSL
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManager.getKeyManagers(), trustManager.getTrustManagers(), null);
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
    }
}
