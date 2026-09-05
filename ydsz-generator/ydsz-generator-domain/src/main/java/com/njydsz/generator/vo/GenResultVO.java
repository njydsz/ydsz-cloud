package com.njydsz.generator.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成结果 VO。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenResultVO {

  /** 任务 ID。 */
  private Long historyId;
  /** 生成文件数量。 */
  private Integer fileCount;
  /** 成功文件数量。 */
  private Integer successCount;
  /** 跳过文件数量。 */
  private Integer skipCount;
  /** 失败文件数量。 */
  private Integer failCount;
}
