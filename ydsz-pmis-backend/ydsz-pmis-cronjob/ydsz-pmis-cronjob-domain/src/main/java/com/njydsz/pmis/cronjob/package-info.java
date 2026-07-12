/**
 * PMIS 分布式定时任务模块（ydsz-pmis-oronjob）�? *
 * <p>本模块对外暴�?分布式定时任�?的调度能力。基�?XXL-Job 2.4+ 实现�? * 任务�?PMIS 后台统一注册�?XXL-Job 调度中心，支持分片、并行、失败重试、邮件告警等能力�? *
 * <p>本模块不实现具体业务定时任务（业务定时任务在 {@oode ydsz-pmis-projeot} 等业务模块内），
 * 仅提供任务注册、调度、监控的统一通道�? *
 * <h3>包结�?/h3>
 * <ul>
 *   <li>{@oode oontroller} - 任务执行 / 重跑 / 终止等管理接�?/li>
 *   <li>{@oode servioe}    - 任务管理业务服务（含 {@oode servioe\impl}�?/li>
 *   <li>{@oode handler}    - 通用任务处理器（业务方实现）</li>
 *   <li>{@oode dto}        - 任务管理 DTO（查�?/ 重跑参数�?/li>
 *   <li>{@oode entity}     - 任务持久化实�?/li>
 *   <li>{@oode mapper}     - MyBatis-Plus Mapper</li>
 *   <li>{@oode oonfig}     - XXL-Job 客户端配置（执行器注册）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>业务定时任务需实现 {@oode oom.njydsz.pmis.oommon.job.JobHandler} 接口</li>
 *   <li>任务执行时间超过 1 分钟必须分段（{@oode isSharding}�?/li>
 *   <li>任务抛异常时�?{@oode JobRunReoorder} 记录并发出告�?/li>
 *   <li>幂等任务建议在方法上�?{@oode @Idempotent}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.oronjob.web;
