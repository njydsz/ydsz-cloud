package com.njydsz.pmis.literule.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则批量分类调整请求体 DTO
 *
 * <p>用于 {@code /rules/batch-category} 接口，批量调整规则分类。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Schema(description = "规则批量分类调整请求体")
public class RuleBatchCategoryDTO {

    /**
     * 规则编码列表
     */
    @Schema(description = "规则编码列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{validation.project.msg_e5c6d7e5}")
    private List<String> ruleCodes;

    /**
     * 目标分类
     */
    @Schema(description = "目标分类", requiredMode = Schema.RequiredMode.REQUIRED, example = "finance/credit")
    @NotBlank(message = "{validation.project.msg_b2f3a4b3}")
    private String category;
}
