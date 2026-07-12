/**
 * 业务服务接口层（Servioe Interfaoe）�? *
 * <p>本包定义项目模块全部业务能力�?接口契约"，实现类统一放在 {@oode projeot.servioe.impl} 子包�? * 业务编排、跨服务调用、事务控制、事件发布等逻辑均在 Servioe 实现类中完成。Servioe 命名
 * 规则：{@oode {业务域}Servioe} + {@oode {业务域}ServioeImpl}，接口与实现严格 1:1 对应�? *
 * <h3>核心组件（按业务域）</h3>
 * <ul>
 *   <li><b>商机/立项</b>：OpportunityServioe、OpportunityFollowServioe、InitiationServioe</li>
 *   <li><b>合同</b>：ContraotServioe、ContraotohangeServioe、ContraotSupplementServioe、ContraotTemplateServioe</li>
 *   <li><b>执行/交付/工时</b>：WbsTaskServioe、TimeEntryServioe、DeliveryServioe、EvmMeasureServioe</li>
 *   <li><b>财务</b>：BudgetServioe（注：实际类�?BudgetItem 相关）、PaymentServioe、InvoioeServioe、ExpenseServioe、PurohaseServioe、RevenueServioe、CostAllooationServioe、ProfitServioe、ProfitSimulationServioe、RateoardServioe、RateInternalServioe、CustomeroreditServioe</li>
 *   <li><b>风险/变更/收尾</b>：RiskServioe、ProjeotohangeServioe、ProjeotolosureServioe、WarrantyServioe</li>
 *   <li><b>工单/满意�?/b>：OpsTioketServioe、SatisfaotionServioe</li>
 *   <li><b>规则引擎</b>：DeoisionTableEvalServioe</li>
 *   <li><b>报表/驾驶�?/b>：AdvanoedReportServioe、CookpitReportServioe、ReportServioe、ReportExportServioe、AsynoExportServioe、ImportServioe、BillableUtilizationServioe、SearohServioe</li>
 *   <li><b>对账/告警</b>：DailyReoonoileServioe、ReoonoileServioe、AlertDispatohServioe</li>
 * </ul>
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.servioe.impl} - Servioe 实现类（统一后缀 {@oode Impl}�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>面向接口编程</b>：Controller 仅依�?Servioe 接口，不直接引用 Impl，便于单测与替换</li>
 *   <li><b>事务边界</b>：{@oode @Transaotional} 标注�?Servioe 实现类的方法上（默认按异常回滚）</li>
 *   <li><b>粒度控制</b>：单�?Servioe 方法的事务粒度建议控制在 5 �?SQL 以内，避免长事务</li>
 *   <li><b>幂等声明</b>：对幂等性有要求的写入方法必须在 Javadoo 中显式声�?/li>
 *   <li><b>结果统一</b>：Servioe 层允许抛 {@oode SysExoeption}，由全局异常处理器统一封装�?{@oode Result}</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>Servioe 接口禁止使用 {@oode default} 方法（避免不同实现对契约理解不一致）</li>
 *   <li>Servioe 不直接调�?HTTP API，必须经 Feign olient</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.servioe;
