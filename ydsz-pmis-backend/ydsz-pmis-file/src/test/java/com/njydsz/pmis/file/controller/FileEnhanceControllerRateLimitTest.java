package com.njydsz.pmis.file.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileEnhanceController 限流注解测试。
 *
 * <p>P1-11: 验证文件上传入口（病毒扫描 / 分片初始化）已加 {@link RateLimit}（10 次 / 60 秒）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FileEnhanceController @RateLimit 注解测试")
class FileEnhanceControllerRateLimitTest {

    @Test
    @DisplayName("scanVirus 应配置 10 次/60 秒限流")
    void scanVirus_shouldBeRateLimited() throws NoSuchMethodException {
        Method scan = FileEnhanceController.class.getMethod("scanVirus", MultipartFile.class);
        RateLimit rateLimit = scan.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("scanVirus 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("file-upload");
        assertThat(rateLimit.qps()).isEqualTo(10);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("initMultipartUpload 应配置 10 次/60 秒限流")
    void initMultipartUpload_shouldBeRateLimited() throws NoSuchMethodException {
        Method init = FileEnhanceController.class.getMethod("initMultipartUpload",
                String.class, long.class, int.class);
        RateLimit rateLimit = init.getAnnotation(RateLimit.class);

        assertThat(rateLimit).as("initMultipartUpload 必须标注 @RateLimit").isNotNull();
        assertThat(rateLimit.key()).isEqualTo("file-upload");
        assertThat(rateLimit.qps()).isEqualTo(10);
        assertThat(rateLimit.windowSeconds()).isEqualTo(60);
    }
}
