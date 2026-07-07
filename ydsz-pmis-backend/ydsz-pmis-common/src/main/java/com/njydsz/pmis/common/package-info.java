/**
 * PMIS 通用基础模块（ydsz-pmis-common）。
 *
 * <p>本模块是 PMIS 平台的公共底座，被所有业务模块（system / userinfo / project / workflow / agent / cronjob）依赖。
 * 模块不包含业务实现，仅提供：
 *
 * <h3>1. 通用能力</h3>
 * <ul>
 *   <li>{@code api}        - 统一响应封装 {@link com.njydsz.pmis.common.api.Result}、业务错误码枚举
 *                            {@link com.njydsz.pmis.common.api.BizErrorCode}、分页结果
 *                            {@link com.njydsz.pmis.common.api.PageResult}</li>
 *   <li>{@code exception}  - 全局异常处理
 *                            {@link com.njydsz.pmis.common.exception.GlobalExceptionHandler}
 *                            与业务异常基类 {@link com.njydsz.pmis.common.exception.BizException}</li>
 *   <li>{@code constant}   - 跨模块共享的常量（系统、缓存、MQ Topic、线程池等）</li>
 *   <li>{@code util}       - 通用工具类（雪花 ID、链路追踪、排序、密码学、PDF、JSON 等）</li>
 * </ul>
 *
 * <h3>2. 横切关注点</h3>
 * <ul>
 *   <li>{@code aspect}     - AOP 切面（权限、限流、幂等、分布式锁、数据范围、操作日志、接口指标）</li>
 *   <li>{@code annotation} - 自定义注解（幂等、限流、权限、数据范围、分布式锁、操作日志、API 指标等）</li>
 *   <li>{@code filter}     - Web 过滤器（XSS、链路追踪、CSRF Cookie、严格 Content-Type）</li>
 *   <li>{@code interceptor}- Web 拦截器（鉴权透传）</li>
 * </ul>
 *
 * <h3>3. 安全与合规</h3>
 * <ul>
 *   <li>{@code security}   - 登录上下文、安全策略、密码策略、TOTP、CSRF、敏感操作事件等</li>
 *   <li>{@code sensitive}  - 7 种敏感数据脱敏策略、加密字段序列化器</li>
 *   <li>{@code permission} - 权限码定义与校验器</li>
 *   <li>{@code kms}        - 密钥管理（Jasypt / 环境变量 / 自定义 SPI）</li>
 *   <li>{@code log}        - 操作日志 MDC 上下文</li>
 *   <li>{@code event}      - 通用领域事件（操作日志、项目变更执行等）</li>
 * </ul>
 *
 * <h3>4. 基础设施集成</h3>
 * <ul>
 *   <li>{@code config}     - Spring / MyBatis-Plus / Sentinel / Seata / Resilience4j / 缓存 / 异步线程池等
 *                            自动配置</li>
 *   <li>{@code datasource} - 动态数据源常量（读写分离）</li>
 *   <li>{@code feign}      - 跨服务 Feign 客户端（统一拦截器、日志、降级工厂）</li>
 *   <li>{@code sentry}     - 异常监控 Sentry 集成</li>
 *   <li>{@code tracing}    - 链路追踪 Brave 桥接</li>
 *   <li>{@code tx}         - 分布式事务后置处理器</li>
 * </ul>
 *
 * <h3>5. 平台能力</h3>
 * <ul>
 *   <li>{@code excel}      - Excel 导入导出工具</li>
 *   <li>{@code featureflag}- 特性开关（动态配置）</li>
 *   <li>{@code chaos}      - 混沌工程（注入故障用于韧性演练）</li>
 *   <li>{@code health}     - 自定义健康检查（Redis / DB）</li>
 *   <li>{@code job}        - XXL-Job 任务执行抽象</li>
 *   <li>{@code reconcile}  - 对账引擎（订单 / 流水 / 第三方等）</li>
 *   <li>{@code service}    - 布隆过滤器等公共服务</li>
 *   <li>{@code migration}  - 加密字段迁移 CLI</li>
 *   <li>{@code token}      - JWT 令牌工具</li>
 * </ul>
 *
 * <h3>依赖方向</h3>
 * <p>本模块作为底座，禁止依赖任何业务模块（system / userinfo / project / workflow / agent）。
 * 业务模块依赖本模块时通过 Maven 引入 {@code ydsz-pmis-common} 即可。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common;
