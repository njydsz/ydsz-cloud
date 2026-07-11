/**
 * 业务服务层：定义 PMIS 系统管理模块的核心业务接口与顶层服务实现。
 *
 * <p>本包是 Controller 与持久层之间的"业务编排层"，承担事务控制、跨实体协作、
 * 第三方调用编排、领域规则校验等核心职责。遵循"接口与实现分离"原则：
 * 业务接口集中在本包，具体实现下沉至 {@code service.impl} 子包（少量历史兼容类
 * 保留在本包，标注 {@code @Service}）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code MessageService} - 消息发送核心服务接口，定义 {@code send/sendDirect/pageLog/loadTemplate} 等方法</li>
 *   <li>{@code MessageServiceImpl} - 消息服务实现（含模板渲染、通道路由、日志落库），
>       供其他模块 {@code @Autowired} 直接调用（已合并原 Feign 调用）</li>
 *   <li>{@code MessageTemplateServiceImpl} - 消息模板管理实现（CRUD + 租户隔离）</li>
 *   <li>{@code NotificationService} - 站内通知服务接口（发送/收件箱/已读/删除）</li>
 *   <li>{@code ConfigService} - 系统动态配置服务接口</li>
 *   <li>{@code FileService} / {@code FileEnhanceService} - 文件管理服务接口
 *       （基础 + 秒传/分片/断点续传增强能力）</li>
 *   <li>{@code OperationLogServiceImpl} - 操作日志服务实现（异步落库 + 审计查询）</li>
 *   <li>{@code RealtimePushService} - 实时推送服务（基于 WebSocket STOMP，
>       将通知/邮件结果实时推送到用户浏览器）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口与实现分离</b>：业务接口定义在本包，实现统一在 {@code service.impl} 子包</li>
 *   <li><b>事务边界清晰</b>：{@code @Transactional(rollbackFor = Exception.class)} 标注在写操作上，
>       读操作使用 {@code readOnly = true} 优化</li>
 *   <li><b>幂等可控</b>：对外暴露的写操作须保证幂等性，幂等键通过入参或业务字段构造</li>
 *   <li><b>业务异常显式</b>：所有业务校验失败须抛 {@code BizException(BizErrorCode, i18nKey)}，
>       禁止 {@code RuntimeException} 模糊处理</li>
 *   <li><b>无状态可水平扩展</b>：所有 Service 均为无状态 Bean（仅依赖 Mapper/其他 Service），
>       支持多实例部署</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增业务接口须在本 {@code package-info.java} 中登记</li>
 *   <li>接口方法命名使用业务动词（{@code send/mark/load/inbox}），避免 CRUD 式命名</li>
 *   <li>Service 方法粒度适中，单方法不超过 50 行；超过则拆分私有方法</li>
 *   <li>跨服务调用通过 {@code feign} 包暴露的 Client 注入，禁止直接写 HTTP</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.server.service;
