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
 * 身份证号检测器
 * <p>
 * 检测 18 位和 15 位中国身份证号码，支持校验位验证。
 *
 * @author ydsz-pmis-team
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

    @Override
    public PiiType getSupportedType() {
        return PiiType.ID_CARD;
    }

    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 10) {
            return "****";
        }
        return matchedText.substring(0, 6) + "********" + matchedText.substring(matchedText.length() - 4);
    }

    /**
     * 18 位身份证校验位验证
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
