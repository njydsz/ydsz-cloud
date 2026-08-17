package com.njydsz.system.domain.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 导入结果 VO
 *
 * <p>封装 Excel 导入的结果统计信息。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Data
@Builder
@Schema(description = "导入结果")
public class ImportResult {

  /** 总行数 */
  @Schema(description = "总行数")
  private int totalCount;

  /** 成功数 */
  @Schema(description = "成功数")
  private int successCount;

  /** 失败数 */
  @Schema(description = "失败数")
  private int failCount;

  /** 跳过的行数（重复或无效数据） */
  @Schema(description = "跳过行数")
  private int skipCount;

  /** 错误信息列表 */
  @Schema(description = "错误信息列表")
  private List<String> errors;

  /** 导入结果消息 */
  @Schema(description = "结果消息")
  private String message;
}
