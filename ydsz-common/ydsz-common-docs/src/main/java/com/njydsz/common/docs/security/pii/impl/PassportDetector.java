package com.njydsz.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.security.pii.PiiDetector;

/**
 * 护照号码检测器（中国因私护照）
 *
 * <p>检测中国因私护照号码（E/G 开头 + 8 位数字，共 9 位）。
 *
 * <p><b>注意：</b>仅匹配格式不做校验位验证（护照号无公开校验算法）， 因此置信度只给 0.7；且纯数字前导 E/G 的正则可能命中业务编码、
 * 会员编号等，存在误报风险，建议结合"护照"关键词上下文二次过滤。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class PassportDetector implements PiiDetector {

  /** 中国因私护照号正则：E/G 开头 + 8 位数字 */
  private static final Pattern PASSPORT_PATTERN = Pattern.compile("\\b[EGeg]\\d{8}\\b");

  /**
   * 扫描全文中的疑似中国因私护照号。
   *
   * <p>无法做真实校验位验证，仅凭首字母 + 8 位数字判定； 同时要求文本中出现"护照""passport"等关键词以提升准确率 为可选项（未强制），避免无上下文的纯编码误报。
   *
   * <p>返回的下标基于预处理后的文本。
   *
   * @param content 文档内容；为 {@code null} 或其 text 为 {@code null} 时返回空列表，不抛异常
   * @return PII 发现列表；无命中时返回空列表而非 {@code null}
   */
  @Override
  public List<PiiFinding> detect(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return List.of();
    }

    String text = content.getText();
    List<PiiFinding> findings = new ArrayList<>(16);
    Matcher matcher = PASSPORT_PATTERN.matcher(text);

    while (matcher.find()) {
      String matched = matcher.group();
      // 检查上下文是否有"护照"相关关键词
      boolean hasPassportContext = containsPassportKeyword(text, matcher.start(), matcher.end());
      double confidence = hasPassportContext ? 0.7 : 0.4;

      findings.add(
          PiiFinding.builder()
              .type(PiiType.PASSPORT)
              .maskedValue(mask(matched))
              .startIndex(matcher.start())
              .endIndex(matcher.end())
              .confidence(confidence)
              .build());
    }

    return findings;
  }

  /**
   * 声明本检测器负责的 PII 类别。
   *
   * @return 恒为 {@link PiiType#PASSPORT}
   */
  @Override
  public PiiType getSupportedType() {
    return PiiType.PASSPORT;
  }

  /**
   * 对护照号做保留首尾脱敏。
   *
   * <p>保留首字母（E/G，区分护照类型）与末 2 位，中间全部遮蔽。 如 {@code E12345678} 脱敏为 {@code E******78}。
   *
   * @param matchedText 命中的护照号；为 {@code null} 或不足 3 位时返回 {@code "****"}
   * @return 脱敏后的护照号串；输入异常时返回默认掩码
   */
  @Override
  public String mask(String matchedText) {
    if (matchedText == null || matchedText.length() < 3) {
      return "****";
    }
    return matchedText.charAt(0) + "******" + matchedText.substring(matchedText.length() - 2);
  }

  /**
   * 检查命中位置前后 30 字符内是否包含护照相关关键词。
   *
   * <p>用于在无校验位可用的场景下，通过上下文提升判断准确率， 降低纯格式匹配造成的误报。匹配不区分大小写。
   *
   * @param text 全文本
   * @param start 命中位置起始下标
   * @param end 命中位置结束下标
   * @return 存在关键词返回 {@code true}；上下文无关键词或临近文本边界也返回 {@code false}
   */
  private boolean containsPassportKeyword(String text, int start, int end) {
    int contextStart = Math.max(0, start - 30);
    int contextEnd = Math.min(text.length(), end + 30);
    String context = text.substring(contextStart, contextEnd).toLowerCase();
    return context.contains("护照")
        || context.contains("passport")
        || context.contains("traveldocument");
  }
}
