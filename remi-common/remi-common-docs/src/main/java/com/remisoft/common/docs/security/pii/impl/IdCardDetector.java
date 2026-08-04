package com.remisoft.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.remisoft.common.docs.domain.DocumentContent;
import com.remisoft.common.docs.domain.PiiFinding;
import com.remisoft.common.docs.enums.PiiType;
import com.remisoft.common.docs.security.pii.PiiDetector;

/**
 * 身份证号检测器
 * <p>
 * 检测 18 位和 15 位中国身份证号码，支持校验位验证。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component
public class IdCardDetector implements PiiDetector {

    /** 18 位身份证号正则 */
    private static final Pattern ID_CARD_18 = Pattern.compile(
            "\\b[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b");

    /** 15 位身份证号正则 */
    private static final Pattern ID_CARD_15 = Pattern.compile(
            "\\b[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}\\b");

    /**
     * 扫描全文中的 18 位与 15 位身份证号。
     *
     * <p>18 位号码在正则命中后<b>还要过校验位算法</b>，通过才记为发现，
     * 置信度 0.95；15 位号码<b>无校验位可验</b>，仅凭格式判定，故置信度只给 0.7。
     * 置信度差异是留给上层做阻断/告警分级的依据，不要一视同仁地处理。
     *
     * <p><b>可能重复命中：</b>18 位号码的前 15 位在特定情况下也可能满足 15 位正则，
     * 从而同一段文本产生两条 finding。上层若需去重，应按 startIndex 区间做合并。
     *
     * <p>返回的下标基于<b>预处理后</b>的文本，脱敏时须使用同一份文本，
     * 否则位置错位会切错内容。
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

        // 18 位身份证
        Matcher m18 = ID_CARD_18.matcher(text);
        while (m18.find()) {
            String matched = m18.group();
            if (validateIdCard18(matched)) {
                findings.add(PiiFinding.builder()
                        .type(PiiType.ID_CARD)
                        .maskedValue(mask(matched))
                        .startIndex(m18.start())
                        .endIndex(m18.end())
                        .confidence(0.95)
                        .build());
            }
        }

        // 15 位身份证
        Matcher m15 = ID_CARD_15.matcher(text);
        while (m15.find()) {
            String matched = m15.group();
            findings.add(PiiFinding.builder()
                    .type(PiiType.ID_CARD)
                    .maskedValue(mask(matched))
                    .startIndex(m15.start())
                    .endIndex(m15.end())
                    .confidence(0.7)
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
     * 对身份证号做保留头尾的脱敏。
     *
     * <p>保留前 6 位（行政区划码）与后 4 位，中间固定 8 个星号。
     * 保留区划码是业务上的刻意选择——统计分析、属地核验仍需地区信息，
     * 而出生日期与顺序码这类可直接定位到个人的字段被完全遮蔽。
     *
     * <p>注意 18 位号码脱敏后长度仍为 18，但 15 位号码脱敏后会变成 18 位，
     * <b>长度不守恒</b>，做定长替换的调用方需留意。
     *
     * @param matchedText 命中的原始号码；为 {@code null} 或长度不足 10 时按不可安全脱敏处理
     * @return 形如 {@code 110101********1234} 的脱敏串；输入过短时统一返回 {@code "****"}
     */
    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 10) {
            return "****";
        }
        return matchedText.substring(0, 6) + "********" + matchedText.substring(matchedText.length() - 4);
    }

    /**
     * 用 ISO 7064:1983 MOD 11-2 算法校验 18 位身份证的末位校验码。
     *
     * <p>前 17 位按固定权重加权求和后对 11 取模，查表得到期望校验字符。
     * 引入该校验是为了压制误报——纯靠正则，任何 18 位数字串（如订单号、
     * 设备编号）都可能被误判为身份证，加校验后误报率大幅下降。
     *
     * <p>末位 {@code x} 统一转大写比较，兼容小写写法。
     *
     * @param idCard 待校验字符串，可为 {@code null}
     * @return 校验通过返回 {@code true}；为 {@code null}、长度不等于 18、
     *         前 17 位含非数字或校验位不符时返回 {@code false}
     */
    private boolean validateIdCard18(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = idCard.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            sum += (c - '0') * weights[i];
        }
        int index = sum % 11;
        char expectedCheck = checkCodes[index];
        char actualCheck = Character.toUpperCase(idCard.charAt(17));
        return expectedCheck == actualCheck;
    }
}
