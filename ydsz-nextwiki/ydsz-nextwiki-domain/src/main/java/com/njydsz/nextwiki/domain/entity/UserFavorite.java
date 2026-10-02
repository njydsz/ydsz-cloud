package com.njydsz.nextwiki.domain.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户收藏夹持久化实体。
 *
 * <p><b>S2-P1-06：快捷访问入口</b>
 *
 * <p>对应用户收藏夹表 {@code ydsz_file_user_favorite}，记录用户收藏的文件/目录节点（{@link #nodeId}），
 * 通过 {@link #sort} 控制收藏列表的展示顺序（值越小越靠前）。支持软删除（{@link #isDeleted}），
 * 删除操作仅翻转标记位而不移除记录。每个用户对同一节点仅保留一条收藏记录。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合 JPA 继承，Builder 返回原始父类类型
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_file_user_favorite")
public class UserFavorite extends MpBaseEntity<String> {

  /** 用户ID */
  private String userId;

  /** 收藏的文件/目录节点ID */
  private String nodeId;

  /** 删除时间 */
  private LocalDateTime deletedTime;
}
