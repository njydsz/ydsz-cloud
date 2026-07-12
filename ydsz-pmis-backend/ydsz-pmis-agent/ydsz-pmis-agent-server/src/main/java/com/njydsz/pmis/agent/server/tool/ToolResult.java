package com.njydsz.pmis.agent.server.tool;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 工具执行结果（P1-1 落地）
 *
 * <p>封装工具执行的输出，供 ReAct 推理循环作为 Observation 反馈给 LLM。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Data
public class ToolResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 执行是否成功 */
    private boolean success;

    /** 文本输出（LLM 可读的观察结果） */
    private String output;

    /** 结构化数据（可选，供程序逻辑使用） */
    private Map<String, Object> data;

    /** 错误信息（success=false 时填充） */
    private String error;

    /** 构造成功结果 */
    public static ToolResult success(String output) {
        ToolResult r = new ToolResult();
        r.success = true;
        r.output = output;
        return r;
    }

    /** 构造成功结果（带结构化数据） */
    public static ToolResult success(String output, Map<String, Object> data) {
        ToolResult r = new ToolResult();
        r.success = true;
        r.output = output;
        r.data = data;
        return r;
    }

    /** 构造失败结果 */
    public static ToolResult failure(String error) {
        ToolResult r = new ToolResult();
        r.success = false;
        r.error = error;
        r.output = "工具执行失败: " + error;
        return r;
    }
}
