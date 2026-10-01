package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 公司请求 DTO。
 *
 * <p>同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class CompanyDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 公司 ID（更新时必填） */
  @Xss(message = "{userinfo.param.xss}")
  private String id;

  /** 公司名称 */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 128, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String companyName;

  /** 公司编码（全局唯一，建议格式 {@code COMP_XXX}） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String companyCode;

  /** 上级公司 ID（{@code "0"} 表示顶级公司） */
  @Xss(message = "{userinfo.param.xss}")
  private String parentId;

  /** 联系人姓名 */
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String contactPerson;

  /** 联系电话 */
  @Size(max = 20, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String contactPhone;

  /** 公司地址 */
  @Size(max = 255, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String address;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "{userinfo.param.xss}")
  private String status;
}
