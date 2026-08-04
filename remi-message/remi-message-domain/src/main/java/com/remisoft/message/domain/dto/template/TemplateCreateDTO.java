package com.remisoft.message.domain.dto.template;


import lombok.Data;

/**
 * 模板创建/更新 DTO
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class TemplateCreateDTO {

    /** 模板编码 */
    private String templateCode;

    /** 通道 */
    private String channel;

    /** 语言区域 */
    private String locale;

    /** 语义版本 */
    private String version;

    /** 模板分类 */
    private String category;

    /** 场景编码 */
    private String sceneCode;

    /** 主题(EMAIL 专用) */
    private String subject;

    /** 模板内容 */
    private String content;

    /** 供应商 */
    private String provider;

    /** 供应商侧模板 ID */
    private String providerKey;

    /** 短信签名 */
    private String signName;

    /** 描述说明 */
    private String description;
}
