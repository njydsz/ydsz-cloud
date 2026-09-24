/**
 * ydzs-common-seata — Seata 分布式事务能力封装。
 *
 * <p>提供（见编码规范第 25 章 / shared-rules YDIZ-COMMON-003）：
 * <ul>
 *   <li>{@link com.njydsz.common.seata.annotation.YdszGlobalTransactional} —
 *       对标 {@code @GlobalTransaction}，替代 Seata 原生注解；</li>
 *   <li>{@link com.njydsz.common.seata.config.SeataAutoConfiguration} —
 *       ydsz.seata.enabled=true 时注册全局事务切面 + XID 拦截器 / 过滤器 /
 *       健康检查；</li>
 *   <li>Feign 发出端 {@link com.njydsz.common.seata.interceptor.FeignXidRequestInterceptor} 与
 *       Web 接收端 {@link com.njydsz.common.seata.filter.XidServletFilter} —
 *       自动透传 XID（Header 名默认 TX_XID）；</li>
 *   <li>{@link com.njydsz.common.seata.health.SeataHealthIndicator} —
 *       Actuator /health 状态暴露。</li>
 * </ul>
 *
 * <p>业务代码不得直接 {@code import org.apache.seata.*}，所有 Seata 能力经
 * {@code com.njydsz.common.seata.*} 封装；XID 透传采用反射调用 Seata API，
 * 本模块不引入 Seata 编译期硬依赖。
 *
 * @author ydsz-team
 * @since ACC-1
 */
package com.njydsz.common.seata;
