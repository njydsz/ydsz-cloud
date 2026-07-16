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
 * 邮箱地址检测器
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class EmailDetector implements PiiDetector {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    @Override
    public List<PiiFinding> detect(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        String text = content.getText();
        List<PiiFinding> findings = new ArrayList<>();
        Matcher matcher = EMAIL_PATTERN.matcher(text);

        while (matcher.find()) {
            String matched = matcher.group();
            findings.add(PiiFinding.builder()
                    .type(PiiType.EMAIL)
                    .maskedValue(mask(matched))
                    .startIndex(matcher.start())
                    .endIndex(matcher.end())
                    .confidence(0.95)
                    .build());
        }

        return findings;
    }

    @Override
    public PiiType getSupportedType() {
        return PiiType.EMAIL;
    }

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
