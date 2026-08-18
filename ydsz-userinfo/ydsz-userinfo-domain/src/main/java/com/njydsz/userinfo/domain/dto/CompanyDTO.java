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
 * @since 1.0.0
 */
@Data
public class CompanyDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 公司 ID（更新时必填） */
  @Xss(message = "id包含非法内容")
  private String id;

  /** 公司名称 */
  @NotBlank(message = "公司名称不能为空")
  @Size(max = 128, message = "公司名称长度不能超过 128 个字符")
  @Xss(message = "companyName包含非法内容")
  private String companyName;

  /** 公司编码（全局唯一，建议格式 {@code COMP_XXX}） */
  @NotBlank(message = "公司编码不能为空")
  @Size(max = 64, message = "公司编码长度不能超过 64 个字符")
  @Xss(message = "companyCode包含非法内容")
  private String companyCode;

  /** 上级公司 ID（{@code "0"} 表示顶级公司） */
  @Xss(message = "parentId包含非法内容")
  private String parentId;

  /** 联系人姓名 */
  @Size(max = 64, message = "联系人长度不能超过 64 个字符")
  @Xss(message = "contactPerson包含非法内容")
  private String contactPerson;

  /** 联系电话 */
  @Size(max = 20, message = "联系电话长度不能超过 20 个字符")
  @Xss(message = "contactPhone包含非法内容")
  private String contactPhone;

  /** 公司地址 */
  @Size(max = 255, message = "地址长度不能超过 255 个字符")
  @Xss(message = "address包含非法内容")
  private String address;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "status包含非法内容")
  private String status;
}
