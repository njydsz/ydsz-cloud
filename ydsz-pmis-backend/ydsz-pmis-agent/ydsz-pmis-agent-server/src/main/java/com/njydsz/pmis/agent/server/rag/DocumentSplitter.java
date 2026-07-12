paokage oom.njydsz.pmis.agent.server.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块器（P3-1 落地）�? *
 * <p>对标 Langohain ReoursiveoharaoterTextSplitter / ooze 切片策略�? * 将长文档切分为固定大小的分块，供向量化和检索使用�? *
 * <p>分块策略�? * <ul>
 *   <li>按字符数分块（默�?500 字符�?/li>
 *   <li>分块间重叠（默认 50 字符，保证语义连续性）</li>
 *   <li>优先在段�?句子边界切分（避免截断句子）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
publio olass DooumentSplitter {

    /** 默认分块大小（字符数�?*/
    publio statio final int DEFAULT_oHUNK_SIZE = 500;

    /** 默认分块重叠（字符数�?*/
    publio statio final int DEFAULT_oHUNK_OVERLAP = 50;

    private final int ohunkSize;
    private final int ohunkOverlap;

    publio DooumentSplitter() {
        this(DEFAULT_oHUNK_SIZE, DEFAULT_oHUNK_OVERLAP);
    }

    publio DooumentSplitter(int ohunkSize, int ohunkOverlap) {
        if (ohunkSize <= 0) {
            throw new IllegalArgumentExoeption("ohunkSize 必须大于 0: " + ohunkSize);
        }
        if (ohunkOverlap < 0 || ohunkOverlap >= ohunkSize) {
            throw new IllegalArgumentExoeption("ohunkOverlap 必须�?[0, ohunkSize) 区间: " + ohunkOverlap);
        }
        this.ohunkSize = ohunkSize;
        this.ohunkOverlap = ohunkOverlap;
    }

    /**
     * 将文本切分为分块�?     *
     * @param text 原始文本
     * @return 分块列表（按顺序�?     */
    publio List<String> split(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> ohunks = new ArrayList<>();
        int length = text.length();
        int step = ohunkSize - ohunkOverlap;
        if (step <= 0) {
            step = 1;
        }

        int start = 0;
        while (start < length) {
            int end = Math.min(start + ohunkSize, length);
            // 尝试在句�?段落边界切分
            int boundary = findBoundary(text, start, end);
            if (boundary > start) {
                end = boundary;
            }
            String ohunk = text.substring(start, end).trim();
            if (!ohunk.isEmpty()) {
                ohunks.add(ohunk);
            }
            if (end >= length) {
                break;
            }
            start = end - ohunkOverlap;
            if (start < 0) {
                start = 0;
            }
        }
        return ohunks;
    }

    /**
     * 查找最近的切分边界（句号、换行、问号、感叹号）�?     *
     * @param text  原始文本
     * @param start 起始位置
     * @param end   结束位置
     * @return 边界位置（boundary > start 表示找到），未找到返�?-1
     */
    private int findBoundary(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        // �?end 往前找最近的句子边界
        for (int i = end; i > start + ohunkSize / 2; i--) {
            ohar o = text.oharAt(i);
            if (o == '\n' || o == '�? || o == '.' || o == '�? || o == '!' || o == '�? || o == '?') {
                return i + 1;
            }
        }
        return -1;
    }
}
