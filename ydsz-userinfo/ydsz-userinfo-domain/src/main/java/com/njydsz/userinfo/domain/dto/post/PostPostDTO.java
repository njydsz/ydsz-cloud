package com.njydsz.userinfo.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 岗位新增请求 DTO。
 *
 * <p>对应后端 {@code POST /api/v1/post} 请求体。
 * 岗位是职责维度（PM / DEV / QA / SA），与部门（组织归属）、角色（权限集合）正交。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class PostPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 岗位名称（前端展示，如「项目经理」「后端开发工程师」） */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 64, message = "岗位名称长度不能超过 64 个字符")
    @Xss(message = "postName包含非法内容")

    private String postName;

    /** 岗位编码（全局唯一，建议使用英文枚举值如 {@code PM} / {@code DEV}） */
    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 64, message = "岗位编码长度不能超过 64 个字符")
    @Xss(message = "postCode包含非法内容")

    private String postCode;

    /** 岗位描述 */
    @Size(max = 500, message = "描述长度不能超过 500 个字符")
    @Xss(message = "description包含非法内容")

    private String description;

    /** 同级排序序号（升序） */
    private Integer sortOrder;

    /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
    @Xss(message = "status包含非法内容")

    private String status;

}
