package perf;

class PerfCaddyTest extends PerfTest {

    @Override
    String proxyImage() {
        return "caddy:latest";
    }

    @Override
    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Override
    String proxyConfigFile() {
        return "/etc/caddy/Caddyfile";
    }

    @Override
    String proxyConfig() {
        return """
                http://*:%d
                reverse_proxy http://%s:%d
                """.formatted(PROXY_PORT, APP_NAME, APP_PORT);
    }
}
