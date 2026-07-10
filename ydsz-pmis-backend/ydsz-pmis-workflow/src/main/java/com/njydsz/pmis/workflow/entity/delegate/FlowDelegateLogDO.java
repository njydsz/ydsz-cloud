package com.njydsz.pmis.workflow.entity.delegate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程委派代理使用日志 DO
 *
 * <p>P1-4: 审计追溯代理操作（谁在什么时间被代理处理了什么任务）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_delegate_log")
public class FlowDelegateLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 关联的授权 ID */
    private String authId;

    /** 流程实例 ID */
    private String instanceId;

    /** 任务 ID */
    private String taskId;

    /** 节点编码 */
    private String nodeCode;

    /** 授权人 ID */
    private String ownerUserId;

    /** 代理人 ID */
    private String delegateUserId;

    /** 操作类型：ACT=办理 / VIEW=查看 */
    private String opType;

    /** 办理动作：PASS/REJECT/CLAIM/TRANSFER */
    private String action;

    /** 办理意见 */
    private String comment;

    /** 链路追踪 ID */
    private String providerTraceId;
}
