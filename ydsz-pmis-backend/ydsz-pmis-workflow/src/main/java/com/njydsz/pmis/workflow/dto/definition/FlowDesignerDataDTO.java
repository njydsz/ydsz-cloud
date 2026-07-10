package com.njydsz.pmis.workflow.dto.definition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 流程设计器数据 DTO
 *
 * <p>P1-10: 由原 Map body 改造为强类型 DTO + JSR-303 校验。
 * designerData 为前端序列化好的 JSON 字符串，控制器层反序列化为 Map 后转交 service。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "流程设计器数据 DTO")
public class FlowDesignerDataDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设计器数据 JSON 字符串（含 nodes + edges，前端已序列化好） */
    @NotBlank(message = "{validation.workflow.msg_a8b9c0d7}")
    private String designerData;
}
