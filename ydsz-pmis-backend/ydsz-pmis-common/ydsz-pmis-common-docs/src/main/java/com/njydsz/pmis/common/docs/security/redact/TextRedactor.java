package com.njydsz.pmis.common.docs.security.redact;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.PiiFinding;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;

import lombok.extern.slf4j.Slf4j;

/**
 * 纯文本脱敏器
 * <p>
 * 对纯文本类文档（TXT/Markdown/HTML/CSV）进行文本替换脱敏。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Component
public class TextRedactor implements DocumentRedactor {

    private static final List<DocumentFormat> SUPPORTED_FORMATS = List.of(
            DocumentFormat.TXT, DocumentFormat.MARKDOWN, DocumentFormat.HTML,
            DocumentFormat.CSV, DocumentFormat.XML);

    @Override
    public byte[] redact(InputStream inputStream, String fileName, DocumentFormat format, List<PiiFinding> findings) {
        if (inputStream == null) {
            return new byte[0];
        }
        if (findings == null || findings.isEmpty()) {
            try {
                return inputStream.readAllBytes();
            } catch (Exception e) {
                return new byte[0];
            }
        }

        try {
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 按起始位置降序排序，从后向前替换避免位置偏移
            List<PiiFinding> sorted = findings.stream()
                    .sorted(Comparator.comparingInt(PiiFinding::getStartIndex).reversed())
                    .toList();

            for (PiiFinding finding : sorted) {
                int start = finding.getStartIndex();
                int end = finding.getEndIndex();
                if (start >= 0 && end <= text.length() && start < end) {
                    String original = text.substring(start, end);
                    String masked = finding.getMaskedValue() != null ? finding.getMaskedValue() : "****";
                    text = text.substring(0, start) + masked + text.substring(end);
                    log.debug("[TextRedactor] 脱敏: {} → {}", original, masked);
                }
            }

            return text.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("[TextRedactor] 脱敏失败: {}", fileName, e);
            return new byte[0];
        }
    }

    @Override
    public boolean supports(DocumentFormat format) {
        return SUPPORTED_FORMATS.contains(format);
    }
}
