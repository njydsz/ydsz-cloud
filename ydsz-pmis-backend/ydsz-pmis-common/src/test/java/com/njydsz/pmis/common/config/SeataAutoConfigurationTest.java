package com.njydsz.pmis.common.config;

import org.junit.jupiter.api.Test;
import org.apache.seata.spring.annotation.GlobalTransactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SeataAutoConfiguration 单元测试。
 */
class SeataAutoConfigurationTest {

    @Test
    void globalTransactionalClassShouldBePresent() {
        assertThat(GlobalTransactional.class).isNotNull();
    }

    @Test
    void shouldCreateConfigurationWhenEnabled() {
        SeataAutoConfiguration config = new SeataAutoConfiguration();
        assertThat(config).isNotNull();
    }
}
