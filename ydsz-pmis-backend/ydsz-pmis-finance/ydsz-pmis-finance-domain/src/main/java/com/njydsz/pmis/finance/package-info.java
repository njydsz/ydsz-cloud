/**
 * 财务会计服务（ydsz-pmis-finance）
 *
 * <p>DDD 分层架构，端口 9011，从原 ydsz-pmis-project 模块拆分而来。
 *
 * <h2>分层结构</h2>
 * <ul>
 *   <li>{@link com.njydsz.pmis.finance.domain} — 领域层：实体/DTO/枚举/VO/Converter/Query</li>
 *   <li>{@link com.njydsz.pmis.finance.infra} — 基础设施层：Mapper 接口 + MyBatis XML</li>
 *   <li>{@link com.njydsz.pmis.finance.server} — 应用服务层：Service + Engine + Job + Exception</li>
 *   <li>{@link com.njydsz.pmis.finance.api} — API 契约层：Feign Client 接口 + Fallback</li>
 *   <li>{@link com.njydsz.pmis.finance.web} — Web 层：Controller + Config + 启动类</li>
 * </ul>
 *
 * <h2>业务域</h2>
 * <ul>
 *   <li>发票管理（Invoice）：开票/审批/状态流转</li>
 *   <li>回款管理（Payment）：回款登记/分配/核销</li>
 *   <li>费用报销（Expense）：费用录入/审批/分摊</li>
 *   <li>收入确认（Revenue）：收入确认/期间汇总</li>
 *   <li>利润核算（Profit）：利润快照/模拟/排名</li>
 *   <li>对账（Reconcile）：日终对账/差异报告</li>
 *   <li>信用评估（Credit）：客户信用评级</li>
 * </ul>
 *
 * <h2>跨域通信</h2>
 * <ul>
 *   <li>→ PM：通过 {@link com.njydsz.pmis.project.api.client.ProjectServiceClient} 调用项目执行服务</li>
 *   <li>→ Sales：通过 {@link com.njydsz.pmis.sales.api.client.SalesDataClient} 调用商务销售服务</li>
 *   <li>→ UserInfo：通过 {@link com.njydsz.pmis.userinfo.api.client.UserServiceClient} 调用用户信息服务</li>
 *   <li>← PM：暴露 {@link com.njydsz.pmis.finance.web.controller.FinanceDataController} 供 PM 模块跨域查询</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.finance;
