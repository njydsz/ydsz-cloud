package com.njydsz.generator.query;

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
  private Long datasourceId;
  /** 模板分组 ID。 */
  private Long templateGroupId;
  /** 表名。 */
  private String tableName;
  /** 输出目录。 */
  private String outputDir;
  /** 冲突策略（SKIP/OVERRIDE/MERGE），可空，默认 SKIP。 */
  private String conflictStrategy;
  /** 触发人，可空。 */
  private String triggeredBy;
}
