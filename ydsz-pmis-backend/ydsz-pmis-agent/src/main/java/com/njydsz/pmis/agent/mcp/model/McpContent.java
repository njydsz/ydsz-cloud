package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 内容项（P3-3 落地）。
 *
 * <p>tools/call 结果中的内容项，可以是文本、资源引用或图片。
 * 当前仅支持 text 类型，其他类型以原始 JSON 形式保留。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpContent {

    /** 内容类型：text / image / resource */
    private String type;

    /** 文本内容（type=text 时填充） */
    private String text;

    /** 图片数据（type=image 时填充，base64 编码） */
    @JsonProperty("data")
    private String imageData;

    /** 图片 MIME 类型（type=image 时填充） */
    private String mimeType;

    /** 资源 URI（type=resource 时填充） */
    private String uri;

    /**
     * 构造文本内容项。
     *
     * @param text 文本
     * @return 文本内容项
     */
    public static McpContent text(String text) {
        return McpContent.builder().type("text").text(text).build();
    }
}
