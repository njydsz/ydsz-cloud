package com.njydsz.common.docs.domain;

import lombok.Builder;
import lombok.Data;

import com.njydsz.common.docs.enums.ParseMode;
import com.njydsz.common.docs.enums.ParseProfile;

/**
 * 文档解析选项
 *
 * <p>控制解析行为的可配置参数。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
public class ParseOptions {

  /** 解析模式，默认完整模式 */
  @Builder.Default private ParseMode mode = ParseMode.FULL;

  /** 解析输出轮廓，控制返回数据的结构化程度 */
  @Builder.Default private ParseProfile profile = ParseProfile.STRUCTURED;

  /** 最大文件大小（字节），超过则拒绝解析，默认 50MB */
  @Builder.Default private long maxFileSize = 50L * 1024 * 1024;

  /** 解析超时时间（毫秒），默认 60 秒 */
  @Builder.Default private long timeoutMs = 60_000L;

  /** 是否提取表格 */
  @Builder.Default private boolean extractTables = true;

  /** 是否提取图片元数据 */
  @Builder.Default private boolean extractImages = true;

  /** 是否提取元数据 */
  @Builder.Default private boolean extractMetadata = true;

  /** 最大页数限制（超过则截断），0 表示不限制 */
  @Builder.Default private int maxPages = 0;

  /** 编码（仅文本文件有效），null 表示自动检测 */
  private String charset;

  // ==================== 语义化工厂方法（E-1） ====================

  /**
   * 创建默认解析选项（全功能：文本 + 表格 + 图片 + 元数据）。
   *
   * @return 默认解析选项
   */
  public static ParseOptions defaults() {
    return builder().build();
  }

  /**
   * 创建纯文本解析选项（跳过表格 / 图片 / 元数据，仅保留纯文本与分节）。
   *
   * <p>适用于全文检索、PII 检测等仅需文本内容的场景。
   *
   * @return 纯文本解析选项
   */
  public static ParseOptions textOnly() {
    return builder()
        .extractTables(false)
        .extractImages(false)
        .extractMetadata(false)
        .build();
  }

  /**
   * 创建 RAG 友好解析选项：保留完整结构（含表格），跳过图片与元数据。
   *
   * <p>下游使用 {@code maxChunkSize} 与 {@code chunkOverlap} 做切片。
   *
   * @param maxChunkSize 最大分块字符数
   * @param chunkOverlap 相邻分块重叠字符数
   * @return RAG 友好解析选项
   */
  public static ParseOptions forRag(int maxChunkSize, int chunkOverlap) {
    return builder()
        .extractTables(true)
        .extractImages(false)
        .extractMetadata(true)
        .build();
  }

  /**
   * 创建快速解析选项（{@link ParseMode#FAST}）：跳过耗时步骤，仅保留核心文本。
   *
   * @return 快速解析选项
   */
  public static ParseOptions fast() {
    return builder()
        .mode(ParseMode.FAST)
        .extractTables(false)
        .extractImages(false)
        .build();
  }
}
