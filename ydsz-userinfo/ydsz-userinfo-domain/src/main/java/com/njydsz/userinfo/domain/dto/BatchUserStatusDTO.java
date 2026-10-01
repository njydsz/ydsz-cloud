package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 批量用户状态操作 DTO。
 *
 * <p>用于批量启用/禁用/删除用户账号。单次操作用户数量受 {@code ydsz.userinfo.batch-size-limit} 限制
 * （默认 500），超出时需分批调用。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class BatchUserStatusDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID 列表 */
  @NotEmpty(message = "{userinfo.param.required}")
  @NotNull(message = "{userinfo.param.required}")
  private List<String> ids;
}
