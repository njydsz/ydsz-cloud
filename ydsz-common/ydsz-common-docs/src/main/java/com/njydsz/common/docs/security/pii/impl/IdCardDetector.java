package com.njydsz.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.safe.sensitive.SensitiveType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * 身份证号检测器
 *
 * <p><b>正则来源：</b>委托 {@link SensitiveUtil#scanWithPositions} 执行匹配， 与全系统 PII 扫描共享同一套正则，消除重复维护。
 *
 * <p>脱敏策略为"前 6 后 4"（保留行政区划码以供区域分析）， 与 {@link SensitiveUtil#idCard} 的"中间 8 位遮蔽"策略不同，
 * 这是文档场景的特殊需求——泄露事件分析需要保留地区信息以快速定位影响范围。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class IdCardDetector implements PiiDetector {

  /**
   * 扫描全文中的 18 位公民身份证号。
   *
   * <p>正则委托 {@link SensitiveUtil#scanWithPositions} 统一维护， 过滤出 {@link SensitiveType#ID_CARD} 类型的匹配。
   *
   * <p>返回的下标基于预处理后的文本，脱敏时须使用同一份文本。
   *
   * @param content 文档内容；为 {@code null} 或其 text 为 {@code null} 时返回空列表，不抛异常
   * @return PII 发现列表，含脱敏值与字符下标区间；无命中时返回空列表而非 {@code null}
   */
  @Override
  public List<PiiFinding> detect(DocumentContent content) {
    if (content == null || content.getText() == null) {
      return List.of();
    }

    String text = content.getText();
    List<PiiFinding> findings = new ArrayList<>(16);

    for (SensitiveUtil.PiiMatch match : SensitiveUtil.scanWithPositions(text)) {
      if (match.type() != SensitiveType.ID_CARD) {
        continue;
      }
      findings.add(
          PiiFinding.builder()
              .type(PiiType.ID_CARD)
              .maskedValue(mask(match.rawValue()))
              .startIndex(match.startIndex())
              .endIndex(match.endIndex())
              .confidence(0.98)
              .build());
    }

    return findings;
  }

  /**
   * 声明本检测器负责的 PII 类别，供组合检测器按类型开关与归类。
   *
   * @return 恒为 {@link PiiType#ID_CARD}
   */
  @Override
  public PiiType getSupportedType() {
    return PiiType.ID_CARD;
  }

  /**
   * 对身份证号做保留首尾脱敏，保留前 6 位行政区划码与后 4 位。
   *
   * <p>与 {@link SensitiveUtil#idCard} 的区别：SensitiveUtil 遮蔽中间 8 位 （前 3 后 5），此处保留前 6
   * 位（行政区划码），供文档泄露分析时 快速定位影响范围。这是文档场景特有的脱敏策略。
   *
   * @param matchedText 命中的原始身份证号；为 {@code null} 或不足 10 位时返回 {@code "****"}
   * @return 形如 {@code 110101********1234} 的脱敏串；输入异常时返回默认掩码
   */
  @Override
  public String mask(String matchedText) {
    if (matchedText == null || matchedText.length() < 10) {
      return "****";
    }
    return matchedText.substring(0, 6)
        + "********"
        + matchedText.substring(matchedText.length() - 4);
  }
}
