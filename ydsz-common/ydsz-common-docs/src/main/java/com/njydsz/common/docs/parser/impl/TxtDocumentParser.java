package com.njydsz.common.docs.parser.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.njydsz.common.docs.domain.DocumentContent;
import com.njydsz.common.docs.domain.DocumentMetadata;
import com.njydsz.common.docs.domain.DocumentSection;
import com.njydsz.common.docs.domain.ParseOptions;
import com.njydsz.common.docs.enums.DocumentFormat;
import com.njydsz.common.docs.exception.DocumentException;
import com.njydsz.common.docs.exception.DocumentExceptionCode;
import com.njydsz.common.docs.parser.DocumentParser;

/**
 * 纯文本文档解析器
 * <p>
 * 解析 .txt 文件，按行切分为段落分节。
 *
 * <p><b>编码约定：</b>不做编码嗅探，字符集完全由 {@code ParseOptions.charset} 指定，
 * 未指定时一律按 UTF-8 解码。GBK 等非 UTF-8 文本必须由调用方显式声明编码，否则会乱码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TxtDocumentParser implements DocumentParser {

    /**
     * 逐行读取纯文本，每个非空行构成一个段落分节。
     *
     * <p>流式逐行读取，内存占用与文件大小无关（除最终拼接的全文外）。
     * 空白行被跳过而不生成分节，避免检索索引里出现大量无意义空条目；
     * 但重建全文时以单个 {@code \n} 连接，因此<b>原文中的空行会丢失</b>，
     * 需要保真还原原始排版的场景不适用本解析器。
     *
     * <p>行内容原样保留（不做 trim），因为纯文本中的缩进本身可能承载结构语义。
     *
     * @param inputStream 文本字节流，由调用方负责关闭；为 {@code null} 时视为空文档
     * @param fileName    原始文件名，仅写入元数据标题与失败日志
     * @param options     解析选项，此处仅取 {@code charset} 字段；传 {@code null} 按 UTF-8 处理
     * @return 文档内容，每个非空行一个 paragraph 分节；页数恒为 1
     * @throws DocumentException 入参流为 {@code null} 时错误码 {@code DOCUMENT_EMPTY}；
     *                           读取失败时错误码 {@code PARSE_FAILED}。
     *                           注意字节与所选编码不符时不会报错，而是产生替换字符（乱码）
     */
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

    /**
     * 声明本解析器在注册中心占据的格式槽位。
     *
     * @return 恒为 {@link DocumentFormat#TXT}
     */
    @Override
    public DocumentFormat getSupportedFormat() {
        return DocumentFormat.TXT;
    }

    /**
     * 确定读取文本所用字符集，未显式配置时回退 UTF-8。
     *
     * @param options 解析选项，可为 {@code null}
     * @return 解析出的字符集；未配置或配置为空白串时返回 {@link StandardCharsets#UTF_8}
     * @throws java.nio.charset.UnsupportedCharsetException 配置的编码名 JVM 不认识时抛出，
     *         该异常不会被 {@code parse} 捕获转换，会直接向上传播
     */
    private Charset resolveCharset(ParseOptions options) {
        if (options != null && options.getCharset() != null && !options.getCharset().isBlank()) {
            return Charset.forName(options.getCharset());
        }
        return StandardCharsets.UTF_8;
    }
}
