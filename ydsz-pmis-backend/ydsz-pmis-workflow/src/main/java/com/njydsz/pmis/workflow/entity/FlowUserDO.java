package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 流程用户 DO
 *
 * <p>对标 Warm-Flow flow_user，会签多办理人扩展。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_user")
public class FlowUserDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 ID */
    private Long taskId;
    /** 流程实例 ID */
    private Long instanceId;
    /** 节点编码 */
    private String nodeCode;

    /** 用户类型：USER/ROLE/DEPT */
    private String userType;

    /** 用户/角色/部门 ID */
    private String userId;
    /** 姓名 */
    private String userName;

    /** 是否已处理：0 否 / 1 是 */
    private Integer processed;
    /** 处理时间 */
    private LocalDateTime processAt;
    /** 审批意见 */
    private String comment;

    /** P1-5: 办理人权重（默认 1，可配置 2/3 等，用于加权会签） */
    private Integer weight;

    /**
     * GAP-P0-3: 加签类型标识，区分原始审批人与动态加签人。
     * <p>GAP-P1-7: 取值对齐 {@link com.njydsz.pmis.workflow.enums.FlowSignType} 枚举，
     * 持久化使用 {@code FlowSignType.name()}。
     * <ul>
     *   <li>ORIGINAL：流程定义中配置的原始审批人</li>
     *   <li>BEFORE：前加签插入的审批人</li>
     *   <li>AFTER：后加签插入的审批人</li>
     *   <li>PARALLEL：并加签插入的审批人（与原审批人并行，所有人审完才推进）</li>
     *   <li>ADD：追加处理人</li>
     * </ul>
     * 默认 ORIGINAL，向后兼容存量数据。
     */
    private String signType;

    /** 租户 ID */
    private Long tenantId;
    /** 链路追踪 ID */
    private String providerTraceId;
}
