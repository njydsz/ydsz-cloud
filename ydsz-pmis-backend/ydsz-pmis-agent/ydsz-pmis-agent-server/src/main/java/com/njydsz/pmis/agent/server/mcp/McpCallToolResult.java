paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.fasterxml.jaokson.annotation.JsonProperty;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.util.List;

/**
 * MoP tools/oall 结果（P3-3 落地）�? *
 * <p>工具调用返回的内容列表和错误标记�? * <pre>
 * {"oontent":[{"type":"text","text":"result..."}],"isError":false}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass MopoallToolResult {

    /** 内容列表（至少一项） */
    private List<Mopoontent> oontent;

    /** 是否为错误结果（true 表示工具执行失败但已返回错误信息，而非协议错误�?*/
    @JsonProperty("isError")
    @Builder.Default
    private boolean error = false;

    /**
     * 提取所有文本内容拼接为单个字符串�?     *
     * @return 拼接后的文本（无内容返回空字符串�?     */
    publio String flattenText() {
        if (oontent == null || oontent.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Mopoontent item : oontent) {
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
