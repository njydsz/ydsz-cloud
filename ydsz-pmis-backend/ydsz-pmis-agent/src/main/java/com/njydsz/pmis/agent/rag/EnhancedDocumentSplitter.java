package com.njydsz.pmis.agent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库分片策略增强（P2-4 落地）。
 *
 * <p>对标 Coze 知识库分片 / Dify Chunking Strategy / LangChain Text Splitter：
 * 提供多种智能分片策略，替代简单的固定长度切分。
 *
 * <p>支持的分片策略：
 * <ul>
 *   <li><b>固定长度</b> - 按字符数切分（默认，向后兼容）</li>
 *   <li><b>语义分段</b> - 按段落/标题/分隔符切分，保留语义完整性</li>
 *   <li><b>递归切分</b> - 先按大分隔符切分，超长再按小分隔符递归切分</li>
 *   <li><b>Markdown 结构化</b> - 按 # 标题层级切分</li>
 *   <li><b>滑动窗口</b> - 带重叠的固定长度切分，保留上下文连续性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-4)
 */
@Slf4j
@Component
public class EnhancedDocumentSplitter {

    /** 默认分块大小 */
    public static final int DEFAULT_CHUNK_SIZE = 500;

    /** 默认分块重叠 */
    public static final int DEFAULT_CHUNK_OVERLAP = 50;

    /**
     * 分片策略枚举。
     */
    public enum Strategy {
        /** 固定长度切分 */
        FIXED_SIZE,
        /** 语义分段（按段落/标题） */
        SEMANTIC,
        /** 递归切分（先大后小） */
        RECURSIVE,
        /** Markdown 结构化切分 */
        MARKDOWN,
        /** 滑动窗口（带重叠） */
        SLIDING_WINDOW
    }

    /**
     * 按指定策略分片文档。
     *
     * @param content    文档内容
     * @param strategy   分片策略
     * @param chunkSize  目标分块大小（字符数）
     * @param overlap    分块重叠（字符数）
     * @return 分块列表
     */
    public List<String> split(String content, Strategy strategy,
                               int chunkSize, int overlap) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) chunkSize = DEFAULT_CHUNK_SIZE;
        if (overlap < 0) overlap = DEFAULT_CHUNK_OVERLAP;
        if (overlap >= chunkSize) overlap = chunkSize / 4;

        return switch (strategy) {
            case FIXED_SIZE -> splitFixedSize(content, chunkSize);
            case SEMANTIC -> splitSemantic(content, chunkSize, overlap);
            case RECURSIVE -> splitRecursive(content, chunkSize, overlap);
            case MARKDOWN -> splitMarkdown(content, chunkSize, overlap);
            case SLIDING_WINDOW -> splitSlidingWindow(content, chunkSize, overlap);
        };
    }

    /**
     * 自动选择策略分片。
     *
     * <p>根据内容特征自动选择最佳策略：
     * <ul>
     *   <li>包含 Markdown 标题标记 → MARKDOWN</li>
     *   <li>包含明确段落分隔（双换行）→ SEMANTIC</li>
     *   <li>其他 → RECURSIVE</li>
     * </ul>
     */
    public List<String> splitAuto(String content, int chunkSize, int overlap) {
        if (content == null || content.isBlank()) return List.of();

        // 检测 Markdown 标题
        long mdHeaders = content.lines()
                .filter(l -> l.strip().startsWith("# "))
                .count();
        if (mdHeaders >= 2) {
            log.debug("[Splitter] 自动选择 MARKDOWN 策略 ({} 个标题)", mdHeaders);
            return splitMarkdown(content, chunkSize, overlap);
        }

        // 检测段落分隔
        long paragraphs = content.split("\n\\s*\n").length;
        if (paragraphs >= 3) {
            log.debug("[Splitter] 自动选择 SEMANTIC 策略 ({} 个段落)", paragraphs);
            return splitSemantic(content, chunkSize, overlap);
        }

        log.debug("[Splitter] 自动选择 RECURSIVE 策略");
        return splitRecursive(content, chunkSize, overlap);
    }

    // ==================== 策略实现 ====================

    /**
     * 固定长度切分。
     */
    private List<String> splitFixedSize(String content, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < content.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, content.length());
            chunks.add(content.substring(i, end).strip());
        }
        return chunks;
    }

    /**
     * 语义分段：按段落和标题切分。
     */
    private List<String> splitSemantic(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        // 先按双换行分段
        String[] paragraphs = content.split("\n\\s*\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() + 2 > chunkSize && current.length() > 0) {
                chunks.add(current.toString().strip());
                // 保留重叠
                if (overlap > 0) {
                    String tail = current.substring(Math.max(0, current.length() - overlap));
                    current = new StringBuilder(tail);
                } else {
                    current = new StringBuilder();
                }
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(para.strip());
        }
        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    /**
     * 递归切分：先按大分隔符，再按小分隔符。
     */
    private List<String> splitRecursive(String content, int chunkSize, int overlap) {
        // 分隔符优先级：双换行 > 单换行 > 句号 > 逗号 > 空格
        String[] separators = {"\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", ", ", " "};
        return splitRecursiveInternal(content, chunkSize, overlap, separators, 0);
    }

    private List<String> splitRecursiveInternal(String content, int chunkSize, int overlap,
                                                  String[] separators, int sepIdx) {
        if (content.length() <= chunkSize) {
            return content.isBlank() ? List.of() : List.of(content.strip());
        }
        if (sepIdx >= separators.length) {
            // 所有分隔符都试过，降级为固定长度
            return splitSlidingWindow(content, chunkSize, overlap);
        }

        String sep = separators[sepIdx];
        String[] parts = content.split(java.util.regex.Pattern.quote(sep));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            if (current.length() + part.length() + sep.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().strip());
                if (overlap > 0 && current.length() > overlap) {
                    current = new StringBuilder(current.substring(current.length() - overlap));
                } else {
                    current = new StringBuilder();
                }
            }
            if (current.length() > 0) current.append(sep);
            current.append(part);
        }
        if (current.length() > 0) {
            // 如果当前块仍超长，用更细的分隔符继续切
            if (current.length() > chunkSize * 1.5 && sepIdx < separators.length - 1) {
                chunks.addAll(splitRecursiveInternal(current.toString(), chunkSize, overlap,
                        separators, sepIdx + 1));
            } else {
                chunks.add(current.toString().strip());
            }
        }
        return chunks;
    }

    /**
     * Markdown 结构化切分：按标题层级切分。
     */
    private List<String> splitMarkdown(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] lines = content.split("\n");
        StringBuilder current = new StringBuilder();
        String currentHeader = "";

        for (String line : lines) {
            // 检测标题
            String stripped = line.strip();
            if (stripped.startsWith("# ")) {
                // 新的一级标题，保存当前块
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current = new StringBuilder();
                }
                currentHeader = stripped;
                current.append(line).append("\n");
            } else if (stripped.startsWith("## ") && current.length() > chunkSize / 2) {
                // 二级标题且当前块已够大，切分
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current = new StringBuilder();
                }
                current.append(line).append("\n");
            } else {
                if (current.length() + line.length() + 1 > chunkSize * 1.5 && current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current = new StringBuilder(currentHeader + "\n");
                }
                current.append(line).append("\n");
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    /**
     * 滑动窗口切分（带重叠）。
     */
    private List<String> splitSlidingWindow(String content, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        if (step <= 0) step = chunkSize;

        for (int i = 0; i < content.length(); i += step) {
            int end = Math.min(i + chunkSize, content.length());
            chunks.add(content.substring(i, end).strip());
            if (end >= content.length()) break;
        }
        return chunks;
    }
}
