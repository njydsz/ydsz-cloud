package com.njydsz.pmis.agent.entity.orchestration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * DAG 执行实例实体（P3-2 落地）。
 *
 * <p>记录一次 DAG 执行的整体状态与汇总信息。
 * 节点级明细存储在 {@link DagNodeInstanceDO}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_dag_instance")
public class DagInstanceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** DAG 定义 ID */
    private String dagDefinitionId;

    /** DAG 名称（冗余，便于查询） */
    private String dagName;

    /** 业务类型 */
    private String bizType;

    /** 业务引用 */
    private String bizRef;

    /** 实例状态：CREATED / RUNNING / SUCCESS / FAILED / CANCELLED / TIMEOUT */
    private String status;

    /** 全局输入参数 JSON */
    private String globalInputsJson;

    /** 节点输出汇总 JSON */
    private String nodeOutputsJson;

    /** 总耗时（毫秒） */
    private Long totalCostMs;

    /** 成功节点数 */
    private Integer successCount;

    /** 失败节点数 */
    private Integer failedCount;

    /** 跳过节点数 */
    private Integer skippedCount;

    /** 总节点数 */
    private Integer totalNodes;

    /** 备注（如中止原因） */
    private String note;
}
