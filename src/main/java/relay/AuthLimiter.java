package relay;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;

final class AuthLimiter {
    private final LinkedHashMap<String, Deque<Long>> attempts = new LinkedHashMap<>(16, 0.75f, true);

    synchronized void check(String address) {
        long now = System.nanoTime();
        Deque<Long> history = attempts.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        while (!history.isEmpty() && now - history.peekFirst() >= Duration.ofMinutes(1).toNanos()) {
            history.removeFirst();
        }
        if (history.size() >= 30) {
            throw new Problem(429, "Too many sign-in attempts. Try again in one minute.");
        }
        history.addLast(now);
        if (attempts.size() > 4096) {
            attempts.remove(attempts.keySet().iterator().next());
        }
    }
}
