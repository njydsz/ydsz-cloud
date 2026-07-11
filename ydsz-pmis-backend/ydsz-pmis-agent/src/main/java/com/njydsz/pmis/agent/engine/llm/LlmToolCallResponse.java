package com.njydsz.pmis.agent.engine.llm;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 原生 Function Calling 响应（P4-2 落地）。
 *
 * <p>封装 LLM 在收到 tools 参数后返回的结果，可能是：
 * <ul>
 *   <li>纯文本回复（LLM 决定不调用工具，直接回答）</li>
 *   <li>工具调用请求（LLM 决定调用一个或多个工具，可能并行）</li>
 * </ul>
 *
 * <p>对标 OpenAI Chat Completions 响应中的 finish_reason + tool_calls 结构。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-2)
 */
@Data
public class LlmToolCallResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** LLM 纯文本回复（当不调用工具时填充） */
    private String content;

    /** LLM 请求调用的工具列表（可能并行多个） */
    private List<ToolCall> toolCalls;

    /** Token 用量统计（P0-3 落地） */
    private TokenUsage usage;

    /** 是否请求调用工具 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /**
     * 单个工具调用请求。
     */
    @Data
    public static class ToolCall implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 工具调用 ID（LLM 生成，用于关联工具执行结果） */
        private String id;

        /** 工具调用索引（用于并行调用的顺序标识） */
        private int index;

        /** 工具类型（通常为 "function"） */
        private String type;

        /** 函数调用信息 */
        private FunctionCall function;

        /**
         * 函数调用信息。
         */
        @Data
        public static class FunctionCall implements Serializable {

            @Serial
            private static final long serialVersionUID = 1L;

            /** 工具/函数名称 */
            private String name;

            /** 调用参数（JSON 字符串，需解析为 Map） */
            private String arguments;

            /**
             * 将 arguments JSON 字符串解析为 Map。
             *
             * @return 参数 Map；解析失败返回空 Map
             */
            @SuppressWarnings("unchecked")
            public Map<String, Object> getArgumentsAsMap() {
                if (arguments == null || arguments.isBlank()) {
                    return Map.of();
                }
                try {
                    return com.alibaba.fastjson2.JSON.parseObject(arguments, Map.class);
                } catch (Exception e) {
                    return Map.of();
                }
            }
        }
    }
}
