package serviceforce;

import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Transactional mail over HTTPS, which works on Railway's Free plan. */
interface MailDelivery {
    void send(String to, String subject, String body);

    static MailDelivery configured() {
        String key = System.getenv("BREVO_API_KEY");
        String from = System.getenv("SERVICEFORCE_EMAIL_FROM");
        if (key == null || key.isBlank() || from == null || from.isBlank())
            return (to, subject, body) -> { throw new Problem(503, "Email delivery is not configured. Ask the administrator to connect an email provider."); };
        return new Brevo(key, from);
    }

    final class Brevo implements MailDelivery {
        private static final Gson JSON = new Gson();
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private final String key, from;

        Brevo(String key, String from) { this.key = key; this.from = from; }

        public void send(String to, String subject, String body) {
            try {
                var payload = Map.of("sender", Map.of("name", "ServiceForce", "email", from),
                        "to", new Object[]{Map.of("email", to)}, "subject", subject, "textContent", body);
                var request = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                        .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                        .header("api-key", key).POST(HttpRequest.BodyPublishers.ofString(JSON.toJson(payload))).build();
                var response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new Problem(503, "Email could not be delivered. Please try again later.");
            } catch (Problem e) { throw e; }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Problem(503, "Email could not be delivered. Please try again later.");
            } catch (Exception e) {
                throw new Problem(503, "Email could not be delivered. Please try again later.");
            }
        }
    }
}
