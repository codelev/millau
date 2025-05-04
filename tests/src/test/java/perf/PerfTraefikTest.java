package perf;

class PerfTraefikTest extends PerfTest {

    @Override
    String proxyImage() {
        return "traefik:latest";
    }

    @Override
    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Override
    String proxyConfigFile() {
        return "/etc/traefik/traefik.yaml";
    }

    @Override
    String proxyConfig() {
        return """
                entryPoints:
                  web:
                    address: ":%d"
                providers:
                  file:
                    filename: /etc/traefik/traefik.yaml
                http:
                  routers:
                    app-router:
                      rule: "PathPrefix(`/`)"
                      entryPoints:
                        - web
                      service: app
                  services:
                    app:
                      loadBalancer:
                        servers:
                          - url: "http://%s:%d"
                """.formatted(PROXY_PORT, APP_NAME, APP_PORT);
    }
}
