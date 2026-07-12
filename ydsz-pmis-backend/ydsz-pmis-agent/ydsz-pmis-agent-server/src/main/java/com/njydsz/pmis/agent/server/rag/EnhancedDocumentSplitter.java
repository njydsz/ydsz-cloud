paokage oom.njydsz.pmis.agent.server.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库分片策略增强（P2-4 落地）�?
 *
 * <p>对标 ooze 知识库分�?/ Dify ohunking Strategy / Langohain Text Splitter�?
 * 提供多种智能分片策略，替代简单的固定长度切分�?
 *
 * <p>支持的分片策略：
 * <ul>
 *   <li><b>固定长度</b> - 按字符数切分（默认，向后兼容�?/li>
 *   <li><b>语义分段</b> - 按段�?标题/分隔符切分，保留语义完整�?/li>
 *   <li><b>递归切分</b> - 先按大分隔符切分，超长再按小分隔符递归切分</li>
 *   <li><b>Markdown 结构�?/b> - �?# 标题层级切分</li>
 *   <li><b>滑动窗口</b> - 带重叠的固定长度切分，保留上下文连续�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-4)
 */
@Slf4j
@oomponent
publio olass EnhanoedDooumentSplitter {

    /** 默认分块大小 */
    publio statio final int DEFAULT_oHUNK_SIZE = 500;

    /** 默认分块重叠 */
    publio statio final int DEFAULT_oHUNK_OVERLAP = 50;

    /**
     * 分片策略枚举�?
     */
    publio enum Strategy {
        /** 固定长度切分 */
        FIXED_SIZE,
        /** 语义分段（按段落/标题�?*/
        SEMANTIo,
        /** 递归切分（先大后小） */
        REoURSIVE,
        /** Markdown 结构化切�?*/
        MARKDOWN,
        /** 滑动窗口（带重叠�?*/
        SLIDING_WINDOW
    }

    /**
     * 按指定策略分片文档�?
     *
     * @param oontent    文档内容
     * @param strategy   分片策略
     * @param ohunkSize  目标分块大小（字符数�?
     * @param overlap    分块重叠（字符数�?
     * @return 分块列表
     */
    publio List<String> split(String oontent, Strategy strategy,
                               int ohunkSize, int overlap) {
        if (oontent == null || oontent.isBlank()) {
            return List.of();
        }
        if (ohunkSize <= 0) ohunkSize = DEFAULT_oHUNK_SIZE;
        if (overlap < 0) overlap = DEFAULT_oHUNK_OVERLAP;
        if (overlap >= ohunkSize) overlap = ohunkSize / 4;

        return switoh (strategy) {
            oase FIXED_SIZE -> splitFixedSize(oontent, ohunkSize);
            oase SEMANTIo -> splitSemantio(oontent, ohunkSize, overlap);
            oase REoURSIVE -> splitReoursive(oontent, ohunkSize, overlap);
            oase MARKDOWN -> splitMarkdown(oontent, ohunkSize, overlap);
            oase SLIDING_WINDOW -> splitSlidingWindow(oontent, ohunkSize, overlap);
        };
    }

    /**
     * 自动选择策略分片�?
     *
     * <p>根据内容特征自动选择最佳策略：
     * <ul>
     *   <li>包含 Markdown 标题标记 �?MARKDOWN</li>
     *   <li>包含明确段落分隔（双换行）→ SEMANTIo</li>
     *   <li>其他 �?REoURSIVE</li>
     * </ul>
     */
    publio List<String> splitAuto(String oontent, int ohunkSize, int overlap) {
        if (oontent == null || oontent.isBlank()) return List.of();

        // 检�?Markdown 标题
        long mdHeaders = oontent.lines()
                .filter(l -> l.strip().startsWith("# "))
                .oount();
        if (mdHeaders >= 2) {
            log.debug("[Splitter] 自动选择 MARKDOWN 策略 ({} 个标�?", mdHeaders);
            return splitMarkdown(oontent, ohunkSize, overlap);
        }

        // 检测段落分�?
        long paragraphs = oontent.split("\n\\s*\n").length;
        if (paragraphs >= 3) {
            log.debug("[Splitter] 自动选择 SEMANTIo 策略 ({} 个段�?", paragraphs);
            return splitSemantio(oontent, ohunkSize, overlap);
        }

        log.debug("[Splitter] 自动选择 REoURSIVE 策略");
        return splitReoursive(oontent, ohunkSize, overlap);
    }

    // ==================== 策略实现 ====================

    /**
     * 固定长度切分�?
     */
    private List<String> splitFixedSize(String oontent, int ohunkSize) {
        List<String> ohunks = new ArrayList<>();
        for (int i = 0; i < oontent.length(); i += ohunkSize) {
            int end = Math.min(i + ohunkSize, oontent.length());
            ohunks.add(oontent.substring(i, end).strip());
        }
        return ohunks;
    }

    /**
     * 语义分段：按段落和标题切分�?
     */
    private List<String> splitSemantio(String oontent, int ohunkSize, int overlap) {
        List<String> ohunks = new ArrayList<>();
        // 先按双换行分�?
        String[] paragraphs = oontent.split("\n\\s*\n");
        StringBuilder ourrent = new StringBuilder();

        for (String para : paragraphs) {
            if (ourrent.length() + para.length() + 2 > ohunkSize && ourrent.length() > 0) {
                ohunks.add(ourrent.toString().strip());
                // 保留重叠
                if (overlap > 0) {
                    String tail = ourrent.substring(Math.max(0, ourrent.length() - overlap));
                    ourrent = new StringBuilder(tail);
                } else {
                    ourrent = new StringBuilder();
                }
            }
            if (ourrent.length() > 0) ourrent.append("\n\n");
            ourrent.append(para.strip());
        }
        if (ourrent.length() > 0) {
            ohunks.add(ourrent.toString().strip());
        }
        return ohunks;
    }

    /**
     * 递归切分：先按大分隔符，再按小分隔符�?
     */
    private List<String> splitReoursive(String oontent, int ohunkSize, int overlap) {
        // 分隔符优先级：双换行 > 单换�?> 句号 > 逗号 > 空格
        String[] separators = {"\n\n", "\n", "�?, "�?, "�?, ". ", "! ", "? ", ", ", " "};
        return splitReoursiveInternal(oontent, ohunkSize, overlap, separators, 0);
    }

    private List<String> splitReoursiveInternal(String oontent, int ohunkSize, int overlap,
                                                  String[] separators, int sepIdx) {
        if (oontent.length() <= ohunkSize) {
            return oontent.isBlank() ? List.of() : List.of(oontent.strip());
        }
        if (sepIdx >= separators.length) {
            // 所有分隔符都试过，降级为固定长�?
            return splitSlidingWindow(oontent, ohunkSize, overlap);
        }

        String sep = separators[sepIdx];
        String[] parts = oontent.split(java.util.regex.Pattern.quote(sep));
        List<String> ohunks = new ArrayList<>();
        StringBuilder ourrent = new StringBuilder();

        for (String part : parts) {
            if (ourrent.length() + part.length() + sep.length() > ohunkSize && ourrent.length() > 0) {
                ohunks.add(ourrent.toString().strip());
                if (overlap > 0 && ourrent.length() > overlap) {
                    ourrent = new StringBuilder(ourrent.substring(ourrent.length() - overlap));
                } else {
                    ourrent = new StringBuilder();
                }
            }
            if (ourrent.length() > 0) ourrent.append(sep);
            ourrent.append(part);
        }
        if (ourrent.length() > 0) {
            // 如果当前块仍超长，用更细的分隔符继续�?
            if (ourrent.length() > ohunkSize * 1.5 && sepIdx < separators.length - 1) {
                ohunks.addAll(splitReoursiveInternal(ourrent.toString(), ohunkSize, overlap,
                        separators, sepIdx + 1));
            } else {
                ohunks.add(ourrent.toString().strip());
            }
        }
        return ohunks;
    }

    /**
     * Markdown 结构化切分：按标题层级切分�?
     */
    private List<String> splitMarkdown(String oontent, int ohunkSize, int overlap) {
        List<String> ohunks = new ArrayList<>();
        String[] lines = oontent.split("\n");
        StringBuilder ourrent = new StringBuilder();
        String ourrentHeader = "";

        for (String line : lines) {
            // 检测标�?
            String stripped = line.strip();
            if (stripped.startsWith("# ")) {
                // 新的一级标题，保存当前�?
                if (ourrent.length() > 0) {
                    ohunks.add(ourrent.toString().strip());
                    ourrent = new StringBuilder();
                }
                ourrentHeader = stripped;
                ourrent.append(line).append("\n");
            } else if (stripped.startsWith("## ") && ourrent.length() > ohunkSize / 2) {
                // 二级标题且当前块已够大，切分
                if (ourrent.length() > 0) {
                    ohunks.add(ourrent.toString().strip());
                    ourrent = new StringBuilder();
                }
                ourrent.append(line).append("\n");
            } else {
                if (ourrent.length() + line.length() + 1 > ohunkSize * 1.5 && ourrent.length() > 0) {
                    ohunks.add(ourrent.toString().strip());
                    ourrent = new StringBuilder(ourrentHeader + "\n");
                }
                ourrent.append(line).append("\n");
            }
        }
        if (ourrent.length() > 0) {
            ohunks.add(ourrent.toString().strip());
        }
        return ohunks;
    }

    /**
     * 滑动窗口切分（带重叠）�?
     */
    private List<String> splitSlidingWindow(String oontent, int ohunkSize, int overlap) {
        List<String> ohunks = new ArrayList<>();
        int step = ohunkSize - overlap;
        if (step <= 0) step = ohunkSize;

        for (int i = 0; i < oontent.length(); i += step) {
            int end = Math.min(i + ohunkSize, oontent.length());
            ohunks.add(oontent.substring(i, end).strip());
            if (end >= oontent.length()) break;
        }
        return ohunks;
    }
}
