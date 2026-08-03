package com.njydsz.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.PiiFinding;
import com.njydsz.common.docs.enums.PiiType;
import com.njydsz.common.docs.security.pii.PiiDetector;

/**
 * 银行卡号检测器
 * <p>
 * 检测 16-19 位银行卡号，支持 Luhn 校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class BankCardDetector implements PiiDetector {

    /** 银行卡号正则（16-19 位连续数字） */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{16,19}(?!\\d)");

    /** 已知银行卡 BIN 前缀（前 4 位），覆盖主要发卡机构 */
    private static final Set<String> KNOWN_BIN_PREFIXES = Set.of(
            "6222", "6225", "6226", "6227", "6228", "6229", // 银联
            "4528", "4518", "4392", "4385", "4032", "4026", // Visa (4xxx)
            "5187", "5234", "5246", "5280", "5359", "5440", // Mastercard (5xxx)
            "6210", "6211", "6212", "6213", "6214", "6216", "6217", "6218", "6219", // 借记卡
            "6200", "6201", "6202", "6203", "6204", "6205" // 预付卡
    );

    /**
     * 扫描全文中的银行卡号，需同时通过 Luhn 校验与 BIN 前缀白名单。
     *
     * <p><b>双重过滤是刻意的严格策略：</b>16~19 位数字串在业务文档中极为常见
     * （订单号、流水号、时间戳拼接），仅靠正则误报率极高。
     * 先过 Luhn 校验滤掉随机数字（约 90% 被排除），再要求前 4 位命中已知发卡行 BIN，
     * 两道关卡后剩下的基本可确信是真实卡号。
     *
     * <p><b>代价是漏报：</b>{@link #KNOWN_BIN_PREFIXES} 是硬编码的有限集合，
     * 未收录的发卡行卡号会被<b>静默放过</b>。这是"宁可漏报不可刷屏误报"的取舍；
     * 若业务对漏报敏感，需扩充该常量集合。置信度给 0.85 亦反映此不确定性。
     *
     * <p>返回的下标基于预处理后的文本，脱敏时须使用同一份文本。
     *
     * @param content 文档内容；为 {@code null} 或其 text 为 {@code null} 时返回空列表，不抛异常
     * @return PII 发现列表，仅含双重校验均通过的卡号；无命中时返回空列表而非 {@code null}
     */
    @Override
    public List<PiiFinding> detect(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        String text = content.getText();
        List<PiiFinding> findings = new ArrayList<>();
        Matcher matcher = BANK_CARD_PATTERN.matcher(text);

        while (matcher.find()) {
            String matched = matcher.group();
            if (validateLuhn(matched) && isKnownBin(matched)) {
                findings.add(PiiFinding.builder()
                        .type(PiiType.BANK_CARD)
                        .maskedValue(mask(matched))
                        .startIndex(matcher.start())
                        .endIndex(matcher.end())
                        .confidence(0.85)
                        .build());
            }
        }

        return findings;
    }

    /**
     * 声明本检测器负责的 PII 类别，供组合检测器按类型开关与归类。
     *
     * @return 恒为 {@link PiiType#BANK_CARD}
     */
    @Override
    public PiiType getSupportedType() {
        return PiiType.BANK_CARD;
    }

    /**
     * 对银行卡号做保留头尾的脱敏。
     *
     * <p>保留前 4 位（BIN，可辨识发卡行）与后 4 位（用户自辨），
     * 中间以固定 4 个星号替代。注意星号数量<b>不随原长度变化</b>，
     * 因此脱敏后长度恒为 12，无法从结果反推原卡号位数——这正是期望的效果。
     *
     * @param matchedText 命中的原始卡号；为 {@code null} 或长度小于 8 时按不可安全脱敏处理
     * @return 形如 {@code 6222****1234} 的脱敏串；输入过短时统一返回 {@code "****"}
     */
    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 8) {
            return "****";
        }
        return matchedText.substring(0, 4) + "****" + matchedText.substring(matchedText.length() - 4);
    }

    /**
     * 校验卡号前 4 位是否属于已登记的发卡机构 BIN 白名单。
     *
     * <p>作为 Luhn 之后的第二道过滤：Luhn 只保证数字串"结构合法"，
     * 无法区分合法的卡号与恰好满足校验的其他编号，BIN 白名单补上业务维度的约束。
     * 白名单为静态硬编码，新增发卡行需修改 {@link #KNOWN_BIN_PREFIXES}。
     *
     * @param cardNumber 待判定的卡号，可为 {@code null}
     * @return 前 4 位命中白名单返回 {@code true}；为 {@code null} 或长度不足 4 时返回 {@code false}
     */
    private boolean isKnownBin(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return false;
        }
        return KNOWN_BIN_PREFIXES.contains(cardNumber.substring(0, 4));
    }

    /**
     * 用 Luhn（模 10）算法校验卡号自校验位。
     *
     * <p>从右往左隔位加倍，加倍结果大于 9 则减 9，最终总和能被 10 整除即通过。
     * 该算法是国际银行卡号的通用校验规则，可低成本滤除绝大多数随机数字串，
     * 是本检测器压制误报的第一道关卡。
     *
     * <p>遇到非数字字符立即判负，避免 {@code charAt} 运算产生无意义的负数参与求和。
     *
     * @param cardNumber 待校验的纯数字卡号，由正则保证非 {@code null} 且非空
     * @return 校验和满足模 10 为 0 时返回 {@code true}；含非数字字符时返回 {@code false}
     */
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';
            if (digit < 0 || digit > 9) {
                return false;
            }
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
