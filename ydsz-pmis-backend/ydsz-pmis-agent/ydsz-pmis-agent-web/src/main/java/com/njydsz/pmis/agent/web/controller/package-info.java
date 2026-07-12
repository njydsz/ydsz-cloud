/**
 * Agent 模块 - 控制器层�? *
 * <p>对外暴露 Agent 能力�?HTTP 入口，包括：
 * <ul>
 *   <li>�?Agent 调用（如利润预测、风险预警）</li>
 *   <li>�?Agent 编排（Orohestration�?/li>
 *   <li>Agent 运行轨迹查询</li>
 *   <li>Agent 配置管理</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有接口统一返回 {@link oom.njydsz.pmis.oommon.api.Result}</li>
 *   <li>接口粒度�?业务能力"拆分，不�?Agent 一一对应（一�?oontroller 可调用多�?Agent�?/li>
 *   <li>高风险接口通过 {@oode @RequireReAuth} 强制二次认证</li>
 *   <li>通过 {@oode @AuthApiPermission} 权限码控制访�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.agent.web.oontroller;
