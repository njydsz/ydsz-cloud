package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程委派代理（长期授权）实体
 *
 * <p>对应数据库表 {@code ydsz_flow_delegate_auth}，P1-4: 长期授权委派，
 * 区别于单任务委派（{@code FlowTaskServiceImpl.delegate}）。
 * 用户预先设置规则：在 {@code [startTime, endTime]} 区间内到达的匹配任务自动转给被代理人。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>请假/出差期间，将待办自动转给指定代理人</li>
 *   <li>部门负责人调整时，将原负责人的流程待办转给继任者</li>
 *   <li>特殊审批人（如法律顾问）需要长期接管某类流程</li>
 * </ul>
 *
 * <p><b>匹配模式（{@code scopeType}）：</b>
 * <ul>
 *   <li>{@code ALL}：所有流程所有节点</li>
 *   <li>{@code FLOW}：指定流程（{@code flowCode} 必填）</li>
 *   <li>{@code FLOW_NODE}：指定流程的指定节点（{@code flowCode} + {@code nodeCode} 必填）</li>
 *   <li>{@code ROLE}：指定角色产生的待办（{@code roleCode} 必填）</li>
 * </ul>
 *
 * <p><b>授权状态（{@code authStatus}）：</b>
 * <ul>
 *   <li>{@code ENABLED}：生效中</li>
 *   <li>{@code DISABLED}：手动停用</li>
 *   <li>{@code EXPIRED}：已过期（{@code endTime < now()}，由调度器自动标记）</li>
 *   <li>{@code REVOKED}：已撤销（用户手动撤回）</li>
 * </ul>
 *
 * <p><b>调度处理：</b>由 {@code FlowDelegateScheduler} 每分钟扫描，
 * 对匹配的新待办自动调用 {@code delegate(taskId, delegateUserId)} 实现转交。
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>普通索引 {@code idx_owner}（{@code owner_user_id}）：查询我的授权</li>
 *   <li>普通索引 {@code idx_delegate}（{@code delegate_user_id}）：查询代理给我的待办</li>
 *   <li>普通索引 {@code idx_status_time}（{@code auth_status}, {@code end_time}）：过期扫描</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.workflow.server.scheduler.FlowDelegateScheduler 委派扫描调度器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_delegate_auth")
public class FlowDelegateAuth extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权人（原办理人）ID */
    private String ownerUserId;

    /** 授权人姓名（冗余） */
    private String ownerUserName;

    /** 被授权人（代理人）ID */
    private String delegateUserId;

    /** 被授权人姓名（冗余） */
    private String delegateUserName;

    /** 匹配模式：{@code ALL} / {@code FLOW} / {@code FLOW_NODE} / {@code ROLE} */
    private String scopeType;

    /** 流程编码（{@code FLOW/FLOW_NODE} 模式必填） */
    private String flowCode;

    /** 节点编码（{@code FLOW_NODE} 模式必填） */
    private String nodeCode;

    /** 角色编码（{@code ROLE} 模式必填） */
    private String roleCode;

    /** 生效开始时间 */
    private LocalDateTime startTime;

    /** 生效结束时间 */
    private LocalDateTime endTime;

    /** 授权状态：{@code ENABLED} / {@code DISABLED} / {@code EXPIRED} / {@code REVOKED} */
    private String authStatus;

    /** 授权原因（如「出差 3 天」「部门调整」） */
    private String reason;

    /** 链路追踪 ID */
    private String providerTraceId;
}
