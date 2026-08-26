package com.njydsz.nextwiki.infra.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 知识库空间持久化实体
 *
 * <p><b>S3-P2-01：空间管理聚合根</b>
 *
 * <p>对应知识库空间表 {@code nw_space}，表示一个知识库空间（类似 Confluence 的 Space）。 空间是文件节点的顶级容器，每个文件节点必须属于一个空间。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_wiki_space")
public class Space implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 状态：活跃 */
  public static final String STATUS_ACTIVE = "active";

  /** 状态：已归档 */
  public static final String STATUS_ARCHIVED = "archived";

  /** 状态：已删除 */
  public static final String STATUS_DELETED = "deleted";

  /** 可见性：私有 */
  public static final String VISIBILITY_PRIVATE = "private";

  /** 可见性：组织内可见 */
  public static final String VISIBILITY_ORGANIZATION = "organization";

  /** 可见性：公开 */
  public static final String VISIBILITY_PUBLIC = "public";

  /** 主键ID（分布式雪花ID） */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 空间名称 */
  private String name;

  /** 空间描述 */
  private String description;

  /** 空间图标 URL */
  private String iconUrl;

  /** 空间封面 URL */
  private String coverUrl;

  /** 租户ID */
  private String tenantId;

  /** 空间所有者（创建者） */
  private String ownerId;

  /** 空间状态：active / archived / deleted */
  private String status;

  /** 可见性：private / organization / public */
  private String visibility;

  /** 排序序号 */
  private Integer sortOrder;

  /** 成员数量 */
  private Integer memberCount;

  /** 节点数量（文件/目录总数） */
  private Integer nodeCount;

  /** 空间独立配额（字节，NULL 表示使用租户配额） */
  private Long quotaLimit;

  /** 已使用配额（字节） */
  private Long quotaUsed;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 创建人 */
  private String createdBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 更新人 */
  private String updatedBy;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean deleted;

  /** 删除时间 */
  private LocalDateTime deletedTime;
}
