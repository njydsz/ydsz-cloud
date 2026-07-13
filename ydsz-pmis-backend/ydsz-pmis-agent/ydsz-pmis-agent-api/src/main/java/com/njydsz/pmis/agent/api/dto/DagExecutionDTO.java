package com.njydsz.pmis.agent.api.dto;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * DAG 编排请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Schema(description = "DAG 编排请求")
public class DagExecutionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "DSL 内容不能为空")
    @Schema(description = "YAML DSL 内容", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "name: project-analysis\nnodes:\n  analyze:\n    agent-type: CHAT\n    prompt: \"分析项目\"\n  report:\n    agent-type: CHAT\n    prompt: \"生成报告\"\n    input-from: analyze\nedges:\n  report:\n    - analyze")
    private String dsl;

    @NotBlank(message = "用户输入不能为空")
    @Schema(description = "用户输入", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userInput;

    public String getDsl() { return dsl; }
    public void setDsl(String dsl) { this.dsl = dsl; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
}
