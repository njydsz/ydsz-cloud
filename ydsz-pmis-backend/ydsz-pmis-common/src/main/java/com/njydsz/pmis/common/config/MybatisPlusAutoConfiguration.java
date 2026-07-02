package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 默认拦截器配置
 *
 * <p>所有引入 ydsz-pmis-common 的业务模块（project / execution / audit / agent / file 等）
 * 自动获得以下两个拦截器：
 * <ul>
 *   <li>{@link PaginationInnerInterceptor} — 分页查询支持（PostgreSQL 方言）</li>
 *   <li>{@link OptimisticLockerInnerInterceptor} — 乐观锁支持（P1-12），
 *       实体需在版本字段上标注 {@code @com.baomidou.mybatisplus.annotation.Version}。</li>
 * </ul>
 *
 * <p>顺序说明：MyBatis-Plus 官方建议乐观锁拦截器在分页拦截器之后添加，
 * 这样 UPDATE 时先校验 version 再分页查询，避免分页后的 UPDATE 丢失乐观锁校验。
 *
 * <p>覆盖策略：业务模块可通过自定义 {@link MybatisPlusInterceptor} Bean
 * （{@code @Bean public MybatisPlusInterceptor ...}）覆盖此默认配置。
 * 当前已自定义的模块：ydsz-pmis-user / ydsz-pmis-notification / ydsz-pmis-config。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusAutoConfiguration {

    /**
     * 默认 MybatisPlusInterceptor（分页 + 乐观锁）
     *
     * <p>仅在容器中没有其他 MybatisPlusInterceptor Bean 时生效，
     * 避免与业务模块自定义配置冲突。
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 分页拦截器（PostgreSQL 方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        // 2. 乐观锁拦截器（P1-12）：实体需标注 @Version 字段，UPDATE 时自动 SET version = version + 1
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
