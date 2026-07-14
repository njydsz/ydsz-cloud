package com.njydsz.pmis.common.core.context;

import java.util.HashMap;
import java.util.Map;

import com.alaiba.ttl.TransmittableThreadLocal;

public final class CrossProtocolContextPropagator {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";
    public static final String USER_ID_HEADER = "X-User-Id";

    private static final ThreadLocal<Map<String, String>> CONTEXT =
            new TransmittableThreadLocal<Map<String, String>>() {
                @Override
                protected Map<String, String> initialValue() {
                    return new HashMap<>();
                }
            };

    private CrossProtocolContextPropagator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void put(String key, String value) {
        if (key == null) { return; }
        if (value == null) { CONTEXT.get().remove(key); return; }
        CONTEXT.get().put(key, value);
    }

    public static String get(String key) {
        if (key == null) { return null; }
        return CONTEXT.get().get(key);
    }

    public static Map<String, String> snapshot() {
        return Map.copyOf(CONTEXT.get());
    }

    public static void restore(Map<String, String> ctx) {
        CONTEXT.remove();
        if (ctx != null && !ctx.isEmpty()) { CONTEXT.set(new HashMap<>(ctx)); }
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
