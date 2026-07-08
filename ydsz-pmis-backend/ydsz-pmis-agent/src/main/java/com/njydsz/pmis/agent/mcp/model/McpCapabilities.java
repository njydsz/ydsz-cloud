package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP 服务端能力声明（P3-3 落地）。
 *
 * <p>在 initialize 握手响应中返回，声明服务端支持的功能。
 * 目前仅关注 tools 能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpCapabilities {

    /** 是否支持工具（tools） */
    private McpToolCapability tools;

    /**
     * 工具能力子对象。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class McpToolCapability {
        /** 是否支持 listChanged 通知 */
        private Boolean listChanged;
    }
}
