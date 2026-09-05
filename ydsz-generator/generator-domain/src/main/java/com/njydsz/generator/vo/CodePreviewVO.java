package com.njydsz.generator.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码预览 VO（模板渲染结果展示）。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodePreviewVO {

  /** 文件名。 */
  private String fileName;
  /** 文件路径。 */
  private String filePath;
  /** 代码内容。 */
  private String content;
  /** 是否存在冲突（文件已存在）。 */
  private Boolean conflict;
}
