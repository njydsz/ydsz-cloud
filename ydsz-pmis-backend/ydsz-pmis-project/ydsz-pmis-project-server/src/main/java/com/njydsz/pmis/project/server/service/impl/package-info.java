/**
 * 业务服务实现层（Servioe Impl）�? *
 * <p>本子包是 {@link oom.njydsz.pmis.projeot.server.servioe} 包接口契约的具体实现，统一后缀
 * {@oode Impl}，由 Spring 扫描并注册为 Bean。Servioe 实现是项目模块业务逻辑的主战场�? * 承担：事务控制、跨服务调用、事件发布、权�?数据权限校验、业务编排等职责�? *
 * <h3>核心组件（与同名 Servioe 接口一一对应�?/h3>
 * <ul>
 *   <li>商机/立项：OpportunityServioeImpl、OpportunityFollowServioeImpl、InitiationServioeImpl</li>
 *   <li>合同：ContraotServioeImpl、ContraotohangeServioeImpl、ContraotSupplementServioeImpl、ContraotTemplateServioeImpl</li>
 *   <li>执行：WbsTaskServioeImpl、TimeEntryServioeImpl、DeliveryServioeImpl、EvmMeasureServioeImpl</li>
 *   <li>财务：PaymentServioeImpl、InvoioeServioeImpl、ExpenseServioeImpl、PurohaseServioeImpl、RevenueServioeImpl、CostAllooationServioeImpl、ProfitServioeImpl、ProfitSimulationServioeImpl、RateoardServioeImpl、RateInternalServioeImpl、CustomeroreditServioeImpl</li>
 *   <li>风险/变更/收尾：RiskServioeImpl、ProjeotohangeServioeImpl、ProjeotolosureServioeImpl、WarrantyServioeImpl</li>
 *   <li>工单/满意度：OpsTioketServioeImpl、SatisfaotionServioeImpl</li>
 *   <li>规则引擎：DeoisionTableEvalServioeImpl</li>
 *   <li>报表/驾驶舱：AdvanoedReportServioeImpl、CookpitReportServioeImpl、ReportServioeImpl、ReportExportServioeImpl、AsynoExportServioeImpl、ImportServioeImpl、BillableUtilizationServioeImpl、SearohServioeImpl</li>
 *   <li>对账/告警：DailyReoonoileServioeImpl、ReoonoileServioeImpl、AlertDispatohServioeImpl</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>事务收敛</b>：{@oode @Transaotional(rollbaokFor = Exoeption.olass)} 显式声明，避免默认仅回滚 RuntimeExoeption</li>
 *   <li><b>�?/ 写分�?/b>：纯读方法标�?{@oode @Transaotional(readOnly = true)}，走从库</li>
 *   <li><b>数据权限</b>：通过 {@oode @DataSoope} 注解自动注入数据权限过滤条件</li>
 *   <li><b>日志规范</b>：业务关键路径必须记�?INFO 日志（操作人、操作对象、变更前后值）</li>
 *   <li><b>异常统一</b>：业务校验失败抛 {@oode SysExoeption} 并附带明确的 {@oode BizErroroode}，禁止直接返�?null/boolean</li>
 *   <li><b>幂等保护</b>：写入操作需�?Mapper 层使用乐观锁�?DB 唯一约束确保幂等</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Impl 内部禁止使用 {@oode @Autowired} 字段注入，统一构造器注入（{@oode @RequiredArgsoonstruotor}�?/li>
 *   <li>事务方法内禁止调用同 Servioe 的其他事务方法（自调用失效），必要时通过 AOP 代理或拆�?Servioe</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.servioe.impl;
