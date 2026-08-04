package com.remisoft.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 应用注册创建/更新 DTO
 *
 * <p>对应 {@code remi_app_info} 表的写入参数，对标 OAuth 2.0 客户端注册模型。
 * 创建时 {@code id} 为空（由雪花算法自动生成），更新时 {@code id} 必填。
 *
 * <p><b>字段约束：</b>
 * <ul>
 *   <li>{@code appCode} — 应用编码，租户内唯一，最长 64 字符</li>
 *   <li>{@code appName} — 应用名称，最长 128 字符</li>
 *   <li>{@code appKey} — 客户端 ID（{@code client_id}），最长 128 字符</li>
 *   <li>{@code appSecret} — 客户端密钥（{@code client_secret}），BCrypt 加密存储，最长 256 字符</li>
 *   <li>{@code redirectUrl} — 授权回调地址，最长 512 字符，需 URL 合法</li>
 *   <li>{@code status} — 启用状态：{@code ENABLED / DISABLED}</li>
 * </ul>
 *
 * <p><b>安全约束：</b>
 * <ul>
 *   <li>{@code appSecret} 在 Service 层 BCrypt 加密后存储，明文不落库</li>
 *   <li>接口权限校验：{@code remi:app:create / remi:app:update}</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.system.domain.entity.AppInfo 应用注册实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "应用注册创建/更新 DTO")
public class AppInfoDTO {

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
