package com.njydsz.message.domain.dto;

import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;
import com.njydsz.common.safe.sensitive.SensitiveData;
import com.njydsz.common.safe.sensitive.SensitiveType;

/**
 * 用户通道绑定 DTO。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class UserChannelBindingDTO {

  /** 用户 ID */
  @Xss private String userId;

  /** 通道类型: SMS/EMAIL/PUSH/DINGTALK/WECOM/FEISHU 等 */
  @Xss private String channelType;

  /** 通道用户标识(手机号/邮箱/钉钉userId 等) */
  @SensitiveData(SensitiveType.PHONE)
  @Xss private String channelUserId;

  /** 是否已验证: 0 未验证 / 1 已验证 */
  private Integer verified;

  /** 是否主绑定: 0 否 / 1 是 */
  private Integer isPrimary;

  /** 扩展字段 JSON */
  @Xss private String extra;
}
