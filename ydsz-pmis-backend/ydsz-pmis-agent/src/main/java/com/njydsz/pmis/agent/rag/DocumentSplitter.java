package com.njydsz.pmis.agent.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块器（P3-1 落地）。
 *
 * <p>对标 LangChain RecursiveCharacterTextSplitter / Coze 切片策略，
 * 将长文档切分为固定大小的分块，供向量化和检索使用。
 *
 * <p>分块策略：
 * <ul>
 *   <li>按字符数分块（默认 500 字符）</li>
 *   <li>分块间重叠（默认 50 字符，保证语义连续性）</li>
 *   <li>优先在段落/句子边界切分（避免截断句子）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-1)
 */
public class DocumentSplitter {

    /** 默认分块大小（字符数） */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认分块重叠（字符数） */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentSplitter() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public DocumentSplitter(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0: " + chunkSize);
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap 必须在 [0, chunkSize) 区间: " + chunkOverlap);
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * 将文本切分为分块。
     *
     * @param text 原始文本
     * @return 分块列表（按顺序）
     */
    public List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int step = chunkSize - chunkOverlap;
        if (step <= 0) {
            step = 1;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            // 尝试在句子/段落边界切分
            int boundary = findBoundary(text, start, end);
            if (boundary > start) {
                end = boundary;
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            start = end - chunkOverlap;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }

    /**
     * 查找最近的切分边界（句号、换行、问号、感叹号）。
     *
     * @param text  原始文本
     * @param start 起始位置
     * @param end   结束位置
     * @return 边界位置（boundary > start 表示找到），未找到返回 -1
     */
    private int findBoundary(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        // 从 end 往前找最近的句子边界
        for (int i = end; i > start + chunkSize / 2; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '.' || c == '！' || c == '!' || c == '？' || c == '?') {
                return i + 1;
            }
        }
        return -1;
    }
}
