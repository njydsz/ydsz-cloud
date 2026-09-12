package com.njydsz.nextwiki.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 知识库空间成员持久化实体
 *
 * <p><b>S3-P2-01：空间成员角色管理</b>
 *
 * <p>对应空间成员表 {@code nw_space_member}，记录用户与空间的归属关系及角色。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_wiki_space_member")
public class SpaceMember extends MpBaseAuditEntity<String> {

  /** 角色：所有者（创建者，不可移除） */
  public static final String ROLE_OWNER = "owner";

  /** 角色：管理员（全部管理权限） */
  public static final String ROLE_ADMIN = "admin";

  /** 角色：编辑者（可读写，不可管理成员） */
  public static final String ROLE_EDITOR = "editor";

  /** 角色：查看者（只读权限） */
  public static final String ROLE_VIEWER = "viewer";

  /** 主键ID（分布式ID手动赋值，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 空间ID */
  private String spaceId;

  /** 用户ID */
  private String userId;

  /** 角色：owner / admin / editor / viewer */
  private String role;

  /** 租户ID */
  private String tenantId;

  /** 加入时间 */
  private LocalDateTime joinedAt;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean isDeleted;
}
