package com.njydsz.userinfo.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 密码历史实体
 *
 * <p>对应数据库表 {@code ydsz_user_password_history}，用于记录用户修改过的密码历史， 防止用户短期内重复使用旧密码，符合信息安全等级保护和密码安全管理要求。
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>密码使用 BCrypt 加密存储，每次加密结果不同，采用逐条比对方式校验
 *   <li>仅保留最近 N 条密码记录（由配置 {@code ydsz.userinfo.password-history-count} 控制，默认 5 条）
 *   <li>用户删除时同步清理密码历史（物理删除，避免敏感数据残留）
 * </ul>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>{@code idx_user_id_created_at} — 用户 ID + 创建时间复合索引（查询历史密码用）
 *   <li>{@code idx_user_id} — 用户 ID 单字段索引（按用户清理历史用）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@TableName("ydsz_user_password_history")
@SuppressWarnings("unchecked")
public class UserPasswordHistory {

  /** 主键 ID（雪花算法） */
  @TableId(type = IdType.ASSIGN_ID)
  private String id;

  /** 用户 ID（关联 ydsz_user_account.id） */
  private String userId;

  /** BCrypt 加密后的历史密码哈希 */
  private String passwordHash;

  /** 创建时间（该密码被设置的日期） */
  private LocalDateTime createdAt;

  /** 逻辑删除标记（0=未删除，1=已删除；用于软删除兼容） */
  @TableField(value = "deleted")
  private Integer deleted;
}
