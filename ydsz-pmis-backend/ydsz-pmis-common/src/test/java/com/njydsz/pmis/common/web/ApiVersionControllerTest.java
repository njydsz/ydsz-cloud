package com.njydsz.pmis.common.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiVersionController 单元测试。
 */
class ApiVersionControllerTest {

    private final ApiVersionController controller = new ApiVersionController();

    @Test
    void getVersion_shouldReturnCurrentVersion() {
        Map<String, Object> result = controller.getVersion();
        assertThat(result.get("current")).isEqualTo("v1");
        assertThat(result.get("releasedAt")).isEqualTo("2026-07-02");
    }

    @Test
    void getVersion_shouldReturnAvailableVersions() {
        Map<String, Object> result = controller.getVersion();
        String[] available = (String[]) result.get("available");
        assertThat(available).contains("v1");
    }

    @Test
    void getVersion_shouldReturnEmptyDeprecatedList() {
        Map<String, Object> result = controller.getVersion();
        String[] deprecated = (String[]) result.get("deprecated");
        assertThat(deprecated).isEmpty();
    }

    @Test
    void health_shouldReturnUpStatus() {
        Map<String, Object> result = controller.health();
        assertThat(result.get("status")).isEqualTo("UP");
        assertThat(result.get("version")).isEqualTo("v1");
    }
}
