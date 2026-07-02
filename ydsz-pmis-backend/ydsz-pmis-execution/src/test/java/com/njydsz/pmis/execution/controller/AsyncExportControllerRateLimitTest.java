package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AsyncExportController 限流注解测试。
 *
 * <p>P1-11: 验证导出提交接口已加 {@link RateLimit}（3 次 / 60 秒）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AsyncExportController @RateLimit 注解测试")
class AsyncExportControllerRateLimitTest {

    @Test
    @DisplayName("submitExport 应配置 3 次/60 秒限流")
    void submitExport_shouldBeRateLimited() throws NoSuchMethodException {
        Method submit = AsyncExportController.class.getMethod("submitExport",
                Long.class, String.class, Map.class);
        RateLimit rateLimit = submit.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("submitExport 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("export");
        assertThat(rateLimit.qps()).isEqualTo(3);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }
}
