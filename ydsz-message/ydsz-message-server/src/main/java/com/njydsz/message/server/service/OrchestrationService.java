package com.njydsz.message.server.service.core;

import com.njydsz.message.domain.dto.core.OrchestrationFlowDTO;
import com.njydsz.message.domain.dto.core.OrchestrationResultVO;

/**
 * 消息编排引擎 Service
 *
 * <p>支持 DAG(有向无环图)流程编排,按拓扑序执行各节点。常用于"先发短信通知,然后发邮件详情,
 * 等待 5 分钟未读再发推送催办"等多步骤消息场景。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>依赖节点全部成功后才执行当前节点</li>
 *   <li>支持 SpEL 条件表达式(如 {@code #{prev.status == 'SUCCESS'}})</li>
 *   <li>节点失败策略：{@code CONTINUE} / {@code ABORT} / {@code RETRY}</li>
 *   <li>流程级超时控制</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>执行编排</b>：{@link #execute} — 入口方法,接收 DAG 流程定义返回执行结果</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.message.domain.dto.core.OrchestrationFlowDTO 编排流程定义
 * @see com.njydsz.message.domain.dto.core.OrchestrationResultVO 执行结果
 */
public interface OrchestrationService {

    /**
     * 执行编排流程。
     *
     * @param flow 流程定义
     * @return 执行结果
     */
    OrchestrationResultVO execute(OrchestrationFlowDTO flow);
}
