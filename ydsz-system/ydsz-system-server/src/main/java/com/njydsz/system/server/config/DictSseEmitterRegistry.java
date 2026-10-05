package com.njydsz.system.server.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.system.server.service.event.DictChangeEventConstants;

@Slf4j
public class DictSseEmitterRegistry {

  private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

  public String register(SseEmitter emitter) {
    String sessionId = IdGenerator.nextIdStr();
    emitters.put(sessionId, emitter);
    emitter.onCompletion(() -> remove(sessionId));
    emitter.onTimeout(() -> remove(sessionId));
    emitter.onError(e -> remove(sessionId));
    log.debug("[DictSseEmitterRegistry] Registered SSE emitter: sessionId={}, total={}",
        sessionId, emitters.size());
    return sessionId;
  }

  public void remove(String sessionId) {
    SseEmitter emitter = emitters.remove(sessionId);
    if (emitter != null) {
      try {
        emitter.complete();
      } catch (Exception e) {
        log.debug("[DictSseEmitterRegistry] Emitter already completed: sessionId={}", sessionId);
      }
      log.debug("[DictSseEmitterRegistry] Removed SSE emitter: sessionId={}, remaining={}",
          sessionId, emitters.size());
    }
  }

  public void broadcastEvent(String json) {
    if (emitters.isEmpty()) {
      return;
    }
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
      String sessionId = entry.getKey();
      try {
        entry.getValue().send(SseEmitter.event().data(json));
      } catch (IOException e) {
        log.debug("[DictSseEmitterRegistry] Emitter send failed, removing: sessionId={}",
            sessionId);
        remove(sessionId);
      } catch (IllegalStateException e) {
        log.debug("[DictSseEmitterRegistry] Emitter already completed: sessionId={}",
            sessionId);
        remove(sessionId);
      } catch (Exception e) {
        log.warn("[DictSseEmitterRegistry] Unexpected error sending: sessionId={}, err={}",
            sessionId, e.getMessage());
        remove(sessionId);
      }
    }
  }

  public void broadcastKeepalive() {
    if (emitters.isEmpty()) {
      return;
    }
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
      String sessionId = entry.getKey();
      try {
        entry.getValue().send(DictChangeEventConstants.SSE_KEEPALIVE_PAYLOAD);
      } catch (Exception e) {
        log.debug("[DictSseEmitterRegistry] Keepalive failed, removing: sessionId={}",
            sessionId);
        remove(sessionId);
      }
    }
  }

  public int size() {
    return emitters.size();
  }
}