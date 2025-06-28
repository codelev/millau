# Millau
Millau (pronounced _mijo_) is a free ingress proxy and load balancer designed for microservice architectures built on Docker Swarm.

![Millau](setup.svg)

Traditional proxies require configuration for each path and host, and must be restarted to apply changes. Modern proxies listen to Docker events and configure routes automatically. While traditional proxies store configuration files on mounted volumes, modern proxies store routes in memory. However, modern proxies do not support load balancing across services - Millau does.

Millau
- Listens to Docker events for zero restarts
- Failover retries for guaranteed delivery
- Written in Golang and available as a slim Docker image

## Comparison

| Product    | Configuration | Multi-service LB | Service discovery | Prometheus metrics | Automatic HTTPS | mTLS    | Image size, MB |
|------------|---------------|------------------|-------------------|--------------------|-----------------|---------|----------------|
| NGINX      | File          | Yes              | No                | No                 | No              | Yes     | 192            |
| HAProxy    | File          | Yes              | No                | Yes                | No              | Yes     | 105            |
| Envoy      | File          | Yes              | No                | Yes                | No              | Yes     | 191            |
| Caddy      | File          | Yes              | No                | Yes                | Yes             | Yes     | 49             |
| Traefik    | Labels        | No               | Yes               | Yes                | Yes             | Yes     | 224            |
| **Millau** | **Labels**    | **Yes**          | **Yes**           | **Yes**            | **Yes**         | **Yes** | **33**         |

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
curl -i -H 'Host: www.company.com' localhost:8080/api/
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
curl -k --http2 --resolve company.local:8443:127.0.0.1 --connect-to company.local:8443 -H 'Host: company.local' https://company.local:8443/api/
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

### Maintenance Mode

```shell
docker stack deploy -c docker-compose.spa.yml spa
curl -H 'Host: company.com' localhost:8080
# homepage
docker service scale spa_homepage=0
curl -H 'Host: company.com' localhost:8080
# maintenance page
docker service scale spa_homepage=1
curl -H 'Host: company.com' localhost:8080
# homepage
docker stack rm spa
```

### Docker Compose

```shell
docker compose up -d
curl -i localhost:8080/rest/echo
# HTTP 200
curl -k --resolve company.local:8443:127.0.0.1 --connect-to company.local:8443 https://company.local:8443/rest/echo
curl -k --http2 --resolve company.local:8443:127.0.0.1 --connect-to company.local:8443 https://company.local:8443/rest/echo
# HTTP 200
docker compose down
```

### Telemetry
Millau exposes the following endpoints on port `9100`:
- Healthcheck `/`:
    - responds `up` and `200 OK` when healthy,
    - responds `down` and `503 Service Unavailable` when unhealthy.
- Prometheus metrics `/metrics`:

| Metric                                 | Description                                              |
|----------------------------------------|----------------------------------------------------------|
| **Ingress**                            |                                                          |
| `millau_ingress_open_connections`      | The current count of open connections by port.           |
| `millau_ingress_requests_total`        | The total count of requests received by port.            |
| `millau_ingress_requests_bytes_total`  | The total size of requests in bytes handled by port.     |
| `millau_ingress_responses_bytes_total` | The total size of responses in bytes handled by port.    |
| **Load Balancer**                      |                                                          |
| `millau_lb_successful_requests_total`  | The total count of requests handled by service.          |
| `millau_lb_failed_requests_total`      | The total count of requests not handled by service.      |
| `millau_lb_requests_bytes_total`       | The total size of requests in bytes handled by service.  |
| `millau_lb_responses_bytes_total`      | The total size of responses in bytes handled by service. |
| `millau_lb_retries_total`              | The count of retries made for service.                   |
| `millau_lb_status`                     | Current service status, `0` for `down` or `1` for `up`.  |
| `millau_lb_request_duration_seconds`   | Request handling histogram by service.                   |
| `millau_topology`                      | Requests routed to services.                             |

