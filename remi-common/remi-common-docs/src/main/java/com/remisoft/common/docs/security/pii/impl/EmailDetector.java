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
 * 邮箱地址检测器
 *
 * @author remi-team
 * @since 1.0.0
 */
@Component
public class EmailDetector implements PiiDetector {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    /**
     * 扫描全文中的邮箱地址。
     *
     * <p>采用简化正则而非 RFC 5322 完整文法：完整文法过于复杂且会引入
     * 灾难性回溯风险，而实务中出现的邮箱几乎都符合"本地部分@域名.顶级域"的常见形态。
     * 置信度给 0.95——邮箱结构特征强，误报概率低。
     *
     * <p><b>不识别的形式：</b>带引号的本地部分、IP 字面量域名
     * （{@code user@[192.168.0.1]}）、以及 IDN 中文域名。
     * 反之，形似邮箱的字符串（如 Maven 坐标、部分文件路径）可能被误报。
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
     * <p>只留本地部分首字母，其余以三个星号替代。域名不脱敏是有意为之：
     * 组织归属（如 {@code @company.com}）对安全审计与泄露溯源有价值，
     * 且域名本身不属于个人标识信息。
     *
     * <p>本地部分只有 1 个字符时，连首字母一并遮蔽，
     * 否则脱敏后等同于暴露了完整本地部分。
     *
     * @param matchedText 命中的原始邮箱地址，可为 {@code null}
     * @return 形如 {@code z***@example.com} 的脱敏串；
     *         入参为 {@code null} 或不含 {@code @}（含以 {@code @} 开头）时返回 {@code "****"}
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
