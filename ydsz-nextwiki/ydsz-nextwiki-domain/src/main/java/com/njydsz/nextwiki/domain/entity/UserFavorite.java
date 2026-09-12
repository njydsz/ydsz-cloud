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
 * 用户收藏夹持久化实体
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>对应用户收藏夹表 {@code nw_user_favorite}，记录用户收藏的文件/目录节点， 支持排序与软删除。
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
@TableName("ydsz_wiki_user_favorite")
public class UserFavorite extends MpBaseAuditEntity<String> {

  /** 主键ID（分布式ID手动赋值，覆盖基类 ASSIGN_ID）。 */
  @TableId(type = IdType.INPUT)
  private String id;

  /** 用户ID */
  private String userId;

  /** 收藏的文件/目录节点ID */
  private String nodeId;

  /** 租户ID */
  private String tenantId;

  /** 排序序号（值越小越靠前） */
  private Integer sort;

  /** 逻辑删除标识 */
  @TableLogic
  private Boolean isDeleted;

  /** 删除时间 */
  private LocalDateTime deletedTime;
}
