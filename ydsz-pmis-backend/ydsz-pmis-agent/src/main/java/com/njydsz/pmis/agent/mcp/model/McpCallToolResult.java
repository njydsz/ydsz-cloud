package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP tools/call 结果（P3-3 落地）。
 *
 * <p>工具调用返回的内容列表和错误标记：
 * <pre>
 * {"content":[{"type":"text","text":"result..."}],"isError":false}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpCallToolResult {

    /** 内容列表（至少一项） */
    private List<McpContent> content;

    /** 是否为错误结果（true 表示工具执行失败但已返回错误信息，而非协议错误） */
    @JsonProperty("isError")
    @Builder.Default
    private boolean error = false;

    /**
     * 提取所有文本内容拼接为单个字符串。
     *
     * @return 拼接后的文本（无内容返回空字符串）
     */
    public String flattenText() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpContent item : content) {
            if (item != null && item.getText() != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(item.getText());
            }
        }
        return sb.toString();
    }
}
