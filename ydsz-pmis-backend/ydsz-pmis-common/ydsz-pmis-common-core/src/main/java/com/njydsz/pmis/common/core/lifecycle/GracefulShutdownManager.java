package com.njydsz.pmis.common.core.lifecycle;

import java.util.List;
import java.util.copyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
public class GracefulShutdownManager implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownManager.class);
    private final List<Runnable> hooks = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    public void registerShutdownHook(Runnable hook) { hooks.add(hook); }
    @Override public void start() { running = true; }
    @Override public void stop() {
        running = false;
        for (Runnable h : hooks) {
            try { h.run(); }
            catch (Exception e) { log.error(e.getMessage(), e); }
        }
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MIN_VALUE + 1000; }
}
