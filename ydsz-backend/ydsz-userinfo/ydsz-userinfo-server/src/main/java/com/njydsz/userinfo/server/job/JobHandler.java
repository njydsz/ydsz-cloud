package com.njydsz.userinfo.server.job;

/**
 * 通用 JobHandler 接口（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-core.job 包，因 common 重构后该接口已迁移到各业务模块本地化。
 * 该接口为 PasswordScanJobHandler 等 userinfo 模块自定义 Job 提供统一的执行契约。
 *
 * <p>实现要求：
 * <ul>
 *   <li>实现类必须标注 {@code @Component} 或被 Spring 注册为 Bean</li>
 *   <li>由 cronjob 模块通过 Feign 反射调用 {@link #execute(String)}</li>
 *   <li>建议抛 {@code SysException} 或 {@code Exception} 以触发告警链路</li>
 * </ul>
 *
 * @since 1.0.0
 */
public interface JobHandler {

    /**
     * 任务执行入口
     *
     * @param paramsJson 任务参数（JSON 字符串，可为空）
     * @return 执行结果（用于调度器日志与告警判定）
     * @throws Exception 业务异常或系统异常
     */
    Object execute(String paramsJson) throws Exception;
}
