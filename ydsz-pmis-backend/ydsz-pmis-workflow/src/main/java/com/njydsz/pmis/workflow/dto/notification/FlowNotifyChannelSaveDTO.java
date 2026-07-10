package com.njydsz.pmis.workflow.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程通知通道创建/更新 DTO
 *
 * <p>隔离 {@link com.njydsz.pmis.workflow.entity.FlowNotifyChannelDO} 的
 * id/tenantId 及审计字段，避免越权写入。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "通知通道表单")
public class FlowNotifyChannelSaveDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "通道 ID（更新时传入）")
    private String id;

    @NotBlank(message = "通道类型不能为空")
    @Schema(description = "通道类型: WEBHOOK/EMAIL/DINGTALK/FEISHU", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channelType;

    @NotBlank(message = "通道名称不能为空")
    @Schema(description = "通道名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channelName;

    @Schema(description = "通道配置 JSON")
    private String config;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
