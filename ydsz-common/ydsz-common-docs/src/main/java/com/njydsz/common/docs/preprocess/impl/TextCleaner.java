package com.njydsz.common.docs.preprocess.impl;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.preprocess.DocumentPreprocessor;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 文本清洗预处理器
 *
 * <p>移除文档文本中的无关字符和噪声。
 *
 * <p><b>处理步骤：</b>
 *
 * <ul>
 *   <li>移除控制字符（除换行和制表符外）
 *   <li>移除 BOM 标记
 *   <li>移除 PDF 提取常见的页码标记
 *   <li>移除不可见的零宽字符
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class TextCleaner implements DocumentPreprocessor {

  /** 控制字符（保留 \n \t） */
  private static final Pattern CONTROL_CHARS =
      Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

  /** BOM 标记 */
  private static final Pattern BOM = Pattern.compile("\uFEFF");

  /** 零宽字符 */
  private static final Pattern ZERO_WIDTH = Pattern.compile("[\u200B-\u200D]");

  /** PDF 页码标记（如 "- 1 -" 或 "Page 1 of 10"） */
  private static final Pattern PAGE_NUMBER =
      Pattern.compile(
          "(?m)^\\s*[-–]?\\s*\\d+\\s*[-–]?\\s*$|" + "(?i)page\\s+\\d+\\s*(of\\s+\\d+)?\\s*$");

  /**
   * 剔除全文中的不可见字符与 PDF 抽取残留的页码行。
   *
   * <p>按 BOM → 零宽字符 → 控制字符 → 页码 的固定次序替换。 <b>零宽字符必须先于其他步骤清除</b>：{@code U+200B~200D} 可被插入到敏感串中间 （如
   * {@code 138<ZWSP>0013<ZWSP>8000}），不先剥离就会让后续 PII 正则整体失配， 这是一条典型的检测绕过路径。控制字符则会污染日志与下游存储。
   *
   * <p>页码行清理属于<b>启发式</b>，存在误删风险：正文中独占一行的纯数字 （如编号列表、数据表中孤立的一行数字）会被一并移除。 权衡后仍保留该规则，因为 PDF
   * 逐页抽取产生的页码噪声对检索质量影响更大。
   *
   * <p><b>只处理 text，不同步 sections：</b>与 {@link TextNormalizer} 不同， 本处理器不修改分节内容，故清洗后全文与分节文本会出现不一致，
   * 依赖分节的下游需自行留意。同时字符偏移量会变化，PII 检测须在其后执行。
   *
   * <p>就地修改入参并返回同一引用，非线程安全。
   *
   * @param content 待清洗的文档内容；为 {@code null} 或其 text 为 {@code null} 时原样返回，不抛异常
   * @return 与入参同一引用（text 与 totalChars 已更新）；入参为 {@code null} 时返回 {@code null}
   */
  @Override
  public DocumentContent process(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return content;
    }

    String text = content.getText();

    // 移除 BOM
    text = BOM.matcher(text).replaceAll("");

    // 移除零宽字符
    text = ZERO_WIDTH.matcher(text).replaceAll("");

    // 移除控制字符
    text = CONTROL_CHARS.matcher(text).replaceAll("");

    // 移除 PDF 页码标记
    text = PAGE_NUMBER.matcher(text).replaceAll("");

    content.setText(text);
    content.setTotalChars(text.length());
    return content;
  }

  /**
   * 返回本处理器的稳定标识，用于流水线日志与指标打点。
   *
   * @return 恒为 {@code "text-cleaner"}
   */
  @Override
  public String getName() {
    return "text-cleaner";
  }

  /**
   * 声明在预处理流水线中的执行序号。
   *
   * <p>取 20，夹在归一化（10）与分块（30）之间： 需先由归一化统一字符形态，本步的正则才有确定语义； 又须早于分块，避免噪声字符被计入块长度、把页码噪声固化进各个分块。
   *
   * @return 恒为 20，数值越小越先执行
   */
  @Override
  public int getOrder() {
    return 20;
  }
}
