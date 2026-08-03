package com.njydsz.common.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.ResourceBundleMessageSource;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.BaseResponse;

/**
 * {@link CoreAutoConfiguration} 自动装配集成测试。
 *
 * <p>覆盖：默认启用、显式开关、PageConstants 运行时同步、i18n resolver 注册、
 * 无 MessageSource 时优雅降级。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@DisplayName("CoreAutoConfiguration 自动装配测试")
class CoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        // 清理静态状态，避免测试间污染
        BaseResponse.setResolver(null);
        PageConstants.setMaxPageSize(1000);
        PageConstants.setDefaultPageSize(20);
    }

    @Test
    @DisplayName("默认启用：注册 PageConstantsInitializer 并同步分页配置")
    void defaultEnabled_syncsPageConstants() {
        contextRunner
                .withPropertyValues(
                        "ydsz.core.max-page-size=500",
                        "ydsz.core.default-page-size=50")
                .run(context -> {
                    assertThat(context).hasSingleBean(CoreAutoConfiguration.PageConstantsInitializer.class);
                    // SmartInitializingSingleton 在上下文刷新后同步
                    assertThat(PageConstants.getMaxPageSize()).isEqualTo(500);
                    assertThat(PageConstants.getDefaultPageSize()).isEqualTo(50);
                });
    }

    @Test
    @DisplayName("显式关闭 ydsz.core.enabled=false 时不注册任何 Bean")
    void disabled_noBeans() {
        contextRunner
                .withPropertyValues("ydsz.core.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CoreAutoConfiguration.PageConstantsInitializer.class);
                    assertThat(context).doesNotHaveBean(SpringMessageResolver.class);
                });
    }

    @Test
    @DisplayName("存在 MessageSource 时注册 SpringMessageResolver 并绑定 BaseResponse")
    void messageSource_registersResolver() {
        contextRunner
                .withBean(ResourceBundleMessageSource.class, () -> {
                    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
                    source.setBasename("i18n/messages");
                    source.setDefaultEncoding("UTF-8");
                    return source;
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(SpringMessageResolver.class);
                    assertThat(BaseResponse.isResolverRegistered()).isTrue();
                    assertThat(BaseResponse.success().getMsg()).isNotBlank();
                });
    }

    @Test
    @DisplayName("无 MessageSource 时不注册 resolver，BaseResponse 保持未绑定")
    void noMessageSource_gracefulDegrade() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SpringMessageResolver.class);
            assertThat(BaseResponse.isResolverRegistered()).isFalse();
        });
    }

    @Test
    @DisplayName("默认配置值同步到 PageConstants")
    void defaults_synced() {
        contextRunner.run(context -> {
            assertThat(PageConstants.getMaxPageSize()).isEqualTo(1000);
            assertThat(PageConstants.getDefaultPageSize()).isEqualTo(20);
        });
    }
}
