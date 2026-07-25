package com.njydsz.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用注册 VO。
 *
 * <p>注意：不包含 {@code appSecret} 字段，避免密钥哈希泄露给前端。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "应用注册视图对象")
public class AppInfoVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "应用编码")
    private String appCode;

    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "应用 Key（client_id）")
    private String appKey;

    @Schema(description = "授权回调地址")
    private String redirectUrl;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
