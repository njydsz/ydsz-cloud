package com.njydsz.pmis.common.sentry.trace;
public interface TraceSpanManager {
    void startSpan(String name);
    void endSpan();
    void addTag(String key, String value);
}