---
title: Changelog
description: Notable changes to the project.
---

## 0.0.7
2025-06-28

**Added**
- Automatic HTTPS.

## 0.0.6
2025-06-15

**Fixed**
- TLS certificate sharing among services.

## 0.0.5
2025-06-07

**Fixed**
- Incorrect behavior when scaling to 0 replicas.

**Changed**
- Deduplicated log messages during redeployments.

## 0.0.4
2025-05-31

**Added**
- Topology metrics in Prometheus.
- Topology metrics in Grafana dashboard.

## 0.0.3
2025-05-24

**Added**
- Docker healthcheck.
- Prometheus metrics.
- Grafana dashboard.
- Graceful shutdown.

**Changed**
- Host matching prioritization:
  - exact match,
  - longest suffix match.

## 0.0.2
2025-05-18

**Added**
- Display all known services after initialization.
- Support for commercial licenses.
- Display logo, version, and license information on startup.

**Changed**
- Path matching prioritization:
  - exact match,
  - longest prefix match.

**Fixed**
- Incorrect logging of re-enabled services.

## 0.0.1
2025-05-10

**Added**
- Logging levels.
- HTTP and HTTPS ports.
- Multi-service load balancing.
- Docker service discovery.
- Docker Swarm service discovery.
- Failover retries.
