package com.njydsz.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * Company 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class CompanyPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128, message = "公司名称长度不能超过 128 个字符")
    private String companyName;

    @NotBlank(message = "公司编码不能为空")
    @Size(max = 64, message = "公司编码长度不能超过 64 个字符")
    private String companyCode;

    private String parentId;

    @Size(max = 64, message = "联系人长度不能超过 64 个字符")
    private String contactPerson;

    @Size(max = 20, message = "联系电话长度不能超过 20 个字符")
    private String contactPhone;

    @Size(max = 255, message = "地址长度不能超过 255 个字符")
    private String address;

    private String status;

}