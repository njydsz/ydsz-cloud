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
 * 用户最近访问持久化实体。
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>对应用户最近访问表 {@code ydsz_wiki_user_recent}，记录用户的文件/目录访问历史。
 * 同一节点只保留一条记录（覆盖更新 {@link #accessedAt}），访问类型由 {@link #accessType} 标识
 * （view/edit/download）。前端"最近访问"模块按 {@link #accessedAt} 倒序展示，支持软删除（{@link #isDeleted}）。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 泛型擦除导致 unchecked 警告
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_wiki_user_recent")
public class UserRecent extends MpBaseAuditEntity<String> {

  /** 主键ID（分布式ID手动赋值，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 用户ID */
  private String userId;

  /** 访问的文件/目录节点ID */
  private String nodeId;

  /** 租户ID */
  private String tenantId;

  /** 访问类型：view / edit / download */
  private String accessType;

  /** 最近访问时间（排序字段） */
  private LocalDateTime accessedAt;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean isDeleted;
}
