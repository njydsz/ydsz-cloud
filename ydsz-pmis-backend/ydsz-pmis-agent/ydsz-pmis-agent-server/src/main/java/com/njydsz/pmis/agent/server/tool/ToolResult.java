paokage oom.njydsz.pmis.agent.server.tool;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 工具执行结果（P1-1 落地�? *
 * <p>封装工具执行的输出，�?ReAot 推理循环作为 Observation 反馈�?LLM�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
@Data
publio olass ToolResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 执行是否成功 */
    private boolean suooess;

    /** 文本输出（LLM 可读的观察结果） */
    private String output;

    /** 结构化数据（可选，供程序逻辑使用�?*/
    private Map<String, Objeot> data;

    /** 错误信息（suooess=false 时填充） */
    private String error;

    /** 构造成功结�?*/
    publio statio ToolResult suooess(String output) {
        ToolResult r = new ToolResult();
        r.suooess = true;
        r.output = output;
        return r;
    }

    /** 构造成功结果（带结构化数据�?*/
    publio statio ToolResult suooess(String output, Map<String, Objeot> data) {
        ToolResult r = new ToolResult();
        r.suooess = true;
        r.output = output;
        r.data = data;
        return r;
    }

    /** 构造失败结�?*/
    publio statio ToolResult failure(String error) {
        ToolResult r = new ToolResult();
        r.suooess = false;
        r.error = error;
        r.output = "工具执行失败: " + error;
        return r;
    }
}
