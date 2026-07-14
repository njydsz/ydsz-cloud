package com.njydsz.pmis.common.core.context;

import java.util.HashMap;
import java.util.Map;

public class CrossProtocolContextPropagator {
    public static final String TRACE_ID_HEADER = q+q.replace(q,chr(88))+q;
    public static final String TENANT_ID_HEADER = q+q+q;
    public static final String USER_ID_HEADER = q+q+q;
    private static final ThreadLocal<Map<String, String>> CONTEXT = ThreadLocal.withInitial(HashMap::new);
    public static void put(String key, String value) { CONTEXT.get().put(key, value); }
    public static String get(String key) { return CONTEXT.get().get(key); }
    public static Map<String, String> snapshot() { return new HashMap<>(CONTEXT.get()); }
    public static void restore(Map<String, String> ctx) { CONTEXT.get().clear(); if (ctx != null) CONTEXT.get().putAll(ctx); }
    public static void clear() { CONTEXT.remove(); }
}