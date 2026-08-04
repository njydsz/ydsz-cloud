package com.remisoft.userinfo.domain.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.io.Serial;
/**
 * 公司新增请求 DTO。
 *
 * <p>对应后端 {@code POST /api/v1/company} 请求体。
 * 新增时需要填写公司基本信息、联系信息及初始状态。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class CompanyPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 公司名称 */
    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128, message = "公司名称长度不能超过 128 个字符")
    private String companyName;

    /** 公司编码（全局唯一，建议格式 {@code COMP_XXX}） */
    @NotBlank(message = "公司编码不能为空")
    @Size(max = 64, message = "公司编码长度不能超过 64 个字符")
    private String companyCode;

    /** 上级公司 ID（{@code "0"} 表示顶级公司） */
    private String parentId;

    /** 联系人姓名 */
    @Size(max = 64, message = "联系人长度不能超过 64 个字符")
    private String contactPerson;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话长度不能超过 20 个字符")
    private String contactPhone;

    /** 公司地址 */
    @Size(max = 255, message = "地址长度不能超过 255 个字符")
    private String address;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    private String status;

}