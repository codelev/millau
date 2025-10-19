package perf;

import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PerfEchoNestTest extends PerfTest {

    @Override
    String proxyImage() {
        return appImage();
    }

    @Override
    String appImage() {
        return "codelev/echo-nest:latest";
    }

    @Override
    String proxyConfigFile() {
        return null;
    }

    @Override
    String proxyConfig() {
        return null;
    }

    @Override
    String url() {
        return String.format("http://%s:%d%s", appContainer.getHost(), appContainer.getMappedPort(APP_PORT), APP_ENDPOINT);
    }
}
