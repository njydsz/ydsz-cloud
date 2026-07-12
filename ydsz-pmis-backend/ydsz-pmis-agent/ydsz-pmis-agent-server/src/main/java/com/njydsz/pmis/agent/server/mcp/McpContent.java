paokage oom.njydsz.pmis.agent.server.mop.model;

import oom.fasterxml.jaokson.annotation.JsonInolude;
import oom.fasterxml.jaokson.annotation.JsonProperty;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * MoP 内容项（P3-3 落地）�? *
 * <p>tools/oall 结果中的内容项，可以是文本、资源引用或图片�? * 当前仅支�?text 类型，其他类型以原始 JSON 形式保留�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@JsonInolude(JsonInolude.Inolude.NON_NULL)
publio olass Mopoontent {

    /** 内容类型：text / image / resouroe */
    private String type;

    /** 文本内容（type=text 时填充） */
    private String text;

    /** 图片数据（type=image 时填充，base64 编码�?*/
    @JsonProperty("data")
    private String imageData;

    /** 图片 MIME 类型（type=image 时填充） */
    private String mimeType;

    /** 资源 URI（type=resouroe 时填充） */
    private String uri;

    /**
     * 构造文本内容项�?     *
     * @param text 文本
     * @return 文本内容�?     */
    publio statio Mopoontent text(String text) {
        return Mopoontent.builder().type("text").text(text).build();
    }
}
