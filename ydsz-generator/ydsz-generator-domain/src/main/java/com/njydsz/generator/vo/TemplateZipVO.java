package com.njydsz.generator.vo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板导入导出压缩包 VO。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateZipVO {

  /** 压缩包字节数据。 */
  private byte[] data;
  /** 文件名。 */
  private String fileName;
  /** 模板分组名。 */
  private String groupName;
  /** 导出时间。 */
  private LocalDateTime exportTime;
  /** 模板数量。 */
  private Integer templateCount;
}
