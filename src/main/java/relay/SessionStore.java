package relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Session tokens are rotated on login; only their SHA-256 digests live in memory. */
final class SessionStore {
    static final Duration LIFETIME = Duration.ofHours(8);

    private record Session(int userId, long expiresAt) {}
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();

    synchronized String create(int userId) {
        long now = System.nanoTime();
        sessions.values().removeIf(session -> now - session.expiresAt() >= 0);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(digest(token), new Session(userId, now + LIFETIME.toNanos()));
        return token;
    }

    synchronized int resolve(String token) {
        String key = digest(token);
        Session session = sessions.get(key);
        if (session == null || System.nanoTime() - session.expiresAt() >= 0) {
            sessions.remove(key);
            throw new Problem(401, "Sign in to access your workspace.");
        }
        return session.userId();
    }

    synchronized void revoke(String token) {
        sessions.remove(digest(token));
    }

    private static String digest(String token) {
        try {
            byte[] input = token.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
