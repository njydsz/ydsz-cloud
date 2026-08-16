package com.njydsz.common.docs.preprocess.impl;

import java.text.Normalizer;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.preprocess.DocumentPreprocessor;

/**
 * 文本归一化预处理器
 *
 * <p>对文档文本进行 Unicode 归一化（NFKC）、空白字符标准化和编码统一。
 *
 * <p><b>处理步骤：</b>
 *
 * <ul>
 *   <li>Unicode NFKC 归一化（兼容分解 + 规范组合）
 *   <li>全角空格转半角
 *   <li>连续空格压缩为单空格
 *   <li>Windows 换行符 (\r\n) 转换为 \n
 *   <li>连续空行压缩为单空行
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class TextNormalizer implements DocumentPreprocessor {

  /**
   * 对全文与各分节执行 Unicode 归一化与空白压缩。
   *
   * <p>作为流水线首棒（order=10）运行，为后续的清洗、PII 正则匹配提供稳定的字符基线。 NFKC 会把全角字母数字、连字、上下标等兼容字符折叠为标准形式， 使 {@code
   * １３８…} 这类全角数字也能被手机号正则命中—— 若不做这步，攻击者可用全角字符轻易绕过敏感信息检测。
   *
   * <p><b>会改变字符偏移量：</b>压缩空格与空行后，文本长度缩短， 原有的下标位置全部失效。因此 PII 检测必须在本预处理<b>之后</b>执行， 否则 {@code
   * PiiFinding} 中记录的 startIndex/endIndex 会错位，导致脱敏切错位置。
   *
   * <p><b>就地修改入参：</b>直接改写传入对象的 text、totalChars 与各分节内容并返回同一引用， 而非返回副本。调用方若需保留原始文本，必须自行在调用前深拷贝。
   * 该对象非线程安全，不可在多线程间共享同一 {@link DocumentContent}。
   *
   * @param content 待归一化的文档内容；为 {@code null} 或其 text 为 {@code null} 时原样返回，不抛异常
   * @return 与入参同一引用（已就地归一化）；入参为 {@code null} 时返回 {@code null}
   */
  @Override
  public DocumentContent process(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return content;
    }

    String text = content.getText();

    // Unicode NFKC 归一化
    text = Normalizer.normalize(text, Normalizer.Form.NFKC);

    // 全角空格转半角
    text = text.replace('\u3000', ' ');

    // Windows 换行符统一
    text = text.replace("\r\n", "\n");
    text = text.replace('\r', '\n');

    // 连续空格压缩为单空格（保留换行）
    text = text.replaceAll("[ \\t]+", " ");

    // 连续空行压缩为单空行
    text = text.replaceAll("\\n{3,}", "\n\n");

    // 去除首尾空白
    text = text.strip();

    // 同步更新分节内容
    if (content.getSections() != null) {
      content
          .getSections()
          .forEach(
              s -> {
                if (s.getContent() != null) {
                  s.setContent(s.getContent().strip());
                }
              });
    }

    content.setText(text);
    content.setTotalChars(text.length());
    return content;
  }

  /**
   * 返回本处理器的稳定标识，用于流水线日志与指标打点。
   *
   * @return 恒为 {@code "text-normalizer"}
   */
  @Override
  public String getName() {
    return "text-normalizer";
  }

  /**
   * 声明在预处理流水线中的执行序号。
   *
   * <p>取 10 使其<b>先于</b>清洗器（20）与分块器（30）执行： 字符形态必须先统一，后续基于正则的清洗才有确定的匹配基准， 分块也才能在稳定的文本上计算长度与重叠。
   *
   * @return 恒为 10，数值越小越先执行
   */
  @Override
  public int getOrder() {
    return 10;
  }
}
