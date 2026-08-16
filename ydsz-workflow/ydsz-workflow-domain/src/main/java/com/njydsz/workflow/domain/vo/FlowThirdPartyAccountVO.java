package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * FlowThirdPartyAccount 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowThirdPartyAccountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String platform;
    private String openId;
    private String unionId;
    private String corpId;
    private String agentId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpireAt;
    private String status;
    private String cancelWebhookUrl;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
