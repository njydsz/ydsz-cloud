package com.njydsz.pmis.common.web;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API 版本信息 Controller。
 * 提供 GET /api/version 查询当前 API 版本和可用版本列表。
 */
@RestController
public class ApiVersionController {

    @GetMapping("/api/version")
    public Map<String, Object> getVersion() {
        return Map.of(
                "current", "v1",
                "releasedAt", "2026-07-02",
                "available", new String[]{"v1"},
                "deprecated", new String[]{}
        );
    }

    @Hidden
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "version", "v1");
    }
}
