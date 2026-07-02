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
 * 流程委派代理（长期授权） DO
 *
 * <p>P1-4: 长期授权委派，区别于单任务委派（{@code FlowTaskServiceImpl.delegate}）。
 * <p>用户预先设置规则：在 [startTime, endTime] 区间内到达的匹配任务自动转给被代理人。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_delegate_auth")
public class FlowDelegateAuthDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 授权人（原办理人）ID */
    private Long ownerUserId;

    /** 授权人姓名 */
    private String ownerUserName;

    /** 被授权人（代理人）ID */
    private Long delegateUserId;

    /** 被授权人姓名 */
    private String delegateUserName;

    /** 匹配模式：ALL/FLOW/FLOW_NODE/ROLE */
    private String scopeType;

    /** 流程编码（FLOW/FLOW_NODE 模式必填） */
    private String flowCode;

    /** 节点编码（FLOW_NODE 模式必填） */
    private String nodeCode;

    /** 角色编码（ROLE 模式必填） */
    private String roleCode;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 状态：ENABLED/DISABLED/EXPIRED/REVOKED */
    private String authStatus;

    /** 授权原因 */
    private String reason;

    /** 链路追踪 ID */
    private String providerTraceId;
}
