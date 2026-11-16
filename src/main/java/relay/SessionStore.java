package relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Session tokens are rotated on login; only their SHA-256 digests live in memory. */
final class SessionStore {
    static final Duration LIFETIME = Duration.ofHours(8);

    private record Session(String id, int userId, long expiresAt, String device, Instant createdAt, Instant lastSeenAt) {}
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();

    synchronized String create(int userId, String userAgent) {
        long now = System.nanoTime();
        sessions.values().removeIf(session -> now - session.expiresAt() >= 0);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant at = Instant.now();
        sessions.put(digest(token), new Session(UUID.randomUUID().toString(), userId, now + LIFETIME.toNanos(),
                deviceName(userAgent), at, at));
        return token;
    }

    synchronized int resolve(String token) {
        String key = digest(token);
        Session session = sessions.get(key);
        if (session == null || System.nanoTime() - session.expiresAt() >= 0) {
            sessions.remove(key);
            throw new Problem(401, "Sign in to access your workspace.");
        }
        sessions.put(key, new Session(session.id(), session.userId(), session.expiresAt(), session.device(),
                session.createdAt(), Instant.now()));
        return session.userId();
    }

    synchronized List<Map<String,Object>> list(int userId, String currentToken) {
        resolve(currentToken);
        String currentKey = digest(currentToken);
        List<Map<String,Object>> result = new ArrayList<>();
        for (var entry : sessions.entrySet()) {
            Session s = entry.getValue();
            if (s.userId() == userId) result.add(Map.of("id", s.id(), "device", s.device(),
                    "createdAt", s.createdAt().toString(), "lastSeenAt", s.lastSeenAt().toString(),
                    "current", entry.getKey().equals(currentKey)));
        }
        result.sort((a,b) -> b.get("lastSeenAt").toString().compareTo(a.get("lastSeenAt").toString()));
        return result;
    }

    synchronized void revokeDevice(int userId, String currentToken, String id) {
        resolve(currentToken);
        String currentKey = digest(currentToken);
        sessions.entrySet().removeIf(entry -> entry.getValue().userId() == userId &&
                entry.getValue().id().equals(id) && !entry.getKey().equals(currentKey));
    }

    private static String deviceName(String agent) {
        if (agent == null || agent.isBlank()) return "Unknown device";
        String browser = agent.contains("Edg/") ? "Edge" : agent.contains("Chrome/") ? "Chrome" :
                agent.contains("Firefox/") ? "Firefox" : agent.contains("Safari/") ? "Safari" : "Browser";
        String system = agent.contains("iPhone") ? "iPhone" : agent.contains("iPad") ? "iPad" :
                agent.contains("Android") ? "Android" : agent.contains("Mac OS") ? "Mac" :
                agent.contains("Windows") ? "Windows PC" : agent.contains("Linux") ? "Linux device" : "device";
        return browser + " on " + system;
    }

    synchronized void revoke(String token) {
        sessions.remove(digest(token));
    }

    synchronized void revokeUser(int userId) {
        sessions.values().removeIf(session -> session.userId() == userId);
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
