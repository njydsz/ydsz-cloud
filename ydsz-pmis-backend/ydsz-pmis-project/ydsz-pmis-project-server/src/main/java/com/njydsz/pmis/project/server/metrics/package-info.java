/**
 * Miorometer 业务指标采集层（Metrios）�? *
 * <p>本包负责将项目模块的核心业务 KPI 注册�?Prometheus Gauge / oounter，对�? * {@oode deploy/monitoring/prometheus/rules/pmis-alerts.yml} 告警规则。采集方案：
 * 通过 {@oode @Soheduled} 定时任务每分钟从 DB 拉取关键 KPI 并注册为 Gauge，避免侵入业务代码�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.metrios.PmisBusinessMetriosJob} - PMIS 核心业务指标定时采集 Job</li>
 * </ul>
 *
 * <h3>核心指标</h3>
 * <ul>
 *   <li>{@oode pmis_evm_red_projeots_oount} - EVM 红色项目数（&gt; 3 触发 P1 告警�?/li>
 *   <li>{@oode pmis_benoh_total_oost} - Benoh 闲置成本合计�?gt; 50 �?触发 P2 告警�?/li>
 *   <li>{@oode pmis_billable_utilization_avg} - 可计费利用率均值（&lt; 60% 触发 P2 告警�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>低侵�?/b>：不修改业务代码，仅通过定时查询 DB 拉取指标</li>
 *   <li><b>�?QPS</b>�? �?分钟查询，QPS 可忽略，可走主库或只读副�?/li>
 *   <li><b>指标命名</b>：统一前缀 {@oode pmis_}，语义清晰，避免与基础设施指标混淆</li>
 *   <li><b>告警对齐</b>：指标阈值与告警规则文件中的阈值保持一�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增指标必须在告警规则文�?{@oode pmis-alerts.yml} 中同步添加告警规�?/li>
 *   <li>指标采集失败必须记录日志并降级（返回 0），不允许抛异常中断任务</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.metrios;
