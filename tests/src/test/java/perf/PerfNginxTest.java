package perf;

class PerfNginxTest extends PerfTest {

    @Override
    String proxyImage() {
        return "nginx:latest";
    }

    @Override
    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Override
    String proxyConfigFile() {
        return "/etc/nginx/conf.d/default.conf";
    }

    @Override
    String proxyConfig() {
        return """
                server {
                    listen %d;
                    client_max_body_size 10M;
                    location / {
                        proxy_pass http://%s:%d;
                        proxy_set_header Host $host;
                        proxy_set_header X-Real-IP $remote_addr;
                        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
                        proxy_set_header X-Forwarded-Proto $scheme;
                        proxy_set_header TE trailers;
                    }
                }
                """.formatted(PROXY_PORT, APP_NAME, APP_PORT);
    }
}
