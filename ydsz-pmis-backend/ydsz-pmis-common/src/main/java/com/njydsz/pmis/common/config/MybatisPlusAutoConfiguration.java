package com.njydsz.pmis.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.njydsz.pmis.common.util.PmisSnowflakeIdentifierGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 默认拦截器配置
 *
 * <p>所有引入 ydsz-pmis-common 的业务模块（project / execution / audit / agent / file 等）
 * 自动获得以下四个拦截器：
 * <ul>
 *   <li>{@link TenantLineInnerInterceptor} — 多租户行级隔离（H3.1 修复），
 *       自动为 SQL 追加 {@code WHERE tenant_id = ?}。当前阶段单租户部署（tenant_id 恒为 1），
 *       作为前置防御启用。租户 ID 来自 {@link com.njydsz.pmis.common.security.TenantContext}。</li>
 *   <li>{@link PaginationInnerInterceptor} — 分页查询支持（PostgreSQL 方言）</li>
 *   <li>{@link OptimisticLockerInnerInterceptor} — 乐观锁支持（P1-12），
 *       实体需在版本字段上标注 {@code @com.baomidou.mybatisplus.annotation.Version}。</li>
 *   <li>{@link BlockAttackInnerInterceptor} — 防全表更新/删除（P0-3），
 *       阻止不带 WHERE 条件的 UPDATE/DELETE 语句。</li>
 * </ul>
 *
 * <p>顺序说明（MyBatis-Plus 官方推荐）：
 * <ol>
 *   <li>TenantLine — 必须最先，确保后续拦截器看到带 tenant_id 的 SQL</li>
 *   <li>Pagination</li>
 *   <li>BlockAttack — UPDATE/DELETE 时校验 WHERE 条件，防止全表操作</li>
 *   <li>OptimisticLocker — 最后，UPDATE 时校验 version</li>
 * </ol>
 *
 * <p>覆盖策略：业务模块可通过自定义 {@link MybatisPlusInterceptor} Bean
 * （{@code @Bean public MybatisPlusInterceptor ...}）覆盖此默认配置。
 * 当前已自定义的模块：ydsz-pmis-userinfo / ydsz-pmis-system。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
public class MybatisPlusAutoConfiguration {

    /**
     * 默认 MybatisPlusInterceptor（多租户 + 分页 + 乐观锁）
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
        // 1. 多租户拦截器（H3.1 修复）：必须最先，自动追加 WHERE tenant_id = ?
        //    忽略 undo_log 等无 tenant_id 列的表
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new PmisTenantLineHandler()));
        // 2. 分页拦截器（PostgreSQL 方言）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        // 3. 防全表更新/删除拦截器（P0-3）：阻止不带 WHERE 条件的 UPDATE/DELETE
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        // 4. 乐观锁拦截器（P1-12）：实体需标注 @Version 字段，UPDATE 时自动 SET version = version + 1
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 主键生成器（雪花算法字符串版，19 位 VARCHAR(20)）
     *
     * <p>大厂规范：所有主键由应用层雪花算法生成，数据库不做自增。
     * 配合实体 {@code @TableId(type = IdType.ASSIGN_ID)} 使用，
     * MyBatis-Plus 自动将雪花 Long 转为字符串写入 VARCHAR(20) 主键列。
     *
     * <p>使用 {@code @ConditionalOnMissingBean} 允许业务模块覆盖。
     *
     * @return IdentifierGenerator 实例
     */
    @Bean
    @ConditionalOnMissingBean(IdentifierGenerator.class)
    public IdentifierGenerator identifierGenerator() {
        return new PmisSnowflakeIdentifierGenerator();
    }
}
