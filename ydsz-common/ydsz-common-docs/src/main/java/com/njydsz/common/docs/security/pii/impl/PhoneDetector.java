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
 * 手机号检测器
 *
 * <p><b>正则来源：</b>委托 {@link SensitiveUtil#scanWithPositions} 执行匹配， 与全系统 PII 扫描共享同一套正则，消除重复维护。
 *
 * <p>脱敏策略为"前 3 后 4"，与 {@link SensitiveUtil#phone} 一致。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class PhoneDetector implements PiiDetector {

  /**
   * 扫描全文中的手机号。
   *
   * <p>正则委托 {@link SensitiveUtil#scanWithPositions} 统一维护， 过滤出 {@link SensitiveType#PHONE} 类型的匹配。
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
      if (match.type() != SensitiveType.PHONE) {
        continue;
      }
      findings.add(
          PiiFinding.builder()
              .type(PiiType.PHONE)
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
   * @return 恒为 {@link PiiType#PHONE}
   */
  @Override
  public PiiType getSupportedType() {
    return PiiType.PHONE;
  }

  /**
   * 遮蔽手机号中间 4 位，保留前 3 位与后 4 位。
   *
   * <p>与 {@link SensitiveUtil#phone} 脱敏策略一致。
   *
   * @param matchedText 命中的原始手机号，可为 {@code null}
   * @return 形如 {@code 138****8000} 的脱敏串； 入参为 {@code null} 或不足 8 位时返回 {@code "****"}
   */
  @Override
  public String mask(String matchedText) {
    return SensitiveUtil.maskPhone(matchedText);
  }
}
