package com.njydsz.pmis.common.core.actuator;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import com.njydsz.pmis.common.core.concurrent.ThreadPoolRegistry;

@Endpoint(id = "ydszThreadPool")
public class ThreadPoolEndpoint {
    private final ThreadPoolRegistry registry;
    public ThreadPoolEndpoint(ThreadPoolRegistry registry) { this.registry = registry; }
    @ReadOperation
    public Map<String, Object> threadPools() {
        Map<String, Object> result = new LinkedHashMap<>();
        return result;
    }
}