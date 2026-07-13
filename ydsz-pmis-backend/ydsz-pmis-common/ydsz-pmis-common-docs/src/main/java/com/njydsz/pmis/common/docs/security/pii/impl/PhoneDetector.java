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
 * 手机号检测器
 * <p>
 * 检测中国大陆手机号（11 位）。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Component
public class PhoneDetector implements PiiDetector {

    /** 中国大陆手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)");

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

    @Override
    public PiiType getSupportedType() {
        return PiiType.PHONE;
    }

    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() < 7) {
            return "****";
        }
        return matchedText.substring(0, 3) + "****" + matchedText.substring(matchedText.length() - 4);
    }
}
