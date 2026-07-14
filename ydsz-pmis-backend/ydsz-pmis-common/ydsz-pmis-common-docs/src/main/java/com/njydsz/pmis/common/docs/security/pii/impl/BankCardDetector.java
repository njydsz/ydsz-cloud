package com.njydsz.pmis.common.docs.security.pii.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.PiiFinding;
import com.njydsz.pmis.common.docs.enums.PiiType;
import com.njydsz.pmis.common.docs.security.pii.PiiDetector;

/**
 * 银行卡号检测器
 * <p>
 * 检测 16-19 位银行卡号，支持 Luhn 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Component
public class BankCardDetector implements PiiDetector {

    /** 银行卡号正则（16-19 位连续数字） */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile(
            "(?<!\\d)\\d{16,19}(?!\\d)");

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
            if (validateLuhn(matched)) {
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

    @Override
    public PiiType getSupportedType() {
        return PiiType.BANK_CARD;
    }

    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 8) {
            return "****";
        }
        return matchedText.substring(0, 4) + "****" + matchedText.substring(matchedText.length() - 4);
    }

    /**
     * Luhn 算法校验银行卡号
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