Millau has an official [Grafana dashboard](https://grafana.com/grafana/dashboards/23474-millau-ingress-proxy-and-load-balancer/).
You can try it out locally using the `docker-compose.yml`, which sets up Millau, Echo service, Prometheus and Grafana together.

Once the stack is running, log in Grafana at http://localhost:3000 with username `admin` and password `admin`.
The [millau.json](https://github.com/codelev/millau/tree/main/pg/millau.json) is already installed and ready to use.


### Self-Signed TLS Certificate

```shell
openssl genrsa -out company.local.key 2048
openssl req -new -key company.local.key -out company.local.csr
openssl x509 -req -days 365 -in company.local.csr -signkey company.local.key -out company.local.cert
base64 -w 0 company.local.key > company.local.key.b64
base64 -w 0 company.local.cert > company.local.cert.b64
```

## Configuration

### Logging Levels

- **FATAL**: Indicates an unrecoverable error; the process will exit.
- **ERROR**: Indicates a proxy failure; the process continues running.
- **WARN**: Indicates client or microservice misbehavior; the process continues running.
- **INFO**: Indicates normal functional behavior. Default level.
- **DEBUG**: Indicates step‑by‑step functional behavior.

 ```
 services:
   proxy:
     image: codelev/millau:latest
      environment:
        - LOGGING=DEBUG
      ...
 ```

### HTTP and HTTPS Ports
By default, the HTTP and HTTPS ports are `80` and `443`. You can change them as follows:

 ```
 services:
   proxy:
     image: codelev/millau:latest
      environment:
        - HTTP=8080
        - HTTPS=8443
      ...
 ```

## Automatic HTTPS
By default, ACME API is `https://api.buypass.com/acme/directory` (Buypass AS Certificate Authority, Norway). 
You can change is as follows:

 ```
 services:
   proxy:
     image: codelev/millau:latest
      environment:
        - ACME=https://acme-v02.api.letsencrypt.org/directory
      ...
 ```

### HTTP Host Matching
The matching logic selects the services whose `millau.hosts` label matches the domain hierarchy.
It prioritizes:
- exact matches,
- longest suffix.

| Configured          | Requested       | Selected  |
|---------------------|-----------------|-----------|
| `one.com` `two.com` | `one.com`       | `one.com` |
| `one.com` `two.com` | `www.two.com`   | `two.com` |
| `one.com` `two.com` | `three.two.com` | `two.com` |
| `one.com` `local`   | `blue.local`    | `local`   |
| `*` `two.com`       | `two.com`       | `two.com` |
| `*` `two.com`       | `one.com`       | `*`       |
| `*` `*`             | `one.com`       | `*` `*`   |


### HTTP Path Matching
The matching logic selects the services whose `millau.path` label matches the beginning of the request path. 
It prioritizes:
- exact matches,
- longest prefix.

| Configured                       | Requested          | Selected        |
|----------------------------------|--------------------|-----------------|
| `/api/` `/`                      | `/api/`            | `/api/`         |
| `/api/` `/`                      | `/api`             | `/`             |
| `/api/` `/`                      | `/file.html`       | `/`             |
| `/api/` `/`                      | `/api/x`           | `/api/`         |
| `/api/` `/`                      | `/api/x/`          | `/api/`         |
| `/api/` `/`                      | `/api/x/file.html` | `/api/`         |
| `/api/` `/` `/file.html`         | `/file.html`       | `/file.html`    |
| `/file` `/api/` `/` `/file`      | `/file.html`       | `/file` `/file` |
| `/api/` `/` `/file` `/file.html` | `/file.html`       | `/file.html`    |
| `/api/` `/` `/api/`              | `/api/`            | `/api/` `/api/` |


### Free Commercial License
Adding a license key to your Millau instance removes debugging information from HTTP traffic. You can get the license free of charge in a minute at https://millau.net/license and add it as follows:

 ```
 services:
   proxy:
     image: codelev/millau:latest
      environment:
        - LICENSE=my-license-key
      ...
 ```

## Support
The license key also gives you access to support. Feel free to send a message or schedule a call at https://millau.net/support.

Please note that Millau is a non-commercial project, so constant availability isn't guaranteed. However, you're always welcome to report issues on GitHub https://github.com/codelev/millau/issues.

## Contributing
There are three ways you can support Millau:
1. Use Millau in your stack.
2. Sponsor at https://www.patreon.com/c/millau
3. Star this GitHub repository.

Every user, issued license, and star helps attract the attention of potential investors. The final goal is to raise €20,000 to establish Millau as the standard ingress proxy for Docker Swarm and release it under an open-source license.
