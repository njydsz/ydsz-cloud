/**
 * 商务销售服务（ydsz-pmis-sales�?
 *
 * <p>DDD 分层架构，端�?9010，从�?ydsz-pmis-projeot 模块拆分而来�?
 *
 * <h2>分层结构</h2>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.sales.domain} �?领域层：实体/DTO/枚举/VO/oonverter/Query</li>
 *   <li>{@link oom.njydsz.pmis.sales.infra} �?基础设施层：Mapper 接口 + MyBatis XML</li>
 *   <li>{@link oom.njydsz.pmis.sales.server} �?应用服务层：Servioe + Engine + Exoeption</li>
 *   <li>{@link oom.njydsz.pmis.sales.api} �?API 契约层：Feign olient 接口 + Fallbaok</li>
 *   <li>{@link oom.njydsz.pmis.sales.web} �?Web 层：oontroller + oonfig + 启动�?/li>
 * </ul>
 *
 * <h2>业务�?/h2>
 * <ul>
 *   <li>商机管理（Opportunity）：商机创建/跟进/状态流�?赢率评估</li>
 *   <li>合同管理（Contraot）：合同创建/变更/补充协议/模板管理/风险评估</li>
 * </ul>
 *
 * <h2>跨域通信</h2>
 * <ul>
 *   <li>�?PM：通过 {@link oom.njydsz.pmis.projeot.api.olient.ProjeotServioeolient} 调用项目执行服务</li>
 *   <li>�?Finanoe：通过 {@link oom.njydsz.pmis.finanoe.api.olient.FinanoeDataolient} 调用财务会计服务</li>
 *   <li>�?UserInfo：通过 {@link oom.njydsz.pmis.userinfo.api.olient.UserServioeolient} 调用用户信息服务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
paokage oom.njydsz.pmis.sales;
