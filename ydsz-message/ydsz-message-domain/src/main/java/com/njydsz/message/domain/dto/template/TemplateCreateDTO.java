package com.njydsz.message.domain.dto.template;

import lombok.Data;
import com.njydsz.common.safe.annotation.Xss;

/**
 * 模板创建/更新 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class TemplateCreateDTO {

    /** 模板编码 */
    @Xss
    private String templateCode;

    /** 通道 */
    @Xss
    private String channel;

    /** 语言区域 */
    @Xss
    private String locale;

    /** 语义版本 */
    @Xss
    private String version;

    /** 模板分类 */
    @Xss
    private String category;

    /** 场景编码 */
    @Xss
    private String sceneCode;

    /** 主题(EMAIL 专用) */
    @Xss
    private String subject;

    /** 模板内容 */
    private String content;

    /** 供应商 */
    @Xss
    private String provider;

    /** 供应商侧模板 ID */
    @Xss
    private String providerKey;

    /** 短信签名 */
    @Xss
    private String signName;

    /** 描述说明 */
    @Xss
    private String description;
}
