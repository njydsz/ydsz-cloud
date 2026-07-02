package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MybatisPlusAutoConfiguration} 单元测试
 *
 * <p>验证默认拦截器配置正确包含分页 + 乐观锁两个 InnerInterceptor。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MybatisPlusAutoConfiguration 默认拦截器配置测试")
class MybatisPlusAutoConfigurationTest {

    @Test
    @DisplayName("默认 MybatisPlusInterceptor 应包含 2 个 InnerInterceptor")
    void defaultInterceptorShouldContainPaginationAndOptimisticLocker() {
        MybatisPlusAutoConfiguration config = new MybatisPlusAutoConfiguration();
        MybatisPlusInterceptor interceptor = config.mybatisPlusInterceptor();

        List<InnerInterceptor> inners = interceptor.getInterceptors();
        assertThat(inners).hasSize(2);

        // 第 1 个应为 PaginationInnerInterceptor
        String firstName = inners.get(0).getClass().getSimpleName();
        // 第 2 个应为 OptimisticLockerInnerInterceptor
        String secondName = inners.get(1).getClass().getSimpleName();

        assertThat(firstName).contains("Pagination");
        assertThat(secondName).contains("OptimisticLocker");
    }

    @Test
    @DisplayName("多次调用应返回独立实例")
    void multipleCallReturnIndependentInstances() {
        MybatisPlusAutoConfiguration config = new MybatisPlusAutoConfiguration();
        MybatisPlusInterceptor i1 = config.mybatisPlusInterceptor();
        MybatisPlusInterceptor i2 = config.mybatisPlusInterceptor();
        assertThat(i1).isNotSameAs(i2);
        assertThat(i1.getInterceptors()).hasSize(2);
        assertThat(i2.getInterceptors()).hasSize(2);
    }
}
