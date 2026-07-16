package com.njydsz.pmis.common.docs.preprocess.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.config.DocsProperties;

import com.njydsz.pmis.common.docs.domain.DocumentContent;
import com.njydsz.pmis.common.docs.domain.DocumentSection;
import com.njydsz.pmis.common.docs.preprocess.DocumentPreprocessor;

import lombok.extern.slf4j.Slf4j;

/**
 * 文本分块预处理器
 * <p>
 * 将长文本按语义边界分块，适用于全文索引和 RAG（检索增强生成）场景。
 *
 * <p><b>分块策略：</b>
 * <ul>
 *   <li>优先在段落边界（双换行）处分块</li>
 *   <li>其次在单换行处分块</li>
 *   <li>最后在句号处分块</li>
 *   <li>每块不超过 maxChunkSize 字符（默认 2000）</li>
 *   <li>块间有 overlap 字符的重叠（默认 200），保证上下文连续性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class TextChunker implements DocumentPreprocessor {

    /** 默认最大块大小（字符数） */
    private static final int properties.getMaxChunkSize() = 2000;

    /** 默认块重叠大小（字符数） */
    private static final int properties.getChunkOverlap() = 200;

    @Override
    public DocumentContent process(DocumentContent content) {
        if (content == null || content.getText() == null) {
            return content;
        }

        String text = content.getText();
        if (text.length() <= properties.getMaxChunkSize()) {
            // 文本不够长，无需分块
            return content;
        }

        List<DocumentSection> chunkedSections = new ArrayList<>();
        List<String> chunks = splitIntoChunks(text, properties.getMaxChunkSize(), properties.getChunkOverlap());

        for (int i = 0; i < chunks.size(); i++) {
            chunkedSections.add(DocumentSection.builder()
                    .type("chunk")
                    .content(chunks.get(i))
                    .pageNumber(1)
                    .build());
        }

        // 保留原始分节 + 追加分块结果
        List<DocumentSection> allSections = new ArrayList<>();
        if (content.getSections() != null) {
            allSections.addAll(content.getSections());
        }
        allSections.addAll(chunkedSections);

        content.setSections(allSections);
        return content;
    }

    @Override
    public String getName() {
        return "text-chunker";
    }

    @Override
    public int getOrder() {
        return 30;
    }

    /**
     * 将文本分块
     */
    private List<String> splitIntoChunks(String text, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // 尝试按段落分割
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() + 2 > maxChunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    // 取前 overlap 字符作为下一块的起始
                    String overlapText = current.substring(Math.max(0, current.length() - overlap));
                    current = new StringBuilder(overlapText);
                }
                // 如果单段落超过最大块大小，按句子分割
                if (para.length() > maxChunkSize) {
                    chunks.addAll(splitBySentence(para, maxChunkSize, overlap));
                    current = new StringBuilder();
                    continue;
                }
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(para);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }

        return chunks;
    }

    /**
     * 按句子分割超长段落
     */
    private List<String> splitBySentence(String text, int maxChunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] sentences = text.split("(?<=[。．.！？!?\\n])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > maxChunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    String overlapText = current.substring(Math.max(0, current.length() - overlap));
                    current = new StringBuilder(overlapText);
                }
                if (sentence.length() > maxChunkSize) {
                    // 超长单句直接按最大块大小截断
                    for (int i = 0; i < sentence.length(); i += maxChunkSize - overlap) {
                        int end = Math.min(i + maxChunkSize, sentence.length());
                        chunks.add(sentence.substring(i, end));
                    }
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(sentence);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }

        return chunks;
    }
}
