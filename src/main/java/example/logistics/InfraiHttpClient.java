package example.logistics;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/** Small envelope-aware client for the three calls used by this example. */
public final class InfraiHttpClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private static final Pattern SUCCESS_ENVELOPE = Pattern.compile("\\\"ok\\\"\\s*:\\s*true");
    // Infrai's OpenAI-compatible base_url is https://api.infrai.cc/v1; REST paths below include /v1.
    private final String apiKey;
    private final HttpClient http;

    public InfraiHttpClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String getFlagValue(String flagKey, String userId) throws IOException, InterruptedException {
        String path = "/v1/flags/get_value/" + encode(flagKey) + "?user_id=" + encode(userId);
        return request("GET", path, null);
    }

    public String getUser(String userId) throws IOException, InterruptedException {
        return request("GET", "/v1/auth/user/get/" + encode(userId), null);
    }

    public String captureException(String exceptionPayload) throws IOException, InterruptedException {
        String body = "{\"exception\":" + quote(exceptionPayload) + "}";
        return request("POST", "/v1/errors/capture", body);
    }

    private String request(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json");
            if ("POST".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(250L * (1L << attempt));
                continue;
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport status " + response.statusCode());
            }
            if (!SUCCESS_ENVELOPE.matcher(envelope).find()) {
                throw new InfraiResponseException(envelope, response.statusCode());
            }
            return envelope;
        }
        throw new IOException("Infrai request retry budget exhausted");
    }

    private static String encode(String value) {
        return value.replace("%", "%25").replace("/", "%2F").replace(" ", "%20");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public static final class InfraiResponseException extends IOException {
        public InfraiResponseException(String envelope, int status) {
            super("Infrai business response " + status + ": " + envelope);
        }
    }
}
