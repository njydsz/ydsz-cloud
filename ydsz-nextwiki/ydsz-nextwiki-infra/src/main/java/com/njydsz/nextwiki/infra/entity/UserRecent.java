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
 * 用户最近访问持久化实体
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>对应用户最近访问表 {@code nw_user_recent}，记录用户的文件访问历史， 自动保留最新访问记录（同一节点只保留一条），支持按访问时间倒序查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("nw_user_recent")
public class UserRecent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键ID（分布式雪花ID） */
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

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean deleted;
}
