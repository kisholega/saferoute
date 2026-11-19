package com.saferoute.ai.service.trip;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live updates for tracking pages, using Server-Sent Events. Each tracking token has its own list of
 * open connections; every position update or status change is pushed to all of them at once.
 */
@Component
public class TrackingHub {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> listeners = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String token) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        List<SseEmitter> list = listeners.computeIfAbsent(token, t -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return emitter;
    }

    public void publish(String token, String event, Object data) {
        if (token == null) {
            return;
        }
        List<SseEmitter> list = listeners.get(token);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException ex) {
                list.remove(emitter);
            }
        }
    }

    public int listenerCount(String token) {
        List<SseEmitter> list = listeners.get(token);
        return list == null ? 0 : list.size();
    }

    /** Keeps idle connections open through proxies and removes dead ones. */
    @Scheduled(fixedDelay = 20_000)
    public void heartbeat() {
        listeners.forEach((token, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    list.remove(emitter);
                }
            }
        });
        listeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
