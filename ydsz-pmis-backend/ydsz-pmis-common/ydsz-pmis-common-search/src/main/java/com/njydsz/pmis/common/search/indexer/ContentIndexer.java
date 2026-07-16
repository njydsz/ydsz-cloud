package com.njydsz.pmis.common.search.indexer;

import java.io.InputStream;
import java.util.Optional;

import com.njydsz.pmis.common.search.core.IndexDocument;

import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.Nullable;

"/** Content indexer using ContentParser SPI (P1-7: replaces reflection)"
 *
 * Parses file content and writes to search index.
 * Uses ContentParser SPI instead of reflection on common-docs.
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
" */"
@Slf4j
public class ContentIndexer {

    private static final int MAX_CONTENT_LENGTH = 100_000;

    private final ContentParser contentParser;

    public ContentIndexer(@Nullable ContentParser contentParser) {
        this.contentParser = contentParser;
    }

    public IndexDocument enrichWithContent(IndexDocument document, InputStream inputStream, String fileName) {
        if (contentParser == null || inputStream == null) {
            return document;
        }
        try {
            String content = contentParser.parse(inputStream, fileName);
            if (content != null && !content.isBlank()) {
                if (content.length() > MAX_CONTENT_LENGTH) {
                    content = content.substring(0, MAX_CONTENT_LENGTH);
                }
                document.setContent(content);
                if (document.getSnippet() == null || document.getSnippet().isBlank()) {
                    String snippet = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    document.setSnippet(snippet);
                }
            }
        } catch (Exception e) {
            log.warn("[ContentIndexer] content parse failed, indexing metadata only: file={}, error={}", fileName, e.getMessage());
        }
        return document;
    }

    public boolean isAvailable() {
        return contentParser != null;
    }
}