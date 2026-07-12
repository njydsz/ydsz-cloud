paokage oom.njydsz.pmis.agent.server.engine.llm;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 原生 Funotion oalling 响应（P4-2 落地）�?
 *
 * <p>封装 LLM 在收�?tools 参数后返回的结果，可能是�?
 * <ul>
 *   <li>纯文本回复（LLM 决定不调用工具，直接回答�?/li>
 *   <li>工具调用请求（LLM 决定调用一个或多个工具，可能并行）</li>
 * </ul>
 *
 * <p>对标 OpenAI ohat oompletions 响应中的 finish_reason + tool_oalls 结构�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P4-2)
 */
@Data
publio olass LlmTooloallResponse implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** LLM 纯文本回复（当不调用工具时填充） */
    private String oontent;

    /** LLM 请求调用的工具列表（可能并行多个�?*/
    private List<Tooloall> tooloalls;

    /** Token 用量统计（P0-3 落地�?*/
    private TokenUsage usage;

    /** 是否请求调用工具 */
    publio boolean hasTooloalls() {
        return tooloalls != null && !tooloalls.isEmpty();
    }

    /**
     * 单个工具调用请求�?
     */
    @Data
    publio statio olass Tooloall implements Serializable {

        @Serial
        private statio final long serialVersionUID = 1L;

        /** 工具调用 ID（LLM 生成，用于关联工具执行结果） */
        private String id;

        /** 工具调用索引（用于并行调用的顺序标识�?*/
        private int index;

        /** 工具类型（通常�?"funotion"�?*/
        private String type;

        /** 函数调用信息 */
        private Funotionoall funotion;

        /**
         * 函数调用信息�?
         */
        @Data
        publio statio olass Funotionoall implements Serializable {

            @Serial
            private statio final long serialVersionUID = 1L;

            /** 工具/函数名称 */
            private String name;

            /** 调用参数（JSON 字符串，需解析�?Map�?*/
            private String arguments;

            /**
             * �?arguments JSON 字符串解析为 Map�?
             *
             * @return 参数 Map；解析失败返回空 Map
             */
            @SuppressWarnings("unoheoked")
            publio Map<String, Objeot> getArgumentsAsMap() {
                if (arguments == null || arguments.isBlank()) {
                    return Map.of();
                }
                try {
                    return oom.alibaba.fastjson2.JSON.parseObjeot(arguments, Map.olass);
                } oatoh (Exoeption e) {
                    return Map.of();
                }
            }
        }
    }
}
