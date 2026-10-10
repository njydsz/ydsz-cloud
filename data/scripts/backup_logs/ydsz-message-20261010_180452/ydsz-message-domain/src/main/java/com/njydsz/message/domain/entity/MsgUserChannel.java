package com.njydsz.message.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户通道绑定实体，维护 userId 到各通道联系方式的映射关系。
 *
 * <p>对应数据库表 {@code ydsz_msg_user_channel}。发送时由系统自动解析
 * receiver(userId) → channelUserId（手机号/邮箱/钉钉 userId/企微 userId/飞书 userId/个推 cid），
 * 避免业务方在调用消息中心时自行查询联系方式。isPrimary 标识主绑定（同通道多绑定时优先使用）。
 *
 * @author ydsz
 * @since 26.09.24
 */// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 配合泛型父类继承，Builder 返回原始父类类型
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
