package com.njydsz.common.docs.security.pii.impl;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.security.pii.PiiDetector;
import com.njydsz.common.safe.sensitive.SensitiveType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 邮箱地址检测器
 *
 * <p><b>正则来源：</b>委托 {@link SensitiveUtil#scanWithPositions} 执行匹配， 与全系统 PII 扫描共享同一套正则，消除重复维护。
 *
 * <p>脱敏策略为"首字母 + *** + 完整域名"，与 {@link SensitiveUtil#email} 策略接近但保留了完整域名供安全审计与泄露溯源。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class EmailDetector implements PiiDetector {

  /**
   * 扫描全文中的邮箱地址。
   *
   * <p>正则委托 {@link SensitiveUtil#scanWithPositions} 统一维护， 过滤出 {@link SensitiveType#EMAIL} 类型的匹配。
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
    List<PiiFinding> findings = new ArrayList<>();

    for (SensitiveUtil.PiiMatch match : SensitiveUtil.scanWithPositions(text)) {
      if (match.type() != SensitiveType.EMAIL) {
        continue;
      }
      findings.add(
          PiiFinding.builder()
              .type(PiiType.EMAIL)
              .maskedValue(mask(match.rawValue()))
              .startIndex(match.startIndex())
              .endIndex(match.endIndex())
              .confidence(0.95)
              .build());
    }

    return findings;
  }

  /**
   * 声明本检测器负责的 PII 类别，供组合检测器按类型开关与归类。
   *
   * @return 恒为 {@link PiiType#EMAIL}
   */
  @Override
  public PiiType getSupportedType() {
    return PiiType.EMAIL;
  }

  /**
   * 遮蔽邮箱本地部分，<b>完整保留域名</b>。
   *
   * <p>只留本地部分首字母，其余以三个星号替代。域名不脱敏是有意为之： 组织归属（如 {@code @company.com}）对安全审计与泄露溯源有价值， 且域名本身不属于个人标识信息。
   *
   * <p>本地部分只有 1 个字符时，连首字母一并遮蔽， 否则脱敏后等同于暴露了完整本地部分。
   *
   * @param matchedText 命中的原始邮箱地址，可为 {@code null}
   * @return 形如 {@code z***@example.com} 的脱敏串； 入参为 {@code null} 或不含 {@code @}（含以 {@code @} 开头）时返回
   *     {@code "****"}
   */
  @Override
  public String mask(String matchedText) {
    if (matchedText == null) {
      return "****";
    }
    int atIndex = matchedText.indexOf('@');
    if (atIndex <= 0) {
      return "****";
    }
    String localPart = matchedText.substring(0, atIndex);
    String domain = matchedText.substring(atIndex);
    if (localPart.length() <= 1) {
      return "*" + domain;
    }
    return localPart.charAt(0) + "***" + domain;
  }
}
