package com.njydsz.pmis.agent.engine;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 多模态输入（P4-9 落地）。
 *
 * <p>对标 OpenAI Vision / Coze 多模态 / Dify Image Input：
 * <ul>
 *   <li>支持图片输入（URL 或 Base64）</li>
 *   <li>支持文件输入（PDF、Word 等文档 URL）</li>
 *   <li>与文本一起作为 LLM 的用户消息输入</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * MultimodalInput input = MultimodalInput.builder()
 *     .text("请分析这张项目甘特图")
 *     .imageUrl("https://example.com/gantt.png")
 *     .build();
 * AgentContext ctx = new AgentContext();
 * ctx.setMultimodalInput(input);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-9)
 */
@Data
public class MultimodalInput implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文本输入（可空，与图片/文件共存） */
    private String text;

    /** 图片 URL 列表（支持多张图片） */
    private List<String> imageUrls;

    /** 图片 Base64 列表（data:image/png;base64,... 格式） */
    private List<String> imageBase64List;

    /** 文件 URL 列表（PDF、Word 等文档） */
    private List<String> fileUrls;

    /** 是否包含多模态内容 */
    public boolean hasMultimodalContent() {
        return (imageUrls != null && !imageUrls.isEmpty())
                || (imageBase64List != null && !imageBase64List.isEmpty())
                || (fileUrls != null && !fileUrls.isEmpty());
    }

    /** 构建简单文本输入 */
    public static MultimodalInput text(String text) {
        MultimodalInput input = new MultimodalInput();
        input.setText(text);
        return input;
    }

    /** 构建图片 URL 输入 */
    public static MultimodalInput imageUrl(String text, String imageUrl) {
        MultimodalInput input = new MultimodalInput();
        input.setText(text);
        input.setImageUrls(List.of(imageUrl));
        return input;
    }

    /**
     * 转换为 OpenAI 兼容的 content 数组格式。
     *
     * <p>OpenAI Vision 格式：
     * <pre>
     * [
     *   {"type":"text","text":"请分析这张图"},
     *   {"type":"image_url","image_url":{"url":"https://..."}}
     * ]
     * </pre>
     *
     * @return content 数组的 JSON 字符串
     */
    public String toOpenAiContentJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        if (text != null && !text.isBlank()) {
            sb.append("{\"type\":\"text\",\"text\":\"")
                    .append(escapeJson(text)).append("\"}");
            first = false;
        }

        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (!first) sb.append(",");
                sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                        .append(escapeJson(url)).append("\"}}");
                first = false;
            }
        }

        if (imageBase64List != null) {
            for (String base64 : imageBase64List) {
                if (!first) sb.append(",");
                sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                        .append(escapeJson(base64)).append("\"}}");
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /** 简单 JSON 转义 */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
