package edu.sjsu.cmpe172.tutorbook.notification;

import java.net.http.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the notification service over HTTP with the three fault-handling tools:
 *
 *   TIMEOUT  connect + read timeouts, so a slow service cannot hang a booking.
 *   RETRY    up to maxAttempts tries with exponential backoff (200 ms, 400 ms, ...)
 *            for errors that may go away: connection refused, timeout, 5xx, 429.
 *            A 4xx means our request is wrong, so retrying would not help.
 *   FALLBACK never throws; returns SendResult.failed(...) and the caller
 *            records the notification as FAILED for NotificationRetryJob.
 *
 * Retrying is safe because every attempt sends the same Idempotency-Key, so
 * the service keeps at most one copy even if an attempt that "timed out" had
 * actually arrived.
 */
@Component
public class HttpNotificationGateway implements NotificationGateway {

    static final String PATH = "/mock-notification-service/notifications";

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final NotificationProperties props;
    private final Environment env;
    private final RestClient restClient;

    public HttpNotificationGateway(NotificationProperties props, Environment env) {
        this.props = props;
        this.env = env;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public SendResult send(NotificationRequest request) {
        long backoffMs = props.initialBackoff().toMillis();
        String lastError = null;
        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            try {
                NotificationResponse response = restClient.post()
                        .uri(baseUrl() + PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .body(request)
                        .retrieve()
                        .body(NotificationResponse.class);
                log.info("Notification {} delivered on attempt {} (service status {})",
                        request.idempotencyKey(), attempt, response == null ? "?" : response.status());
                return SendResult.delivered(attempt);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() != 429) {
                    // 400/404/...: our request is wrong; retrying cannot fix it
                    log.error("Notification {} rejected with {}; not retrying", request.idempotencyKey(), e.getStatusCode());
                    return SendResult.failed(attempt, false, "HTTP " + e.getStatusCode().value());
                }
                lastError = "HTTP 429";
            } catch (RestClientResponseException e) {
                lastError = "HTTP " + e.getStatusCode().value(); // 5xx: service is up but failing
            } catch (RestClientException e) {
                lastError = describe(e); // connection refused or timed out
            }
            if (attempt < props.maxAttempts()) {
                log.warn("Notification {} attempt {}/{} failed ({}); retrying in {} ms",
                        request.idempotencyKey(), attempt, props.maxAttempts(), lastError, backoffMs);
                sleep(backoffMs);
                backoffMs *= 2;
            }
        }
        log.error("Notification {} failed after {} attempts ({}); booking is kept, will retry later",
                request.idempotencyKey(), props.maxAttempts(), lastError);
        return SendResult.failed(props.maxAttempts(), true, lastError);
    }

    /** Configured URL, or this app's own port when the mock runs in-process. */
    String baseUrl() {
        if (StringUtils.hasText(props.baseUrl())) {
            return props.baseUrl();
        }
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }

    private static String describe(RestClientException e) {
        String name = e.getMostSpecificCause().getClass().getSimpleName();
        if (name.contains("Timeout")) {
            return "timeout";
        }
        if (name.equals("ConnectException") || name.equals("ClosedChannelException")) {
            return "connection refused";
        }
        return name;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
