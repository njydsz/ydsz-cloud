package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * API 权限注册请求 DTO。
 *
 * <p>供 system 模块扫描 {@code @AuthApiPermission} 后向 userinfo 注册可授权权限项。
 *
 * @author ydsz-team
 * @since 26.10.30
 */
@Data
public class ApiPermissionRegistrationDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** API 权限码，例如 {@code system:user:create}。 */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 100, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String apiCode;

  /** API 权限名称，例如接口 OpenAPI summary。 */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String apiName;

  /** 所属服务名，用于权限项展示和排序。 */
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String serviceName;
}
