package com.njydsz.userinfo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户批量导入结果 DTO
 *
 * <p>封装批量导入的执行结果，包含成功数、失败数、失败明细等详细信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResultDTO {

  /** 导入总数 */
  private int totalCount;

  /** 成功导入数 */
  private int successCount;

  /** 失败数 */
  private int failCount;

  /** 失败明细列表（行号 + 原因） */
  private String failDetails;

  /**
   * 创建成功结果
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
