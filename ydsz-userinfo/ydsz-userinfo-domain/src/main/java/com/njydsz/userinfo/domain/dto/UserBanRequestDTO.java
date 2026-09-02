package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户封禁请求 DTO。
 *
 * <p>用于管理员封禁用户账号，支持临时封禁（指定到期时间）和永久封禁。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class UserBanRequestDTO implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /** 封禁类型（TEMPORARY/PERMANENT），必填 */
  @NotNull(message = "封禁类型不能为空")
  private String banType;

  /** 封禁原因，必填 */
  @NotBlank(message = "封禁原因不能为空")
  private String banReason;

  /** 封禁到期时间（临时封禁必填，永久封禁不填） */
  private LocalDateTime banExpireAt;
}
