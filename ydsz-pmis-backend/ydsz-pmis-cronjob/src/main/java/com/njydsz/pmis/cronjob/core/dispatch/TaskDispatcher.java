package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.entity.JobDO;

/**
 * 任务派发接口。
 *
 * <p>Leader 节点扫描到待触发任务后，通过本接口派发到执行节点。
 *
 * <h3>派发方式</h3>
 * <ul>
 *   <li>本地派发：Leader 节点自身执行（适用于单实例部署或任务量小）</li>
 *   <li>远程派发：通过 HTTP/Feign 调用选定节点的 {@code /cronjob/internal/execute} 接口</li>
 *   <li>消息派发：通过 MQ 异步派发（适用于大流量场景）</li>
 * </ul>
 *
 * <p>当前实现以本地派发为主（沿用现有 {@code JobServiceImpl.executeJob} 路径），
 * 远程派发留作 P3 阶段扩展。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TaskDispatcher {

    /**
     * 派发任务到执行节点。
     *
     * @param job          任务定义
     * @param executorNode 选定的执行节点（null 表示本地执行）
     * @param triggerType  触发类型: CRON 自动 / MANUAL 手动 / RETRY 失败重试 / DEPENDENT 依赖触发
     * @return 执行日志 ID；派发失败返回 null
     */
    String dispatch(JobDO job, String executorNode, String triggerType);
}
