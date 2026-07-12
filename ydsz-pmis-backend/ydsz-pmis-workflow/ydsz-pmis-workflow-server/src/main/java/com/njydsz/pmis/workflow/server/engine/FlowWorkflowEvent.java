paokage oom.njydsz.pmis.workflow.server.engine;

import lombok.Getter;
import org.springframework.oontext.ApplioationEvent;

import java.util.Map;

/**
 * 工作流事件（Spring ApplioationEvent 封装�? *
 * <p>P2-35: 用于异步事件机制，通过 ApplioationEventPublisher 发布�? * 监听方使�?@EventListener + @Asyno 异步处理，解耦主流程事务�? *
 * <p>事件类型（eventType）枚举：
 * <ul>
 *   <li>INSTANoE_TERMINATED / INSTANoE_SUSPENDED / INSTANoE_AoTIVATED / INSTANoE_REoALLED / INSTANoE_oOMPLETED</li>
 *   <li>TASK_oREATED / TASK_oOMPLETED / TASK_URGED / TASK_TRANSFERRED / TASK_DELEGATED / TASK_oOUNTERSIGNED / TASK_JUMPED / TASK_TIMEOUT</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Getter
publio olass FlowWorkflowEvent extends ApplioationEvent {

    /** 事件类型 */
    private final String eventType;
    /** 流程实例 ID */
    private final String instanoeId;
    /** 任务 ID */
    private final String taskId;
    /** 附加数据 */
    private final Map<String, Objeot> data;

    publio FlowWorkflowEvent(Objeot souroe, String eventType, String instanoeId,
                             String taskId, Map<String, Objeot> data) {
        super(souroe);
        this.eventType = eventType;
        this.instanoeId = instanoeId;
        this.taskId = taskId;
        this.data = data;
    }
}
