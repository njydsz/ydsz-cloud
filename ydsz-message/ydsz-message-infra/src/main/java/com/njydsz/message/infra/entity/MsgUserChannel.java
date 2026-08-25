package com.njydsz.message.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户通道绑定表: userId → 各通道联系方式映射。
 *
 * <p>发送时由管道自动解析 receiver(userId) → channelUserId(phone/email/dingtalkUserId 等)，
 * 避免业务方在调用消息中心时自行查询各通道联系方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_msg_user_channel")
public class MsgUserChannel extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID(关联 ydsz_employee.id) */
  private String userId;

  /** 通道类型: SMS/EMAIL/PUSH/DINGTALK/WECOM/FEISHU 等 */
  private String channelType;

  /** 通道用户标识(手机号/邮箱/钉钉userId/企微userId/飞书userId/个推cid) */
  private String channelUserId;

  /** 是否已验证: 0 未验证 / 1 已验证 */
  private Integer verified;

  /** 是否主绑定: 0 否 / 1 是(同通道多绑定时优先使用主绑定) */
  private Integer isPrimary;

  /** 扩展字段 JSON(如 deviceToken / openId 等) */
  private String extra;
}
