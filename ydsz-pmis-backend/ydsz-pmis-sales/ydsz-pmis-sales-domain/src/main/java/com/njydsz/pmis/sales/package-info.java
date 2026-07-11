/**
 * 商务销售服务（ydsz-pmis-sales）
 *
 * <p>DDD 分层架构，端口 9010，从原 ydsz-pmis-project 模块拆分而来。
 *
 * <h2>分层结构</h2>
 * <ul>
 *   <li>{@link com.njydsz.pmis.sales.domain} — 领域层：实体/DTO/枚举/VO/Converter/Query</li>
 *   <li>{@link com.njydsz.pmis.sales.infra} — 基础设施层：Mapper 接口 + MyBatis XML</li>
 *   <li>{@link com.njydsz.pmis.sales.server} — 应用服务层：Service + Engine + Exception</li>
 *   <li>{@link com.njydsz.pmis.sales.api} — API 契约层：Feign Client 接口 + Fallback</li>
 *   <li>{@link com.njydsz.pmis.sales.web} — Web 层：Controller + Config + 启动类</li>
 * </ul>
 *
 * <h2>业务域</h2>
 * <ul>
 *   <li>商机管理（Opportunity）：商机创建/跟进/状态流转/赢率评估</li>
 *   <li>合同管理（Contract）：合同创建/变更/补充协议/模板管理/风险评估</li>
 * </ul>
 *
 * <h2>跨域通信</h2>
 * <ul>
 *   <li>→ PM：通过 {@link com.njydsz.pmis.project.api.client.ProjectServiceClient} 调用项目执行服务</li>
 *   <li>→ Finance：通过 {@link com.njydsz.pmis.finance.api.client.FinanceDataClient} 调用财务会计服务</li>
 *   <li>→ UserInfo：通过 {@link com.njydsz.pmis.userinfo.api.client.UserServiceClient} 调用用户信息服务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.sales;
