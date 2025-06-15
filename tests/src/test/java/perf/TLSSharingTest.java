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
class TLSSharingTest {
    // app
    private static final Pattern INSTANCE_ID = Pattern.compile("ID:\\s([a-f0-9\\-]{36})", Pattern.CASE_INSENSITIVE);

    static final String APP_A_NAME = "app-a";
    static final String APP_B_NAME = "app-b";
    static final String APP_C_NAME = "app-c";
    static final int APP_PORT = 9000;
    static final String APP_ENDPOINT = "/rest/echo";
    static final String TLS_KEY = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRQytmbWdPa3F0dnVFejEKSGRNdTVmZVhyZ2xCUEhRNExwaVFPWmRZOFVrNFA1akdyKysvV3NhYzl5SDFMYW9KMUhhSHZMZmplbkRzMkhZWQpCTTZOd2ZycXNIRmdvWlZIMGJpTmVZTmt5aEVkdGFMdmY0RUZGZWlPWjZGc0cwMDFjRldUYlVvRUx0QVphbzNhCjFRald4ZzFXT241cFJlaHRpbk1nR0VsbFlpT1VBMGxvaENyc3JNVlhBczF2am5mZTIzNlNFMWQ1MXlnTEFHZjMKS1k4YjE5ZGVCS2dzejczNnJ4QnRaTzAzWStlSk5IZERYakRFUHJ5cnZ3bmRBY0lOeDdQbE1KRjlDNEhPcXhwRgpOdmhXeFlkMEd5dTBES24rUXZobWI2SHBSY0d2bnNVU3VMV0pqa09RMS9WQXNZMWtYNSttWDVudjFRVnhkbmtmCmhITVlncFdwQWdNQkFBRUNnZ0VBQXl6Y09aSDE1WmM3cDQ4YWduUnFEdWpKdkUzVzNVcDVpdkVYbkZYc2VoNVkKZGZxVExOOWU0b3lFQXpBMXEyQkcxK240KzRPMXc4Tk10OVFzcnRRNVVoVytCNmtkQzM2ZkJobDVuczUzYzE3cwo1L042SWQvNEF6RkJheW5raVpKTHg2d2VTT3hkUEc4NFFJQllqcEpTamNuTW5aOEJGczhrWWJnc1VRV0JXeDBECmgzRi9uZFI3WVVSVThMRGEzUlZGd0NoUWNDZ0I3U2Zkb2QrVEJibFVteEI0YUVTMkVRRHZPY0tKTU1hQ3VGWEwKellGeEdCaTh4UThTNTEyMU9JaVlzTkNCVnNrSFl4Wll6UWszdjdkd29iM1JRTDhuWUQ2LzZ3SEVnOGtjTW1BcgppcHNZcHNZY1VXbktoMWtQYzA2MnAvaW9udGFobWxOTGJnbU8wOGpQQVFLQmdRRHVGUEdLM2FKeWN3K3d5UU95CnBaMjYyT0xTdmUydS9uaTBrWWNGaEdTWjh2N0w5OEdCdG9ZN0E2ZjhiZVZaSW9BT3hBYzhqeXFEUDFtTDFlUisKT2x1clk1ekxVWU9VTjcxcGU1K3Y0UXpXb3hRdFN2eE43azN6Y1Rnd1Qva0FnZHQ5c2Yxd0ZOZkVWcG5QR0x6eQpEdEY2eWpLbWxNUDc4bUV1ZWRQaGRMbHdjUUtCZ1FETTFKandSTks4Qjd0Sjk4U0dKdTRzTUNEcGZJaTNITXVwCi9aVmtRcmkzZDRUbjhGMUZ0UGxCSkxWbi94YlA2ZWxXa2N1MGtzTm45eXFLSEZmbXg5ZGJzbEovQUZNMDVic3gKRXB2aE9PTkNsWlhtUi9qOWJ2cUMvK0g0OStCTm9EbXdwdUxReUNZcU9YZWYxOC9FbGpKSjZlMmgwOG9wUExHRQpjL2Jia08rVXVRS0JnUUNJMFdSVnB6U1pqT0h4ZURNMTBOTXA4MFcyVWd0clN6WFdudUwzR3JRdGZHVk1sZDZRClNuSXRLOWEzeS9mSEYzcDhBYzhlMEM5Z0tXR3VhSWJjdTNDK3Q0bjlsYVNGNHRwbzZmQkV4Skg2THRHRGpkb2MKZHR3NUVGRjRBaDFZVzBmbG5nbkZCVlZSc1dyR2hyTGVjQUdXRE9pNnJqZEtiR25JcVo1SHJDcDlJUUtCZ0NEWQpVbkhzeDFJSmQxbmtGaXBnNGI1S05XemJZRnprakRBRkRzaVYvbUxGRXBYU3NGSTJNK1hqU2dlVUd3ZFovZVc0CjJVNXFYbFUwaUNpL1pNVUg5SnVxbTVucjVtdk1EdGxPbjVwYzhleENhbGdUNEhSYk1HYURPNndkcTJVbk5Ua0QKZWNsNjNzdlVqVDhmYnh5WjdSUjNJM2pZcWtrMGgwNnkvYm0xb3dWWkFvR0FQRXg5QWxpOTl1RXAybnFzT2dBSQpHT1F5SXdiT21LQzVYY0lrbFpNdWF1WENCU2hLZVNaZHZXZ3ExRlRvQXU2Y2JYRUpNRXlqK3l1QlIxMnFIa25pCm1vcm9xUTJ4ZnJRVUNOVnBKSjMrdzZEVzNqV2NPTVJGaFlvZDVDQmlvOXBWZWcxOTYwMVcwR2hhR1c3Qm11cDYKK1J4M3lPbXZNZ2pSRXc0OVVhRk9GNDQ9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    static final String TLS_CERT = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURPVENDQWlFQ0ZIU200RG11cWp2VjdaYUNsT1NXcVE5QWZiRjBNQTBHQ1NxR1NJYjNEUUVCQ3dVQU1Ga3gKQ3pBSkJnTlZCQVlUQWxWVE1STXdFUVlEVlFRSURBcFRiMjFsTFZOMFlYUmxNU0V3SHdZRFZRUUtEQmhKYm5SbApjbTVsZENCWGFXUm5hWFJ6SUZCMGVTQk1kR1F4RWpBUUJnTlZCQU1NQ1d4dlkyRnNhRzl6ZERBZUZ3MHlOVEExCk1EWXdOalUwTkRWYUZ3MHlOakExTURZd05qVTBORFZhTUZreEN6QUpCZ05WQkFZVEFsVlRNUk13RVFZRFZRUUkKREFwVGIyMWxMVk4wWVhSbE1TRXdId1lEVlFRS0RCaEpiblJsY201bGRDQlhhV1JuYVhSeklGQjBlU0JNZEdReApFakFRQmdOVkJBTU1DV3h2WTJGc2FHOXpkRENDQVNJd0RRWUpLb1pJaHZjTkFRRUJCUUFEZ2dFUEFEQ0NBUW9DCmdnRUJBTDUrYUE2U3EyKzRUUFVkMHk3bDk1ZXVDVUU4ZERndW1KQTVsMWp4U1RnL21NYXY3NzlheHB6M0lmVXQKcWduVWRvZTh0K042Y096WWRoZ0V6bzNCK3Vxd2NXQ2hsVWZSdUkxNWcyVEtFUjIxb3U5L2dRVVY2STVub1d3YgpUVFZ3VlpOdFNnUXUwQmxxamRyVkNOYkdEVlk2Zm1sRjZHMktjeUFZU1dWaUk1UURTV2lFS3V5c3hWY0N6VytPCmQ5N2JmcElUVjNuWEtBc0FaL2Nwanh2WDExNEVxQ3pQdmZxdkVHMWs3VGRqNTRrMGQwTmVNTVErdkt1L0NkMEIKd2czSHMrVXdrWDBMZ2M2ckdrVTIrRmJGaDNRYks3UU1xZjVDK0dadm9lbEZ3YStleFJLNHRZbU9RNURYOVVDeApqV1JmbjZaZm1lL1ZCWEYyZVIrRWN4aUNsYWtDQXdFQUFUQU5CZ2txaGtpRzl3MEJBUXNGQUFPQ0FRRUFpdWhsCkxISXlGNDE0S2RBR1Q4OW5ESk5sS0V3bFAvKzF2V3NSUkU5TmJ2Y2wrNWJJaHRqTkpTNnA3UWoreDJXMG9PeEUKWU9jS0J1Q0djc3plWTQ1MjRCa2hpQmlLdXJSSUpUK0pVSHo3aHJrQ3hkK2Y1RTRRMUJlZVJwR2V0dW1qT290Ygo3cmlwblhoNUtYcDFYVGl4ZXBnWEZPRkZ6RDVDR2lTUFBFeTdpS2hTY1FiNlNjQ3BvTEh2anR1VFhVYnUyNmlvCnEvbE5seVJpNGZjaWIxTWpSZlZ3WkN0V25zMUtEei9vVVlwZWNtYnVFWFF1UmluRllDR3lpSlcvdmRlUjhkWXAKaHZTaHpFQWlxL2N3NXJiWHQvamNrV2JJRHZMWWhIdjF4NUdsNStJenozRkN1L1JjeW5qUmhjWWpFZGZyVGtDYwpGajJ6T056cHYzKzA5WGxiVFE9PQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==";
    static final String WILDCARD_TLS_KEY_EXPIRED = "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRQ3YvNXRDZkRFTkE3S08KRVdDajl6WStBbnY4a0g0cFl4V295Ri9XUTFaUFllQkkwRDZPOUxQYWdmYXNpUFA1UXRvR1h3WUh5cldoS1NIZgpMaUpUQmxSQ0ZUdUdOVU9ZbnFyczNxYkNFS1VEV3cweXQ3SlJHVGprVWpUV1hjS1dWaFJNOWFCazZGNURqdEVaCjlvUUZTQk5TaW5aR3FBcDd4RWZaUmdScEFPZ2JkaTQrYXUrRng5M2FXUi9vcXlOb0xvdlV5bU5uM0JUZmRuTnkKdnBncSt2YWpZM05pQ0dKQmRyRmxnUzl5SzhtNWVNUm0yUHBpN1VVWk1jcnpUd3pzblZLSTZ0bGNCb1JyTWxvOQpyczM0VW4zWktwNDhOZzhYM1MrY1kwdWpodUZocGVjOHBTQXk0cmExVzBEekJWVGo2SXhlT3dKL2ZDMVRKVmtQCnJxSUhRVlpsQWdNQkFBRUNnZ0VBQmV2bXNHVDNUWVhMVXlaWWh3dkJRbkxZOHhWMWVUVUJQSTJKVSs4dUR6dXUKelNSY2huakxPRlhaRTlNRHltWXFhYjUydUhwa2hBaWJRQ1FRT1pUajd0bENDSER3VmtrNFhRSGN3SGpJNUlVQwp1NnNjajhmMmR4QWhBUGxrbTZSSHlKTVhFMjRTQmRuZlJTMWJJY1pjQ0JIcWl0UUtqa0Y3WFY4U3RPbTM1WHlWCkFPWkNGZWhJNzFmbkVWeDhRMXN2V3J6NE85WEhQQk5Fa0lDdHhUVlNEb2U4RU1yZ2JUanZVaE81VE5aR3RpVmcKRzhUN2dzWnVsM2ZJTG1hOGlFdGgxOTlqblNDZkFxK0NkQUd4V21GM1lEejUxVjdsY3JRUDlhekJHaDRwYUFZSApLR0IycHBzT0xSeFJsWENudGo2M3VuL051RlFGNlVqY2oxdldZdHdESFFLQmdRQzNzZ1BPN01YbnJXTzNvSDJoCmpaQytJNWJCNG1ER1FxdW5zNm5razUxR2lXMG8yVWRaVDM3VW83VEQ1WUtNYzI5cVBtZUQrRjNHUlFIOW9mczIKa3NOczJ2MEpoL3RiSFVWYmhmSHlhUGUzVHl0SmpNTkRjbkdSR1JsWklYUjZ3M3cwbEJ6UzdDQmhpd3hhaXI2dwo2V2NQenpPTjEzVVJzUm5kQmhyUEo4WUhHd0tCZ1FEMVJnUVlrVTFVSkx3Qm5uM2tVekVxN2VKZFJ3VEMyR2k3CndGUm9tcHN3cENVZ1ZDcmtlbldteE8yVXN5T3BNb1dNcFFINVo2b240Ym9lR2hMVVFmSE1PVlo2TStvc1NaSFgKTHhKRDdOWDhVOXFpakUwelFPOVF3YXJxb003RkdmMnN6bXpJYzBKcWdpTmw2Yk1QWWVhY3p5dW5RclBXSzJyUwpDckU2QVRod2Z3S0JnQkJjVllSL2lCemJUNDlTUnY0MWlwZTB1YitvOXUrUmpwSlFLU2lIa1RGd0dmM1NaRTFyCkRDUEtOTlpod1ovYXhDaVZTRWp6dlA1a3RRbXUvSjNlc25NbWlmVG9YSlcvNUthREpvcHRtT3FGclpoT1pqSEMKcHFUQ3RJUkF1NHdYang5cnRhbC9pRjdIc0tEN3pJSWJONmVyejY2cGF1N1pkREZBVXZEeVhPdlRBb0dCQUs1WApibUhvSm9kQTlVSlJYanNGeDFVMGNrckxTRjhYWkpyUVF5OTNkZ2hGSlA2dDFTOUN2STRtUEpvT0c3TVE3a3ppCm5WeWlpeGgrSXNWeTB4OTlJRnFDQnk0bDZMWkNOU0EzblV3YTlKKy9HeEdmbU9CdHI2NC9lelg3eC8zaVU2YVoKdEVsblpEWkErdGhlSGFFNWZhckl0OGJVYXZBNVBGekFyT2tHSlJBUkFvR0JBSktCazdpWHpZZzFzUTRQWDU0cwoxMWZkUlFmQ014K2RzdDdpK3BCay9wa2R2OEZzb1QvUzBKM3NGWTBSNEI4TVlHS1ovUHY3L1JVeS85S0hDbEE0CnZ3MHVXMjhUVTJhcml2SitLYzY1dGVSSVpLUTRaQmdUbUdLUENxWEphRmh5UUFaN1RqYTZVLzBXTEhlQnUyTk0KQ0liSVhicHhzY0NreHVRelM5UHhlMys5Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    static final String WILDCARD_TLS_CERT_EXPIRED = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURQVENDQWlVQ0ZFeGpXQ2ZMRkNObkc5dk5rZ29DSTBMSUErQmtNQTBHQ1NxR1NJYjNEUUVCQ3dVQU1Gc3gKQ3pBSkJnTlZCQVlUQWtGVk1STXdFUVlEVlFRSURBcFRiMjFsTFZOMFlYUmxNU0V3SHdZRFZRUUtEQmhKYm5SbApjbTVsZENCWGFXUm5hWFJ6SUZCMGVTQk1kR1F4RkRBU0JnTlZCQU1NQ3lvdWJHOWpZV3hvYjNOME1CNFhEVEkxCk1EWXdNakU1TVRRMU5Wb1hEVEkxTURZd016RTVNVFExTlZvd1d6RUxNQWtHQTFVRUJoTUNRVlV4RXpBUkJnTlYKQkFnTUNsTnZiV1V0VTNSaGRHVXhJVEFmQmdOVkJBb01HRWx1ZEdWeWJtVjBJRmRwWkdkcGRITWdVSFI1SUV4MApaREVVTUJJR0ExVUVBd3dMS2k1c2IyTmhiR2h2YzNRd2dnRWlNQTBHQ1NxR1NJYjNEUUVCQVFVQUE0SUJEd0F3CmdnRUtBb0lCQVFDdi81dENmREVOQTdLT0VXQ2o5elkrQW52OGtINHBZeFdveUYvV1ExWlBZZUJJMEQ2TzlMUGEKZ2Zhc2lQUDVRdG9HWHdZSHlyV2hLU0hmTGlKVEJsUkNGVHVHTlVPWW5xcnMzcWJDRUtVRFd3MHl0N0pSR1RqawpValRXWGNLV1ZoUk05YUJrNkY1RGp0RVo5b1FGU0JOU2luWkdxQXA3eEVmWlJnUnBBT2diZGk0K2F1K0Z4OTNhCldSL29xeU5vTG92VXltTm4zQlRmZG5OeXZwZ3ErdmFqWTNOaUNHSkJkckZsZ1M5eUs4bTVlTVJtMlBwaTdVVVoKTWNyelR3enNuVktJNnRsY0JvUnJNbG85cnMzNFVuM1pLcDQ4Tmc4WDNTK2NZMHVqaHVGaHBlYzhwU0F5NHJhMQpXMER6QlZUajZJeGVPd0ovZkMxVEpWa1BycUlIUVZabEFnTUJBQUV3RFFZSktvWklodmNOQVFFTEJRQURnZ0VCCkFETjhEMWkza2E1OVA0MjlJM3hqTzArcWhsTjdKd0RVd0t3S3JQaDgvSldMZSs3RUM3NmNlKzluQUdFdm4xY1UKcVoxeVd6QkxSSDQ1N2NVNE96Z1prYVFvMW5TdGtaamZsRG5WOWpDUlNGK1RvbmRMT2U5emNRaXNLSlhjTklIZwozMmN5V3dZQ1Rqa2Zzb1J3R1QvNWFPOEY3UG5wb0lQQlMzK2E5WERmeFRKQUZRaWwzVVNnckdkRWc4K0NSN3UwCjlxcEpYQzQ1WTM1ZHFJV1Z0QTNEU3J5N21tSEM1S2ZZS1FPMWVUS2N5cFNkL3grQ3ZLRnJ0c080RnZhaTZUaGoKdTFtVmN0K2dyMDZqMEd3Q0lWVzVHdVJLSmdlK0pGUDV3aWlJK3BnSURKenRybm5RbVRkN2puKzhDYnpkU2tlMwo1U2o3UXBMdUtuakZRRFd0UU9Gb0lKVT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=";
    
