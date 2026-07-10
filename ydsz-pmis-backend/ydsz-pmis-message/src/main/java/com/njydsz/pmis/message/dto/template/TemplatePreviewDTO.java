package com.njydsz.pmis.message.dto.template;


import lombok.Data;

import java.util.Map;

/**
 * 模板预览请求 DTO。
 *
 * <p>P1-6: 支持两种预览模式：
 * <ul>
 *   <li>按 templateCode 加载已发布模板 + 传入 params 渲染预览</li>
 *   <li>直接传入 content + params 渲染预览（草稿预览）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
public class TemplatePreviewDTO {

    /** 模板编码（二选一：templateCode 或 content） */
    private String templateCode;

    /** 模板内容（草稿预览时直接传入，不走模板查询） */
    private String content;

    /** 语言区域 */
    private String locale;

    /** 渲染参数 */
    private Map<String, Object> params;
}
