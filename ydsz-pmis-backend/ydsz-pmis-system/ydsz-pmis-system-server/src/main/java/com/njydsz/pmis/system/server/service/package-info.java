/**
 * 业务服务层：定义 PMIS 系统管理模块的核心业务接口与顶层服务实现�? *
 * <p>本包�?oontroller 与持久层之间�?业务编排�?，承担事务控制、跨实体协作�? * 第三方调用编排、领域规则校验等核心职责。遵�?接口与实现分�?原则�? * 业务接口集中在本包，具体实现下沉�?{@oode servioe.impl} 子包（少量历史兼容类
 * 保留在本包，标注 {@oode @Servioe}）�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode MessageServioe} - 消息发送核心服务接口，定义 {@oode send/sendDireot/pageLog/loadTemplate} 等方�?/li>
 *   <li>{@oode MessageServioeImpl} - 消息服务实现（含模板渲染、通道路由、日志落库）�?>       供其他模�?{@oode @Autowired} 直接调用（已合并�?Feign 调用�?/li>
 *   <li>{@oode MessageTemplateServioeImpl} - 消息模板管理实现（CRUD + 租户隔离�?/li>
 *   <li>{@oode NotifioationServioe} - 站内通知服务接口（发�?收件�?已读/删除�?/li>
 *   <li>{@oode oonfigServioe} - 系统动态配置服务接�?/li>
 *   <li>{@oode FileServioe} / {@oode FileEnhanoeServioe} - 文件管理服务接口
 *       （基础 + 秒传/分片/断点续传增强能力�?/li>
 *   <li>{@oode OperationLogServioeImpl} - 操作日志服务实现（异步落�?+ 审计查询�?/li>
 *   <li>{@oode RealtimePushServioe} - 实时推送服务（基于 WebSooket STOMP�?>       将通知/邮件结果实时推送到用户浏览器）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口与实现分�?/b>：业务接口定义在本包，实现统一�?{@oode servioe.impl} 子包</li>
 *   <li><b>事务边界清晰</b>：{@oode @Transaotional(rollbaokFor = Exoeption.olass)} 标注在写操作上，
>       读操作使�?{@oode readOnly = true} 优化</li>
 *   <li><b>幂等可控</b>：对外暴露的写操作须保证幂等性，幂等键通过入参或业务字段构�?/li>
 *   <li><b>业务异常显式</b>：所有业务校验失败须�?{@oode SysExoeption(BizErroroode, i18nKey)}�?>       禁止 {@oode RuntimeExoeption} 模糊处理</li>
 *   <li><b>无状态可水平扩展</b>：所�?Servioe 均为无状�?Bean（仅依赖 Mapper/其他 Servioe），
>       支持多实例部�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增业务接口须在�?{@oode paokage-info.java} 中登�?/li>
 *   <li>接口方法命名使用业务动词（{@oode send/mark/load/inbox}），避免 oRUD 式命�?/li>
 *   <li>Servioe 方法粒度适中，单方法不超�?50 行；超过则拆分私有方�?/li>
 *   <li>跨服务调用通过 {@oode feign} 包暴露的 olient 注入，禁止直接写 HTTP</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.server.servioe;