    // proxy
    static final int PROXY_PORT = 8443;

    // Docker
    GenericContainer<?> appAContainer;
    GenericContainer<?> appBContainer;
    GenericContainer<?> appCContainer;
    private GenericContainer<?> proxyContainer;
    private static final Network NETWORK = Network.newNetwork();

    String url() {
        return String.format("https://localhost:%d%s", proxyContainer.getMappedPort(PROXY_PORT), APP_ENDPOINT);
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
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/"
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appAContainer.start();

        appBContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_A_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_B_NAME,
                        "millau.enabled", "false",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/",
                        "millau.cert", TLS_CERT,
                        "millau.key", TLS_KEY
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appBContainer.start();

        appCContainer = new GenericContainer<>(DockerImageName.parse(appImage()))
                .withExposedPorts(APP_PORT)
                .withNetwork(NETWORK)
                .withNetworkAliases(APP_A_NAME)
                .withLabels(Map.of(
                        "com.docker.compose.service", APP_C_NAME,
                        "millau.enabled", "true",
                        "millau.port", "" + APP_PORT,
                        "millau.path", "/rest/",
                        "millau.cert", WILDCARD_TLS_CERT_EXPIRED,
                        "millau.key", WILDCARD_TLS_KEY_EXPIRED
                ))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(5)));
        appCContainer.start();

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
        if (appBContainer != null) {
            appBContainer.stop();
        }
        if (appCContainer != null) {
            appCContainer.stop();
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
        int requests = 30;
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
        assertEquals(3, stats.size()); // 3 responding upstreams
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
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(TLS_CERT)));
        String pemKey = new String(Base64.getDecoder().decode(TLS_KEY));
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
