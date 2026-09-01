package com.njydsz.literule.api.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 规则导入请求体 DTO
 *
 * <p>用于 {@code /rules/import} 接口，批量导入规则定义。
 *
 * <p>注意：{@code rules} 保留 {@code List<Map<String, Object>>} 形式，因为每条规则的字段 由前端导出格式决定，需通过 {@code
 * objectMapper.convertValue} 转为 {@link com.njydsz.literule.domain.api.RuleDefinitionDTO}，且导入时容错（单条失败跳过）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "规则导入请求体")
public class RuleImportDTO {

  /** 待导入的规则列表（每条为规则定义的 JSON 对象） */
  @Schema(description = "待导入的规则列表（每条为规则定义的 JSON 对象）")
  private List<Map<String, Object>> rules;
}
