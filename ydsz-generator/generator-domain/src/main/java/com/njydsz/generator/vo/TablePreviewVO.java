package com.njydsz.generator.vo;

import com.njydsz.generator.entity.GenColumnMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表预览 VO（前端表选择展示）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TablePreviewVO {

  /** 表名。 */
  private String tableName;
  /** 表注释。 */
  private String comment;
  /** 列数量。 */
  private Integer columnCount;
  /** 列详情列表。 */
  private List<GenColumnMeta> columns;
}
