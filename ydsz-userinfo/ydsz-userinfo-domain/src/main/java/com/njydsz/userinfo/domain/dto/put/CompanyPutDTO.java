package com.njydsz.userinfo.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 公司修改请求 DTO。
 *
 * <p>对应后端 {@code PUT /api/v1/company} 请求体。 修改时 {@link #id} 必填，其余字段按需填写，未传字段保持原值不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyPutDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 公司 ID（必填） */
  @NotBlank(message = "ID不能为空")
  @Xss(message = "id包含非法内容")
  private String id;

  /** 公司名称 */
  @NotBlank(message = "公司名称不能为空")
  @Size(max = 128, message = "公司名称长度不能超过 128 个字符")
  @Xss(message = "companyName包含非法内容")
  private String companyName;

  /** 公司编码（全局唯一） */
  @NotBlank(message = "公司编码不能为空")
  @Size(max = 64, message = "公司编码长度不能超过 64 个字符")
  @Xss(message = "companyCode包含非法内容")
  private String companyCode;

  /** 上级公司 ID */
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
