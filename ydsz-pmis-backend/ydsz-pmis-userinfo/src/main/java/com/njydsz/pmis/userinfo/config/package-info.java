/**
 * userinfo 模块配置包。
 *
 * <p>集中托管 userinfo 微服务专属的 Spring 配置类，覆盖持久层、安全、缓存等横切关注点。
 * 与 {@code ydsz-pmis-common} 中的通用自动装配协同工作，本包仅在 userinfo 模块存在差异
 * 化需求时进行覆写（{@code @ConditionalOnMissingBean} 兜底）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>MybatisPlusConfig - 重新装配 MyBatis-Plus 拦截器链（多租户 → 分页 → 防全表 → 乐观锁），
 *       对应 PostgreSQL 方言；主键生成器与审计字段填充器由 common 统一注册，本包不重复声明。</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>最小化覆盖：仅在 userinfo 模块存在差异化策略时新增 {@code @Configuration} 类。</li>
 *   <li>执行顺序：多租户拦截器必须位于拦截器链首位，以保障所有 SQL 自动追加 {@code tenant_id} 条件。</li>
 *   <li>安全优先：保留防全表更新/删除拦截器，禁止绕过审计字段填充器直接改库。</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增配置类请使用 {@code @Configuration(proxyBeanMethods = false)} 避免重复扫描开销。</li>
 *   <li>模块自带的配置应避免与 common 自动装配类同名同 Bean，防止启动期 Bean 冲突。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.userinfo.config;
