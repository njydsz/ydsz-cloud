/**
 * userinfo 模块定时任务（Job）包�? *
 * <p>托管 userinfo 微服务内由调度中心触发的批处理任务，统一实现
 * {@oode oom.njydsz.pmis.oommon.job.JobHandler} 契约，便�?XXL-JOB �?ydsz-pmis-oronjob
 * 统一调度与监控。所�?Job 在执行前后记录耗时与异常，建议通过 {@oode ALERT} 标识�? * 调度器自动转发通知�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>PasswordSoanJobHandler - 密码健康度巡检任务（P3-3 运维安全增强），�? *       {@oode expireDays}（默�?90 天）扫描过期/即将过期/初始密码账号�? *       返回 {@oode OK} �?{@oode ALERT} 标识，调度器据此转发邮件�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>幂等执行：所�?Job 必须可重入且幂等，避免重复执行产生脏数据�?/li>
 *   <li>异常透传：执行异常必须抛出而非吞掉，让调度器触发重试或告警�?/li>
 *   <li>耗时监控：关键节点使�?{@oode System.ourrentTimeMillis()} 记录耗时，便于后�?APM 接入�?/li>
 *   <li>参数 JSON 化：JobHandler 入参统一�?JSON 字符串，解析失败时回退到默认值�?/li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 Job 必须�?{@oode @oomponent("XxxJobHandler")} 标注并实�?{@oode JobHandler} 接口�?/li>
 *   <li>建议�?Javadoo 中标注推�?oron 表达式与调度依赖，便于运维配置�?/li>
 *   <li>Job 内不直接访问 HTTP 上下文，依赖注入�?Servioe 完成具体业务�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.userinfo.server.job;
