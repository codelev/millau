# Millau
Millau (pronounced _mijo_) is a free ingress proxy and load balancer designed for microservice architectures built on Docker Swarm.

![Millau](setup.svg)

Traditional proxies require configuration for each path and host, and must be restarted to apply changes. Modern proxies listen to Docker events and configure routes automatically. While traditional proxies store configuration files on mounted volumes, modern proxies store routes in memory. However, modern proxies do not support load balancing across services - Millau does.

Millau
- Listens to Docker events for zero restarts
- Failover retries for guaranteed delivery
- Written in Golang and available as a Docker image

## Comparison

| Product    | Configuration | Multi-service LB | Service discovery | Prometheus metrics | mTLS    |
|------------|---------------|------------------|-------------------|--------------------|---------|
| NGINX      | File          | Yes              | No                | No                 | Yes     |
| HAProxy    | File          | Yes              | No                | Yes                | Yes     |
| Envoy      | File          | Yes              | No                | Yes                | Yes     |
| Caddy      | File          | Yes              | No                | Yes                | Yes     |
| Traefik    | Labels        | No               | Yes               | Yes                | Yes     |
| **Millau** | **Labels**    | **Yes**          | **Yes**           | **No**             | **Yes** |

## Performance

| Product    | Test type                         | Avg response (ms) | Shortest response (ms) |
|------------|-----------------------------------|-------------------|------------------------|
| NGINX      | GET application/json              | 2.45              | 1.73                   |
| HAProxy    | GET application/json              | 2.99              | 1.93                   |
| Caddy      | GET application/json              | 2.80              | 1.66                   |
| Traefik    | GET application/json              | 2.66              | 1.53                   |
| **Millau** | GET application/json              | **2.76**          | **1.74**               |
| NGINX      | POST application/octet-stream 5Mb | 32.24             | 29.27                  |
| HAProxy    | POST application/octet-stream 5Mb | 23.12             | 20.69                  |
| Caddy      | POST application/octet-stream 5Mb | 23.81             | 20.98                  |
| Traefik    | POST application/octet-stream 5Mb | 23.35             | 21.04                  |
| **Millau** | POST application/octet-stream 5Mb | **22.86**         | **20.53**              |

## Quickstart

1. Create `docker-compose.proxy.yml`:
    ```
    services:
      proxy:
        image: codelev/millau:latest
        volumes:
          - /var/run/docker.sock:/var/run/docker.sock:ro
        ports:
          - "8080:80"
        networks:
          - millau
    networks:
      millau:
        external: true
    ```
2. Create `docker-compose.service.yml`:
    ```
    services:
      whoami:
        image: traefik/whoami
        deploy:
          mode: replicated
          replicas: 3
          labels:
            - "millau.enabled=true"
            - "millau.port=80"
        ports:
          - "80"
        networks:
          - millau
    networks:
      millau:
        external: true
    ```
3. Create network: `docker network create --driver=overlay millau`
4. Deploy proxy: `docker stack deploy -c docker-compose.proxy.yml proxy`
5. Deploy service:  `docker stack deploy -c docker-compose.service.yml service`

## Use Cases

### Blue-Green

```shell
docker stack deploy -c docker-compose.bluegreen.yml bluegreen
curl -i -H 'Host: company.com' localhost:8080/api/
# HTTP 200 blue or green
curl -i -H 'Host: green.local' localhost:8080/api/
# HTTP 200 green
curl -i -H 'Host: blue.local' localhost:8080/api/
# HTTP 200 blue
curl -i -H 'Host: company.locall' localhost:8080
# HTTP 502 No matching services found
curl -i -H 'Host: company.local' localhost:8080/apii/
# HTTP 502 No matching services found
curl -k --resolve company.local:8443:127.0.0.1 --connect-to company.local:8443 -H 'Host: company.local' https://company.local:8443/api/
# HTTP 200 blue or green
docker stack rm bluegreen
```

### Ingress

```shell
docker stack deploy -c docker-compose.ingress.yml ingress
curl -i localhost:8080
# HTTP 200 backend
docker stack rm ingress
```

### Failover

```shell
docker stack deploy -c docker-compose.failover.yml failover
curl localhost:8080/rest/echo
# HTTP 200
docker stack rm failover
```

### Docker Compose

```shell
docker compose up -d
curl -i localhost:8080/rest/echo
# HTTP 200
curl -k --resolve company.local:8443:127.0.0.1 --connect-to company.local:8443 https://company.local:8443/rest/echo
# HTTP 200
docker compose down
```

### Self-Signed TLS Certificate

```shell
openssl genrsa -out company.local.key 2048
openssl req -new -key company.local.key -out company.local.csr
openssl x509 -req -days 365 -in company.local.csr -signkey company.local.key -out company.local.cert
base64 -w 0 company.local.key > company.local.key.b64
base64 -w 0 company.local.cert > company.local.cert.b64
```
