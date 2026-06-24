package br.com.digitado.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class OnlineUsersService {

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Instant> lastSeen = new ConcurrentHashMap<>();

    public void markOnline(String login) {
        lastSeen.put(login, Instant.now());
    }

    public long getOnlineCount() {
        Instant threshold = Instant.now().minus(ONLINE_THRESHOLD);
        return lastSeen.values().stream().filter(t -> t.isAfter(threshold)).count();
    }
}
