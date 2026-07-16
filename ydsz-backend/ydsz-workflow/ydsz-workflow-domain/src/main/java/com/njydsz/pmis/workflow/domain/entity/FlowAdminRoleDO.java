package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.domain.entity.BaseDO;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程管理员角色映射 DO（P1-6）
 *
 * <p>存储用户与流程管理员角色的映射关系。
 * 一个用户可拥有多个角色，一个角色可分配给多个用户。
 *
 * <p>角色编码：
 * <ul>
 *   <li>{@code FLOW_ADMIN} — 流程管理员：可管理所有流程（部署/下线/迁移/终止/管理员转交）</li>
 *   <li>{@code FLOW_DESIGNER} — 流程设计者：可设计/编辑流程定义</li>
 *   <li>{@code FLOW_AUDITOR} — 流程审计员：可查看所有流程实例和审计日志（只读）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_admin_role")
public class FlowAdminRoleDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 角色编码（FLOW_ADMIN / FLOW_DESIGNER / FLOW_AUDITOR） */
    private String roleCode;

    /** 租户 ID */
    private String tenantId;

    /** 是否启用 */
    private Boolean enabled;

    /** 授权人 ID */
    private String grantedBy;

    /** 授权时间 */
    private LocalDateTime grantedAt;

    /** 过期时间（null 表示永不过期） */
    private LocalDateTime expireAt;
}
