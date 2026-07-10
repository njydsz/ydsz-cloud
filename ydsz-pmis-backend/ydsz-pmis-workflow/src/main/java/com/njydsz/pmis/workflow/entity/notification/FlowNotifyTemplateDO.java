package com.njydsz.pmis.workflow.entity.notification;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * P1-2: 工作流通知模板 DO
 *
 * <p>对标 GAP-38：通知内容模板化管理，替代硬编码。
 *
 * <p>P1-5: 新增 {@code locale} 字段支持多语言，同一 templateCode + channel 可配置多种语言模板，
 * Resolver 按 locale 优先匹配，未命中时降级到默认 locale（zh_CN）。
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
    /** P1-5: 语言区域（如 zh_CN / en_US），默认 zh_CN */
    private String locale;
    private String title;
    private String content;
    private Integer enabled;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private String providerTraceId;
    @Version
    private Integer version;
}
