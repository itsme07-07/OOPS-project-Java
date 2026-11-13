package relay;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class EmailCodes {
    interface Sender { void send(String email, String code); }
    private record Challenge(String token, String code, Instant issued, int failures) {}
    private final SecureRandom random = new SecureRandom();
    private final Map<String,Challenge> pending = new ConcurrentHashMap<>();
    private final Sender sender;
    EmailCodes(Sender sender) { this.sender = sender; }

    static String address(Object value) {
        if (!(value instanceof String raw)) throw new Problem(400, "Enter an email address.");
        String email = raw.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@<>(),;:]+@[^\\s@<>(),;:]+\\.[^\\s@<>(),;:]+"))
            throw new Problem(400, "Enter a valid email address.");
        return email;
    }

    String issue(String email) {
        Instant now = Instant.now();
        pending.entrySet().removeIf(e -> e.getValue().issued().plusSeconds(600).isBefore(now));
        var fresh = new Challenge(UUID.randomUUID().toString(),
                String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)), now, 0);
        pending.compute(email, (key, old) -> {
            if (old != null && old.issued().plusSeconds(60).isAfter(now))
                throw new Problem(429, "Wait a minute before requesting another code.");
            return fresh;
        });
        try { sender.send(email, fresh.code()); }
        catch (RuntimeException failure) { pending.remove(email, fresh); throw failure; }
        return fresh.token();
    }

    void redeem(String email, Object token, Object code) {
        boolean[] accepted = {false};
        pending.computeIfPresent(email, (key, entry) -> {
            if (entry.issued().plusSeconds(600).isBefore(Instant.now())) return null;
            if (!entry.token().equals(token)) return entry;
            if (entry.code().equals(code)) { accepted[0] = true; return null; }
            int failures = entry.failures() + 1;
            return failures >= 5 ? null : new Challenge(entry.token(),entry.code(),entry.issued(),failures);
        });
        if (!accepted[0]) throw new Problem(400, "Invalid or expired verification code. Try again or request another code.");
    }

    static void sendMail(String email, String code) {
        Map<String,String> env = System.getenv();
        for (String key : List.of("RELAY_SMTP_HOST","RELAY_SMTP_USER","RELAY_SMTP_PASSWORD","RELAY_SMTP_FROM"))
            if (env.getOrDefault(key,"").isBlank()) throw new Problem(503,"Email delivery is not configured. Contact your administrator.");
        Properties props = new Properties();
        props.setProperty("mail.smtp.host",env.get("RELAY_SMTP_HOST"));
        props.setProperty("mail.smtp.port",env.getOrDefault("RELAY_SMTP_PORT","587"));
        props.setProperty("mail.smtp.auth","true");
        props.setProperty("mail.smtp.starttls.enable","true");
        props.setProperty("mail.smtp.starttls.required","true");
        props.setProperty("mail.smtp.ssl.checkserveridentity","true");
        for (String option : List.of("timeout","connectiontimeout","writetimeout"))
            props.setProperty("mail.smtp." + option,"10000");
        try {
            var message = new MimeMessage(Session.getInstance(props));
            message.setFrom(new InternetAddress(env.get("RELAY_SMTP_FROM"),true));
            message.setRecipient(Message.RecipientType.TO,new InternetAddress(email,true));
            message.setSubject("Verify your Relay email address");
            message.setText("Your Relay verification code is " + code + ". It expires in 10 minutes. If you did not request this, ignore this email.");
            Transport.send(message,env.get("RELAY_SMTP_USER"),env.get("RELAY_SMTP_PASSWORD"));
        } catch (MessagingException e) {
            throw new Problem(503,"Relay could not send the email. Please try again later.");
        }
    }
}
