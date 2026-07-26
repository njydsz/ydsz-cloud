package com.njydsz.system.domain.dto;

import com.njydsz.common.domain.dto.BaseDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 应用注册创建/更新 DTO。
 *
 * @author ydsz-team
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "应用注册创建/更新 DTO")
public class AppInfoDTO extends BaseDTO {

    @Schema(description = "主键 ID（更新时必填）")
    private String id;

    @NotBlank(message = "应用编码不能为空")
    @Size(max = 64, message = "应用编码长度不能超过64")
    @Schema(description = "应用编码")
    private String appCode;

    @NotBlank(message = "应用名称不能为空")
    @Size(max = 128, message = "应用名称长度不能超过128")
    @Schema(description = "应用名称")
    private String appName;

    @NotBlank(message = "应用 Key 不能为空")
    @Size(max = 128, message = "应用 Key 长度不能超过128")
    @Schema(description = "应用 Key（client_id）")
    private String appKey;

    @Size(max = 256, message = "应用密钥长度不能超过256")
    @Schema(description = "应用密钥（client_secret，BCrypt 加密存储）")
    private String appSecret;

    @Size(max = 512, message = "回调地址长度不能超过512")
    @Schema(description = "授权回调地址")
    private String redirectUrl;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
