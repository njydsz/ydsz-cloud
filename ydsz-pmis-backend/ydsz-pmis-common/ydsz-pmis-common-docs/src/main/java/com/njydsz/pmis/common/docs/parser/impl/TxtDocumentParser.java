package com.njydsz.pmis.common.docs.parser.impl;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentMetadata;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.domain.ParseOptions;
import com.njydsz.pmis.common.docs.enums.DocumentFormat;
import com.njydsz.pmis.common.docs.exception.DocumentException;
import com.njydsz.pmis.common.docs.exception.DocumentExceptionCode;
import com.njydsz.pmis.common.docs.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本文档解析器
 * <p>
 * 解析 .txt 文件，支持自动编码检测（UTF-8 / GBK）。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "java.io.BufferedReader")
public class TxtDocumentParser implements DocumentParser {

    @Override
    public DocumentContent parse(InputStream inputStream, String fileName, ParseOptions options) {
        if (inputStream == null) {
            throw new DocumentException(DocumentExceptionCode.DOCUMENT_EMPTY);
        }

        Charset charset = resolveCharset(options);
        List<DocumentSection> sections = new ArrayList<>();
        StringBuilder fullText = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) {
                    continue;
                }
                sections.add(DocumentSection.builder()
                        .type("paragraph")
                        .content(line)
                        .pageNumber(1)
                        .build());
                if (fullText.length() > 0) {
                    fullText.append('\n');
                }
                fullText.append(line);
            }
        } catch (IOException e) {
            log.error("[TxtDocumentParser] 解析失败: {}", fileName, e);
            throw new DocumentException(DocumentExceptionCode.PARSE_FAILED, e);
        }

        String text = fullText.toString();
        return DocumentContent.builder()
                .text(text)
                .sections(sections)
                .metadata(DocumentMetadata.builder()
                        .title(fileName)
                        .charCount(text.length())
                        .build())
                .totalChars(text.length())
                .totalPages(1)
                .build();
    }

    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.TXT;
    }

    /**
     * 解析字符编码，优先使用配置指定的编码，否则默认 UTF-8
     */
    private Charset resolveCharset(ParseOptions options) {
        if (options != null && options.getCharset() != null && !options.getCharset().isBlank()) {
            return Charset.forName(options.getCharset());
        }
        return StandardCharsets.UTF_8;
    }
}
