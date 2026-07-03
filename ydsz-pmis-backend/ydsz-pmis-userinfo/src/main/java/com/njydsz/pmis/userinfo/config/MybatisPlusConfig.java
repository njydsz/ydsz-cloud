package com.njydsz.pmis.userinfo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.njydsz.pmis.common.config.AuditFieldFiller;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页 + 乐观锁拦截器（PostgreSQL 方言）
     *
     * <p>P1-12 新增 OptimisticLockerInnerInterceptor，与 common 模块默认配置保持一致，
     * 支持实体 @Version 字段自动校验和自增。
     *
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 审计字段自动填充处理器
     *
     * @return 元对象填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AuditFieldFiller();
    }
}
