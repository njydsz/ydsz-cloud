package com.njydsz.pmis.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * DAG 节点执行实例实体（P3-2 落地）。
 *
 * <p>记录每个节点在一次 DAG 执行中的状态与输出。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_agent_dag_node_instance")
public class DagNodeInstanceDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** DAG 实例 ID */
    private String dagInstanceId;

    /** 节点名 */
    private String nodeName;

    /** 关联的 Agent 类型 */
    private String agentType;

    /** 节点状态：PENDING / RUNNING / SUCCESS / FAILED / SKIPPED */
    private String status;

    /** 节点输出 JSON */
    private String outputJson;

    /** 错误消息 */
    private String errorMessage;

    /** 已重试次数 */
    private Integer retryCount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
