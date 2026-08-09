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
 * API 密钥/Token 检测器
 * <p>
 * 检测文档中泄露的 API 密钥、Access Token、Secret 等凭据。
 *
 * <p><b>检测模式：</b>
 * <ul>
 *   <li>key=value 格式的凭据（key/secret/token/apikey/password/passphrase）</li>
 *   <li>Bearer Token 格式</li>
 *   <li>AWS Access Key ID 格式（AKIA 开头）</li>
 *   <li>JWT 格式（header.payload.signature）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class ApiKeyDetector implements PiiDetector {

    /** key=value 格式的凭据 */
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|secret|token|access[_-]?key|password|passphrase|client[_-]?secret)" +
            "\\s*[:=]\\s*['\"]?([A-Za-z0-9+/=_\\-]{16,})['\"]?");

    /** Bearer Token */
    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+([A-Za-z0-9_\\-\\.]{20,})");

    /** AWS Access Key ID */
    private static final Pattern AWS_PATTERN = Pattern.compile(
            "\\bAKIA[0-9A-Z]{16}\\b");

    /** JWT Token */
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]*\\b");

    @Override
    public List<PiiFinding> detect(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        String text = content.getText();
        List<PiiFinding> findings = new ArrayList<>();

        // key=value 格式
        Matcher kvMatcher = KEY_VALUE_PATTERN.matcher(text);
        while (kvMatcher.find()) {
            findings.add(PiiFinding.builder()
                    .type(PiiType.API_KEY)
                    .maskedValue(mask(kvMatcher.group(0)))
                    .startIndex(kvMatcher.start())
                    .endIndex(kvMatcher.end())
                    .confidence(0.9)
                    .build());
        }

        // Bearer Token
        Matcher bearerMatcher = BEARER_PATTERN.matcher(text);
        while (bearerMatcher.find()) {
            findings.add(PiiFinding.builder()
                    .type(PiiType.API_KEY)
                    .maskedValue(mask(bearerMatcher.group(0)))
                    .startIndex(bearerMatcher.start())
                    .endIndex(bearerMatcher.end())
                    .confidence(0.85)
                    .build());
        }

        // AWS Access Key
        Matcher awsMatcher = AWS_PATTERN.matcher(text);
        while (awsMatcher.find()) {
            findings.add(PiiFinding.builder()
                    .type(PiiType.API_KEY)
                    .maskedValue(mask(awsMatcher.group()))
                    .startIndex(awsMatcher.start())
                    .endIndex(awsMatcher.end())
                    .confidence(0.95)
                    .build());
        }

        // JWT
        Matcher jwtMatcher = JWT_PATTERN.matcher(text);
        while (jwtMatcher.find()) {
            findings.add(PiiFinding.builder()
                    .type(PiiType.API_KEY)
                    .maskedValue(mask(jwtMatcher.group()))
                    .startIndex(jwtMatcher.start())
                    .endIndex(jwtMatcher.end())
                    .confidence(0.8)
                    .build());
        }

        return findings;
    }

    @Override
    public PiiType getSupportedType() {
        return PiiType.API_KEY;
    }

    @Override
    public String mask(String matchedText) {
        if (matchedText == null || matchedText.length() <= 8) {
            return "****";
        }
        return matchedText.substring(0, 4) + "****" + matchedText.substring(matchedText.length() - 4);
    }
}
