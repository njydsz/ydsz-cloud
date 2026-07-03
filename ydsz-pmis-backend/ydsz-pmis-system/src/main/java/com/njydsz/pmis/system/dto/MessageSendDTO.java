package com.njydsz.pmis.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 消息发送 DTO（HTTP 入参）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "消息发送请求")
public class MessageSendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "通道: SMS/EMAIL/PUSH", requiredMode = RequiredMode.REQUIRED)
    private String channel;

    @Schema(description = "模板编码（使用模板时必填）")
    private String templateCode;

    @Schema(description = "接收人", requiredMode = RequiredMode.REQUIRED)
    private String receiver;

    @Schema(description = "模板参数")
    private Map<String, Object> params;

    @Schema(description = "直接内容（不使用模板时填此项）")
    private String content;

    @Schema(description = "主题（EMAIL 专用）")
    private String subject;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务单据 ID")
    private String bizId;
}
