# Millau Tests

Requirements:
- Java 21+
- Docker

## Build

1. Copy `application.properties.dist` to `application.properties` and adapt test settings.
2. Execute test:
    ```shell
    docker image rm codelev/millau:latest
    mvn clean test
    ```

## Performance

### Sequential GET requests: caddy:latest

`GET http://localhost:33019/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 138.05 ms       |
| Shortest response    | 1.66 ms       |
| Average response     | 2.80 ms       |

### Parallel GET requests: caddy:latest

`GET http://localhost:33019/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 18.87 ms       |
| Shortest response    | 2.40 ms       |
| Average response     | 6.07 ms       |

### Sequential GET requests: echo-nest

`GET http://localhost:33020/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 21.59 ms       |
| Shortest response    | 0.77 ms       |
| Average response     | 1.20 ms       |

### Parallel GET requests: echo-nest

`GET http://localhost:33020/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 84.00 ms       |
| Shortest response    | 1.56 ms       |
| Average response     | 32.91 ms       |

### Sequential GET requests: echo-spring

`GET http://localhost:33021/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 128.41 ms       |
| Shortest response    | 1.21 ms       |
| Average response     | 2.06 ms       |

### Parallel GET requests: echo-spring

`GET http://localhost:33021/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 16.26 ms       |
| Shortest response    | 1.17 ms       |
| Average response     | 4.64 ms       |

### Sequential GET requests: nginx:latest

`GET http://localhost:33023/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 105.85 ms       |
| Shortest response    | 1.73 ms       |
| Average response     | 2.45 ms       |

### Parallel GET requests: nginx:latest

`GET http://localhost:33023/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 17.21 ms       |
| Shortest response    | 2.23 ms       |
| Average response     | 5.09 ms       |

### Sequential GET requests: codelev/millau:latest

`GET http://localhost:33025/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 131.34 ms       |
| Shortest response    | 1.74 ms       |
| Average response     | 2.76 ms       |

### Parallel GET requests: codelev/millau:latest

`GET http://localhost:33025/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 23.08 ms       |
| Shortest response    | 2.14 ms       |
| Average response     | 9.57 ms       |

### Sequential POST requests: caddy:latest

`POST http://localhost:33028/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 176.56 ms       |
| Shortest response    | 20.98 ms       |
| Average response     | 23.81 ms       |

### Parallel POST requests: caddy:latest

`POST http://localhost:33028/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 386.58 ms       |
| Shortest response    | 58.74 ms       |
| Average response     | 207.89 ms       |

### Sequential POST requests: echo-nest

`POST http://localhost:33029/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 40.91 ms       |
| Shortest response    | 9.33 ms       |
| Average response     | 10.65 ms       |

### Parallel POST requests: echo-nest

`POST http://localhost:33029/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 925.71 ms       |
| Shortest response    | 141.24 ms       |
| Average response     | 798.24 ms       |

### Sequential POST requests: echo-spring

`POST http://localhost:33030/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 158.69 ms       |
| Shortest response    | 20.54 ms       |
| Average response     | 22.35 ms       |

### Parallel POST requests: echo-spring

`POST http://localhost:33030/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 158.28 ms       |
| Shortest response    | 33.95 ms       |
| Average response     | 73.37 ms       |

### Sequential POST requests: nginx:latest

`POST http://localhost:33032/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 157.96 ms       |
| Shortest response    | 29.27 ms       |
| Average response     | 32.24 ms       |

### Parallel POST requests: nginx:latest

`POST http://localhost:33032/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 650.27 ms       |
| Shortest response    | 76.43 ms       |
| Average response     | 367.62 ms       |

### Sequential POST requests: codelev/millau:latest

`POST http://localhost:33034/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 75.24 ms       |
| Shortest response    | 20.53 ms       |
| Average response     | 22.86 ms       |

### Parallel POST requests: codelev/millau:latest

`POST http://localhost:33034/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 394.26 ms       |
| Shortest response    | 47.24 ms       |
| Average response     | 162.66 ms       |

### Sequential GET requests: haproxy:latest

`GET http://localhost:32806/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 125.03 ms       |
| Shortest response    | 1.93 ms       |
| Average response     | 2.99 ms       |

### Parallel GET requests: haproxy:latest

`GET http://localhost:32806/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 13.82 ms       |
| Shortest response    | 2.81 ms       |
| Average response     | 5.63 ms       |

### Sequential POST requests: haproxy:latest

`POST http://localhost:32806/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 64.61 ms       |
| Shortest response    | 20.69 ms       |
| Average response     | 23.12 ms       |

### Parallel POST requests: haproxy:latest

`POST http://localhost:32806/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 371.82 ms       |
| Shortest response    | 46.05 ms       |
| Average response     | 215.60 ms       |

### Sequential GET requests: traefik:latest

`GET http://localhost:32809/rest/echo application/json 0Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 118.78 ms       |
| Shortest response    | 1.53 ms       |
| Average response     | 2.66 ms       |

### Parallel GET requests: traefik:latest

`GET http://localhost:32809/rest/echo application/json 0Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 18.57 ms       |
| Shortest response    | 2.24 ms       |
| Average response     | 6.57 ms       |

### Sequential POST requests: traefik:latest

`POST http://localhost:32809/rest/echo application/octet-stream 5Mb` x 500

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 60.94 ms       |
| Shortest response    | 21.04 ms       |
| Average response     | 23.35 ms       |

### Parallel POST requests: traefik:latest

`POST http://localhost:32809/rest/echo application/octet-stream 5Mb` x 100

| Metric               | Value         |
|----------------------|---------------|
| Longest response     | 271.10 ms       |
| Shortest response    | 34.66 ms       |
| Average response     | 122.43 ms       |