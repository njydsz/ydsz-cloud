package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 消息模板视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String templateCode;
    private String channel;
    private String locale;
    private String version;
    private String category;
    private String sceneCode;
    private String subject;
    private String content;
    private String provider;
    private String providerKey;
    private String signName;
    private String status;
    private String auditStatus;
    private String auditBy;
    private LocalDateTime auditAt;
    private String auditRemark;
    private String description;
    private String variableDefs;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
