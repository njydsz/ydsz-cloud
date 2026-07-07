package com.njydsz.pmis.userinfo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.njydsz.pmis.common.config.PmisTenantLineHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（userinfo 模块自定义拦截器链）
 *
 * <p>覆盖 common 模块 {@code MybatisPlusAutoConfiguration} 的默认拦截器，
 * 保持与 common 一致的四件套（TenantLine + Pagination + BlockAttack + OptimisticLocker）。
 *
 * <p>主键生成器 {@code IdentifierGenerator} 和审计字段填充器 {@code MetaObjectHandler}
 * 已由 common 模块统一注册（{@code @ConditionalOnMissingBean}），此处不再重复声明。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页 + 多租户 + 防全表操作 + 乐观锁拦截器（PostgreSQL 方言）
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 多租户拦截器：必须最先，自动追加 WHERE tenant_id = ?
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new PmisTenantLineHandler()));
        // 2. 分页拦截器（PostgreSQL 方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        // 3. 防全表更新/删除拦截器（P0-3）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        // 4. 乐观锁拦截器（P1-12）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
