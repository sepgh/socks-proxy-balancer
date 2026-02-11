package com.github.sepgh.server;

import com.github.sepgh.health.HealthChecker;
import com.github.sepgh.health.ProxyTestResult;
import com.github.sepgh.proxy.ProxyClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Lightweight HTTP server that exposes application status information.
 * <p>
 * When enabled via {@code status_enabled: true} in config, this server provides
 * a single {@code GET /status} endpoint returning JSON with:
 * <ul>
 *   <li><b>selected_proxy</b> - Name of the currently selected proxy config</li>
 *   <li><b>selected_since</b> - ISO-8601 timestamp of when the current proxy was selected</li>
 *   <li><b>selected_duration_seconds</b> - How long the current proxy has been selected (without restart)</li>
 *   <li><b>listen_host</b> / <b>listen_port</b> - The SOCKS server binding address</li>
 *   <li><b>proxy_latencies</b> - Last measured latency (ms) for each tested proxy config</li>
 * </ul>
 * <p>
 * Configuration example in {@code config.yaml}:
 * <pre>
 * status_enabled: true
 * status_host: "127.0.0.1"
 * status_port: 9080
 * </pre>
 */
public class StatusHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(StatusHttpServer.class);

    private final String host;
    private final int port;
    private final String listenHost;
    private final int listenPort;
    private final HealthChecker healthChecker;
    private HttpServer httpServer;

    public StatusHttpServer(String host, int port, String listenHost, int listenPort, HealthChecker healthChecker) {
        this.host = host;
        this.port = port;
        this.listenHost = listenHost;
        this.listenPort = listenPort;
        this.healthChecker = healthChecker;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.createContext("/status", this::handleStatus);
        httpServer.setExecutor(null);
        httpServer.start();
        logger.info("Status HTTP server started on {}:{}", host, port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            logger.info("Status HTTP server stopped");
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String json = buildStatusJson();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String buildStatusJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Selected proxy info
        ProxyClient selected = healthChecker.getSelectedProxy();
        Instant since = healthChecker.getSelectedProxySince();

        if (selected != null) {
            sb.append("  \"selected_proxy\": \"").append(escapeJson(selected.getName())).append("\",\n");
        } else {
            sb.append("  \"selected_proxy\": null,\n");
        }

        if (since != null) {
            sb.append("  \"selected_since\": \"").append(since.toString()).append("\",\n");
            long durationSeconds = Duration.between(since, Instant.now()).getSeconds();
            sb.append("  \"selected_duration_seconds\": ").append(durationSeconds).append(",\n");
        } else {
            sb.append("  \"selected_since\": null,\n");
            sb.append("  \"selected_duration_seconds\": null,\n");
        }

        // Listening info
        sb.append("  \"listen_host\": \"").append(escapeJson(listenHost)).append("\",\n");
        sb.append("  \"listen_port\": ").append(listenPort).append(",\n");

        // Proxy latencies
        Map<String, ProxyTestResult> results = healthChecker.getLastTestResults();
        sb.append("  \"proxy_latencies\": {");
        if (results != null && !results.isEmpty()) {
            sb.append("\n");
            int i = 0;
            for (Map.Entry<String, ProxyTestResult> entry : results.entrySet()) {
                sb.append("    \"").append(escapeJson(entry.getKey())).append("\": ");
                ProxyTestResult result = entry.getValue();
                if (result.isSuccess()) {
                    sb.append("{\"success\": true, \"latency_ms\": ").append(result.getLatencyMs()).append("}");
                } else {
                    sb.append("{\"success\": false, \"error\": \"").append(escapeJson(
                            result.getErrorMessage() != null ? result.getErrorMessage() : "unknown")).append("\"}");
                }
                if (++i < results.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ");
        }
        sb.append("}\n");

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
