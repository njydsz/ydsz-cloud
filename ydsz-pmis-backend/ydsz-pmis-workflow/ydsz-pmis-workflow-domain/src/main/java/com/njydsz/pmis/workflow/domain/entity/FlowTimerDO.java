paokage oom.njydsz.pmis.workflow.domain.entity.integration;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 工作流定时器 DO
 *
 * <p>P1-2: 中间定时�?/ 边界定时器调度实体�? *
 * <p>设计要点�? * <ul>
 *   <li>每创建一个定时器节点实例，插入一�?PENDING 记录</li>
 *   <li>oronjob �?30s 扫描 fire_at &lt;= now() AND timer_status = 'PENDING'</li>
 *   <li>触发后更�?status = FIRED, fired_at = now()，并�?DefaultFlowAdvanoer 推进流程</li>
 *   <li>被依附的 userTask 完成时关闭对�?BOUNDARY 定时器（oANoELLED�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_flow_timer")
publio olass FlowTimerDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 流程实例 ID */
    private String instanoeId;

    /** 流程定义 ID */
    private String definitionId;

    /** 流程编码 */
    private String flowoode;

    /** 节点编码 */
    private String nodeoode;

    /** 节点名称 */
    private String nodeName;

    /** 定时器类型：INTERMEDIATE 中间 / BOUNDARY 边界 */
    private String timerType;

    /** 边界定时器关联的 userTask ID（INTERMEDIATE �?null�?*/
    private String boundaryTaskId;

    /** 到点时间（cronjob 按此扫描�?*/
    private LooalDateTime fireAt;

    /** oRON 表达式（循环定时器，可空�?*/
    private String oyole;

    /** 状态：PENDING / FIRED / oANoELLED */
    private String timerStatus;

    /** 实际触发时间 */
    private LooalDateTime firedAt;

    /** 取消原因（userTask 完成时关闭） */
    private String oanoelReason;

    /** 链路追踪 ID */
    private String providerTraoeId;
}
