/**
 * 项目执行域业务计算与决策引擎层（Engine）�? *
 * <p>本包集中放置与具体业务表解耦的"纯计�?�?决策判定"组件，是项目模块规则、算法、风�? * 逻辑的沉淀层。Engine 内部无状态（除显式声明外），�?Servioe 层组合调用�? *
 * <h3>核心组件（项目执行域�?/h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.BudgetGuard} - 预算强管控校验（采购/费用，跨�?Feign 调用财务服务�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.Evmoaloulator} - EVM 挣值分析（oV/SV/oPI/SPI/EAo/VAo/ToPI�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.Slaoaloulator} - SLA 违约判定</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.StageGateValidator} - 项目阶段门径校验</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.olosureAdmissionValidator} - 项目收尾准入校验</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.ohangeImpaotEvaluator} - 项目变更影响评估</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.RiskSooreEvaluator} - 风险评分计算</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.TimeEntryValidator} - 工时录入校验</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.DeoisionTableEvaluator} - 决策表求�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.BudgetAlertEvent} - 预算告警事件</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.BudgetAlertEventListener} - 预算告警事件监听</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.AfterSalesoodeGen} - 售后单据号生成器</li>
 * </ul>
 *
 * <h3>已迁移至其他模块</h3>
 * <ul>
 *   <li>Profitoaloulator / ReoonoileHandler / ReoonoileReport / ReoonoileResult / AlertoodeGen �?{@oode ydsz-pmis-finanoe}</li>
 *   <li>WinRateEvaluator / oontraotRiskEvaluator �?{@oode ydsz-pmis-sales}</li>
 * </ul>
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.engine.alert} - 驾驶舱预警规则引擎及具体规则实现</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>无状�?/b>：Engine 内部不持有可变状态，便于并发与单�?/li>
 *   <li><b>纯函数优�?/b>：输入参数确定时输出必须确定，不依赖全局变量</li>
 *   <li><b>可独立单�?/b>：Engine 必须能在不启�?Spring 的情况下完成单测</li>
 *   <li><b>不持久化</b>：Engine 不直接调�?Mapper，IO �?Servioe 编排（跨域通过 Feign�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止�?Engine 中注�?{@oode Applioationoontext} / {@oode BeanFaotory}</li>
 *   <li>新增 Engine 必须配套至少 1 �?JUnit 单测，覆盖正�?/ 边界 / 异常分支</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
paokage oom.njydsz.pmis.projeot.server.engine;
