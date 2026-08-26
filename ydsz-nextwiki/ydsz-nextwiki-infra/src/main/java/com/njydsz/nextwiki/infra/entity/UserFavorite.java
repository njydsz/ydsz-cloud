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
 * 用户收藏夹持久化实体
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>对应用户收藏夹表 {@code nw_user_favorite}，记录用户收藏的文件/目录节点， 支持排序与软删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_wiki_user_favorite")
public class UserFavorite implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键ID（分布式雪花ID） */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 用户ID */
  private String userId;

  /** 收藏的文件/目录节点ID */
  private String nodeId;

  /** 租户ID */
  private String tenantId;

  /** 排序序号（值越小越靠前） */
  private Integer sortOrder;

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
