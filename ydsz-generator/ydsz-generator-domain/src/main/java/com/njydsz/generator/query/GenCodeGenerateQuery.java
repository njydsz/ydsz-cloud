package com.njydsz.generator.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码正式生成请求 Query。
 *
 * <p>承载正式生成代码所需的全部参数，避免 Feign 接口方法参数超限（YDIZ-OOP-002）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenCodeGenerateQuery {

  /** 数据源 ID。 */
  @NotBlank(message = "{generator.codeGenerate.datasourceId.notblank}")
  private String datasourceId;
  /** 模板分组 ID。 */
  @NotBlank(message = "{generator.codeGenerate.templateGroupId.notblank}")
  private String templateGroupId;
  /** 表名。 */
  @NotBlank(message = "{generator.codeGenerate.tableName.notblank}")
  private String tableName;
  /** 输出目录。 */
  @NotBlank(message = "{generator.codeGenerate.outputDir.notblank}")
  private String outputDir;
  /** 冲突策略（SKIP/OVERRIDE/MERGE），可空，默认 SKIP。 */
  @Pattern(regexp = "SKIP|OVERRIDE|MERGE", message = "{generator.codeGenerate.conflictStrategy.pattern}")
  private String conflictStrategy;
  /** 触发人，可空。 */
  private String triggeredBy;
}
