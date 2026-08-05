package com.remisoft.common.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.remisoft.common.core.constant.PageConstants;

/**
 * {@link CoreAutoConfiguration} 自动装配集成测试。
 *
 * <p>覆盖：默认启用、显式开关、PageConstants 运行时同步、
 * i18n resolver 注册（v2.1.0 起由 remi-common-base 的 I18nAutoConfiguration 注册）。
 *
 * @author remi-team
 * @since 1.2.0
 */
@DisplayName("CoreAutoConfiguration 自动装配测试")
class CoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CoreAutoConfiguration.class));

    @AfterEach
    void tearDown() {
        PageConstants.__testReset();
    }

    @Test
    @DisplayName("默认启用：注册 PageConstantsInitializer 并同步分页配置")
    void defaultEnabled_syncsPageConstants() {
        contextRunner
                .withPropertyValues(
                        "remi.core.max-page-size=500",
                        "remi.core.default-page-size=50")
                .run(context -> {
                    assertThat(context).hasSingleBean(CoreAutoConfiguration.PageConstantsInitializer.class);
                    assertThat(PageConstants.getMaxPageSize()).isEqualTo(500);
                    assertThat(PageConstants.getDefaultPageSize()).isEqualTo(50);
                });
    }

    @Test
    @DisplayName("显式关闭 remi.core.enabled=false 时不注册任何 Bean")
    void disabled_noBeans() {
        contextRunner.withPropertyValues("remi.core.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CoreAutoConfiguration.PageConstantsInitializer.class);
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
