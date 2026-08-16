package com.njydsz.common.docs.enums;

/**
 * 解析输出轮廓，约束 {@link com.njydsz.common.docs.domain.DocumentContent} 中各字段的填充程度。
 *
 * <p>不同业务场景对解析结果的结构化程度要求不同：纯文本检索只需 {@link #TEXT_ONLY}， 知识库构建需要 {@link #STRUCTURED}（含分节与表格），而归档场景才需要
 * {@link #FULL}（含图片）。
 *
 * <p>通过显式声明输出轮廓，调用方可对非空字段省略判空保护，解析器实现也可按需跳过 高开销操作（如图片提取），避免"全默认填充"造成的性能浪费。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ParseProfile {

  /**
   * 仅输出纯文本。
   *
   * <p>{@link com.njydsz.common.docs.domain.DocumentContent#getText()} 必有值， {@code sections}、{@code
   * tables}、{@code images} 为空列表。 适用于全文检索、PII 检测等仅需文本内容的场景。
   */
  TEXT_ONLY,

  /**
   * 输出文本与结构化信息（分节、表格）。
   *
   * <p>{@code text}、{@code sections}、{@code tables} 按实际文档结构填充， {@code images}
   * 为空列表。适用于知识库构建、文档摘要等需要段落语义的场景。
   */
  STRUCTURED,

  /**
   * 完整输出（含图片元数据）。
   *
   * <p>所有字段按实际文档内容填充，包括图片列表。 仅适用于归档、数字资产管理等需要完整文档信息的场景。 注意：图片提取会显著增加解析耗时与内存占用。
   */
  FULL
}
