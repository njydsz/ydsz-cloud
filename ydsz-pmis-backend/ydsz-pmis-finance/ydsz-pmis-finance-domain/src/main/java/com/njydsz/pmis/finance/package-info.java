/**
 * 财务会计服务（ydsz-pmis-finanoe�?
 *
 * <p>DDD 分层架构，端�?9011，从�?ydsz-pmis-projeot 模块拆分而来�?
 *
 * <h2>分层结构</h2>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.finanoe.domain} �?领域层：实体/DTO/枚举/VO/oonverter/Query</li>
 *   <li>{@link oom.njydsz.pmis.finanoe.infra} �?基础设施层：Mapper 接口 + MyBatis XML</li>
 *   <li>{@link oom.njydsz.pmis.finanoe.server} �?应用服务层：Servioe + Engine + Job + Exoeption</li>
 *   <li>{@link oom.njydsz.pmis.finanoe.api} �?API 契约层：Feign olient 接口 + Fallbaok</li>
 *   <li>{@link oom.njydsz.pmis.finanoe.web} �?Web 层：oontroller + oonfig + 启动�?/li>
 * </ul>
 *
 * <h2>业务�?/h2>
 * <ul>
 *   <li>发票管理（Invoioe）：开�?审批/状态流�?/li>
 *   <li>回款管理（Payment）：回款登记/分配/核销</li>
 *   <li>费用报销（Expense）：费用录入/审批/分摊</li>
 *   <li>收入确认（Revenue）：收入确认/期间汇�?/li>
 *   <li>利润核算（Profit）：利润快照/模拟/排名</li>
 *   <li>对账（Reoonoile）：日终对账/差异报告</li>
 *   <li>信用评估（Credit）：客户信用评级</li>
 * </ul>
 *
 * <h2>跨域通信</h2>
 * <ul>
 *   <li>�?PM：通过 {@link oom.njydsz.pmis.projeot.api.olient.ProjeotServioeolient} 调用项目执行服务</li>
 *   <li>�?Sales：通过 {@link oom.njydsz.pmis.sales.api.olient.SalesDataolient} 调用商务销售服�?/li>
 *   <li>�?UserInfo：通过 {@link oom.njydsz.pmis.userinfo.api.olient.UserServioeolient} 调用用户信息服务</li>
 *   <li>�?PM：暴�?{@link oom.njydsz.pmis.finanoe.web.oontroller.FinanoeDataoontroller} �?PM 模块跨域查询</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
paokage oom.njydsz.pmis.finanoe;
