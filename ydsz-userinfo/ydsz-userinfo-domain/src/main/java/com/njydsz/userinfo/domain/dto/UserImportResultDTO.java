package com.njydsz.userinfo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.excel.api.result.ExcelImportResult;

/**
 * 用户批量导入结果 DTO。
 *
 * <p>封装批量导入的执行结果，继承通用 {@link ExcelImportResult} 基类。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserImportResultDTO extends ExcelImportResult {

  /** 失败明细列表（行号 + 原因） */
  private String failDetails;

  /**
   * 创建导入结果（兼容原有静态工厂）。
   *
   * @param totalCount 总数
   * @param successCount 成功数
   * @param failCount 失败数
   * @param failDetails 失败详情
   * @return 导入结果 DTO
   */
  public static UserImportResultDTO of(
      int totalCount, int successCount, int failCount, String failDetails) {
    return UserImportResultDTO.builder()
        .totalCount(totalCount)
        .successCount(successCount)
        .failCount(failCount)
        .failDetails(failDetails)
        .build();
  }
}
