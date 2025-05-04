package perf;

class PerfHaproxyTest extends PerfTest {

    @Override
    String proxyImage() {
        return "haproxy:latest";
    }

    @Override
    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Override
    String proxyConfigFile() {
        return "/usr/local/etc/haproxy/haproxy.cfg";
    }

    @Override
    String proxyConfig() {
        return """
                frontend app_proxy
                    bind *:%d
                    mode http
                    option forwardfor
                    option http-server-close
                    default_backend app_backend
                backend app_backend
                    mode http
                    server app_server %s:%d maxconn 100
                    http-request set-header X-Real-IP %%[src]
                    http-request set-header X-Forwarded-For %%[src]
                    http-request set-header X-Forwarded-Proto http
                """.formatted(PROXY_PORT, APP_NAME, APP_PORT);
    }
}
