package com.njydsz.common.docs.domain;

import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.enums.PiiType;
import lombok.Builder;
import lombok.Data;

/**
 * PII 发现结果
 *
 * <p>表示在文档中检测到的敏感信息项，支持多种格式的定位模型。
 *
 * <p><b>定位模型：</b>
 *
 * <ul>
 *   <li>文本定位（{@link #startIndex} / {@link #endIndex}）：适用于纯文本格式
 *   <li>二进制定位（{@link #binaryLocation}）：适用于 PDF/DOCX/XLSX 等结构化格式
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class PiiFinding {

  /** PII 类型 */
  private PiiType type;

  /** 匹配到的原文（已脱敏，如 138****1234） */
  private String maskedValue;

  /** 在全文中的起始位置（纯文本格式适用） */
  private int startIndex;

  /** 在全文中的结束位置（纯文本格式适用） */
  private int endIndex;

  /** 所在页码（如可确定） */
  private Integer pageNumber;

  /** 置信度（0-1） */
  private double confidence;

  /** 文档格式，用于定位模型选择 */
  private DocumentFormat documentFormat;

  /**
   * 结构化文档（PDF/DOCX/XLSX）中的精确定位信息。
   *
   * <p>当检测对象为二进制格式时，此字段携带页面坐标或单元格定位； 纯文本场景下为 {@code null}，此时使用 {@link #startIndex}/{@link
   * #endIndex}。
   */
  private BinaryLocation binaryLocation;

  /**
   * 二进制文档中的坐标定位。
   *
   * <p>用于 PDF 页面区域、DOCX 段落范围、Excel 单元格等场景。
   */
  @Data
  @Builder
  public static class BinaryLocation {

    /** 页码（PDF/DOCX）或 Sheet 索引（XLSX，从 0 开始） */
    private int pageIndex;

    /** 页面内 X 坐标（PDF）或段落索引（DOCX） */
    private double x;

    /** 页面内 Y 坐标（PDF）或字符偏移（DOCX） */
    private double y;

    /** 区域宽度（PDF）或段落数（DOCX） */
    private double width;

    /** 区域高度（PDF）或字符长度（DOCX） */
    private double height;

    /** 行号（Excel，从 0 开始） */
    private Integer row;

    /** 列号（Excel，从 0 开始） */
    private Integer column;
  }
}
