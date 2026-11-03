package com.njydsz.system.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * API 权限注册请求。
 *
 * <p>用于 system 扫描 {@code @AuthApiPermission} 后向 userinfo 注册可授权权限项。
 *
 * @author ydsz-team
 * @since 26.10.30
 */
@Data
public class ApiPermissionRegistrationRequest implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** API 权限码。 */
  @NotBlank(message = "{system.dto.apiPermissionRegistration.apiCode.required}")
  @Size(max = 100, message = "{system.dto.apiPermissionRegistration.apiCode.max_length}")
  @Xss(message = "{system.dto.apiPermissionRegistration.apiCode.xss}")
  private String apiCode;

  /** API 权限名称。 */
  @Size(max = 255, message = "{system.dto.apiPermissionRegistration.apiName.max_length}")
  @Xss(message = "{system.dto.apiPermissionRegistration.apiName.xss}")
  private String apiName;

  /** 所属服务名。 */
  @Size(max = 64, message = "{system.dto.apiPermissionRegistration.serviceName.max_length}")
  @Xss(message = "{system.dto.apiPermissionRegistration.serviceName.xss}")
  private String serviceName;
}
