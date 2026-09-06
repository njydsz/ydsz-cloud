package com.njydsz.common.excel.api.result;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Excel 导入结果基类（P1-5：公共能力下沉）。
 *
 * <p>封装批量导入 Excel 后的统计信息，各模块的导入结果 VO/DTO 可继承或组合本类，
 * 避免重复定义 totalCount / successCount / failCount 等字段。
 *
 * <p><b>使用方式：</b>
 *
 * <ul>
 *   <li>继承：{@code public class ImportResultVO extends ExcelImportResult}</li>
 *   <li>组合：将 {@code ExcelImportResult} 作为基类字段委托调用</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ExcelImportResult {

  /** 导入总数 */
  private int totalCount;

  /** 成功导入数 */
  private int successCount;

  /** 失败数 */
  private int failCount;

  /** 跳过的行数（重复或无效数据） */
  @Builder.Default
  private int skipCount = 0;

  /** 错误信息列表 */
  @Builder.Default
  private List<String> errors = new ArrayList<>(16);

  /**
   * 构建成功结果（无失败）。
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @return 导入结果实例
   */
  public static ExcelImportResult success(int totalCount, int successCount) {
    return ExcelImportResult.builder()
        .totalCount(totalCount)
        .successCount(successCount)
        .failCount(totalCount - successCount)
        .build();
  }

  /**
   * 构建含失败的导入结果。
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @param failCount 失败数
   * @param errors 错误信息列表
   * @return 导入结果实例
   */
  public static ExcelImportResult partial(int totalCount, int successCount, int failCount,
      List<String> errors) {
    return ExcelImportResult.builder()
        .totalCount(totalCount)
        .successCount(successCount)
        .failCount(failCount)
        .errors(errors)
        .build();
  }
}
