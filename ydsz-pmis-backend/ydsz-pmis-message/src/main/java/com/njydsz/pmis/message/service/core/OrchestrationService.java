package com.njydsz.pmis.message.service.core;

import com.njydsz.pmis.message.dto.core.OrchestrationFlowDTO;
import com.njydsz.pmis.message.dto.core.OrchestrationResultVO;

/**
 * 消息编排引擎服务。
 *
 * <p>P1-9: 支持 DAG（有向无环图）流程编排，按拓扑序执行各节点：
 * <ul>
 *   <li>依赖节点全部成功后才执行当前节点</li>
 *   <li>支持 SpEL 条件表达式（如 {@code #{prev.status == 'SUCCESS'}}）</li>
 *   <li>节点失败策略：CONTINUE / ABORT / RETRY</li>
 *   <li>流程级超时控制</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
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
