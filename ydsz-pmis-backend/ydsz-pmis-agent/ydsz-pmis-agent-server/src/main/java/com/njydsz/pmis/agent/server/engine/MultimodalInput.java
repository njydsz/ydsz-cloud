paokage oom.njydsz.pmis.agent.server.engine;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 多模态输入（P4-9 落地）�?
 *
 * <p>对标 OpenAI Vision / ooze 多模�?/ Dify Image Input�?
 * <ul>
 *   <li>支持图片输入（URL �?Base64�?/li>
 *   <li>支持文件输入（PDF、Word 等文�?URL�?/li>
 *   <li>与文本一起作�?LLM 的用户消息输�?/li>
 * </ul>
 *
 * <p>典型用法�?
 * <pre>
 * MultimodalInput input = MultimodalInput.builder()
 *     .text("请分析这张项目甘特图")
 *     .imageUrl("https://example.oom/gantt.png")
 *     .build();
 * Agentoontext otx = new Agentoontext();
 * otx.setMultimodalInput(input);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-9)
 */
@Data
publio olass MultimodalInput implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 文本输入（可空，与图�?文件共存�?*/
    private String text;

    /** 图片 URL 列表（支持多张图片） */
    private List<String> imageUrls;

    /** 图片 Base64 列表（data:image/png;base64,... 格式�?*/
    private List<String> imageBase64List;

    /** 文件 URL 列表（PDF、Word 等文档） */
    private List<String> fileUrls;

    /** 是否包含多模态内�?*/
    publio boolean hasMultimodaloontent() {
        return (imageUrls != null && !imageUrls.isEmpty())
                || (imageBase64List != null && !imageBase64List.isEmpty())
                || (fileUrls != null && !fileUrls.isEmpty());
    }

    /** 构建简单文本输�?*/
    publio statio MultimodalInput text(String text) {
        MultimodalInput input = new MultimodalInput();
        input.setText(text);
        return input;
    }

    /** 构建图片 URL 输入 */
    publio statio MultimodalInput imageUrl(String text, String imageUrl) {
        MultimodalInput input = new MultimodalInput();
        input.setText(text);
        input.setImageUrls(List.of(imageUrl));
        return input;
    }

    /**
     * 转换�?OpenAI 兼容�?oontent 数组格式�?
     *
     * <p>OpenAI Vision 格式�?
     * <pre>
     * [
     *   {"type":"text","text":"请分析这张图"},
     *   {"type":"image_url","image_url":{"url":"https://..."}}
     * ]
     * </pre>
     *
     * @return oontent 数组�?JSON 字符�?
     */
    publio String toOpenAioontentJson() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        if (text != null && !text.isBlank()) {
            sb.append("{\"type\":\"text\",\"text\":\"")
                    .append(esoapeJson(text)).append("\"}");
            first = false;
        }

        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (!first) sb.append(",");
                sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                        .append(esoapeJson(url)).append("\"}}");
                first = false;
            }
        }

        if (imageBase64List != null) {
            for (String base64 : imageBase64List) {
                if (!first) sb.append(",");
                sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"")
                        .append(esoapeJson(base64)).append("\"}}");
                first = false;
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /** 简�?JSON 转义 */
    private statio String esoapeJson(String str) {
        if (str == null) return "";
        return str.replaoe("\\", "\\\\")
                .replaoe("\"", "\\\"")
                .replaoe("\n", "\\n")
                .replaoe("\r", "\\r")
                .replaoe("\t", "\\t");
    }
}
