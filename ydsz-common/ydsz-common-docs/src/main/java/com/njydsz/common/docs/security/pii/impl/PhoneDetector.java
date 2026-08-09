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
 * 手机号检测器
 * <p>
 * 检测中国大陆手机号（11 位）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class PhoneDetector implements PiiDetector {

    /** 中国大陆手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /**
     * 扫描全文中的中国大陆手机号。
     *
     * <p>正则用前后数字断言（{@code (?<!\d)} / {@code (?!\d)}）包夹，
     * 确保只命中<b>独立</b>的 11 位号码，避免从更长的数字串（如 18 位身份证、
     * 银行卡号）中截出一段误报为手机号。
     *
     * <p>置信度统一给 0.9 而非满分：号段规则 {@code 1[3-9]} 较宽松，
     * 部分 11 位业务流水号仍可能落入该模式，留出人工复核空间。
     *
     * <p><b>不识别的形式：</b>带 {@code +86} 国际前缀、带分隔符
     * （{@code 138-0013-8000}）、以及固话号码，均不会命中。
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
        Matcher matcher = PHONE_PATTERN.matcher(text);

        while (matcher.find()) {
            String matched = matcher.group();
            findings.add(PiiFinding.builder()
                    .type(PiiType.PHONE)
                    .maskedValue(mask(matched))
                    .startIndex(matcher.start())
                    .endIndex(matcher.end())
                    .confidence(0.9)
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
     * 对手机号做前 3 后 4 的行业惯例脱敏。
     *
     * <p>保留号段前缀便于运营商维度的统计，保留后 4 位便于用户自行辨认是本人号码，
     * 中间 4 位遮蔽后已无法还原完整号码。
     *
     * @param matchedText 命中的原始号码；为 {@code null} 或长度小于 7 时按不可安全脱敏处理
     * @return 形如 {@code 138****8000} 的脱敏串；输入过短时统一返回 {@code "****"}
     */
    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 7) {
            return "****";
        }
        return matchedText.substring(0, 3) + "****" + matchedText.substring(matchedText.length() - 4);
    }
}
