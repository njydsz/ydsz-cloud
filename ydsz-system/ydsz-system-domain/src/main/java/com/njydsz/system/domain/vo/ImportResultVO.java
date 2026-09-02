package com.njydsz.system.domain.vo;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 导入结果 VO
 *
 * <p>封装 Excel 导入的结果统计信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class ImportResultVO {

  /** 总行数 */
  private int totalCount;

  /** 成功数 */
  private int successCount;

  /** 失败数 */
  private int failCount;

  /** 跳过的行数（重复或无效数据） */
  private int skipCount;

  /** 错误信息列表 */
  private List<String> errors;

  /** 导入结果消息 */
  private String message;
}
