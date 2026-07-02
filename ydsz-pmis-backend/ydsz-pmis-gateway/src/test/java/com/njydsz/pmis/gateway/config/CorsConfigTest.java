package com.njydsz.pmis.gateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 跨域配置单元测试
 *
 * <p>验证 CorsWebFilter 正确创建且不抛异常。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class CorsConfigTest {

    /** 待测跨域配置实例 */
    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    void corsWebFilterShouldNotBeNull() {
        assertNotNull(corsConfig.corsWebFilter(), "CorsWebFilter 不应为 null");
    }
}
