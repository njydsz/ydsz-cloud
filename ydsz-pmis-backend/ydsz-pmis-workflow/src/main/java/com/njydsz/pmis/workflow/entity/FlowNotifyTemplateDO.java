package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * P1-2: 工作流通知模板 DO
 *
 * <p>对标 GAP-38：通知内容模板化管理，替代硬编码。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Data
@TableName("pmis_flow_notify_template")
public class FlowNotifyTemplateDO {

    private String id;
    private String tenantId;
    private String templateCode;
    private String templateName;
    private String channel;
    private String title;
    private String content;
    private Integer enabled;
    private String description;
    private String createdBy;
    private java.time.LocalDateTime createdAt;
    private String updatedBy;
    private java.time.LocalDateTime updatedAt;
    private Integer deleted;
    private String providerTraceId;
    @Version
    private Integer version;
}
