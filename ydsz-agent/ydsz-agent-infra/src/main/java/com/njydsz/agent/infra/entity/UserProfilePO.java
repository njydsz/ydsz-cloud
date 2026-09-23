package com.njydsz.agent.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 用户画像持久化对象（映射 ydsz_agt_user_profile 表）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_user_profile")
public class UserProfilePO extends MpBaseAuditEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 用户 ID（主键，业务 ID，非自增，对应数据库 user_id 列）。 */
  @TableId(type = IdType.INPUT)
  @TableField("user_id")
  private String userId;

  /** 偏好语言（zh-CN / en-US） */
  private String preferredLanguage;

  /** 关注领域列表（JSON 列存储） */
  @TableField("interested_domains")
  private String interestedDomainsJson;

  /** 领域查询频次统计（JSON 列） */
  @TableField("domain_frequency")
  private String domainFrequencyJson;

  /** 查询风格 */
  private String queryStyle;

  /** 高频意图标签（JSON 列存储） */
  @TableField("common_intents")
  private String commonIntentsJson;

  /** 总交互次数 */
  private Integer totalInteractions;

  /** 最近交互时间 */
  private LocalDateTime lastInteractionAt;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
