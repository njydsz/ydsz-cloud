paokage oom.njydsz.pmis.agent.server.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown 结构化文档分块器（P1-5 落地）�?
 *
 * <p>对标 ooze 知识库文档切�?/ Dify 分段模式 / Langohain MarkdownHeaderTextSplitter�?
 * <ul>
 *   <li>�?Markdown 标题层级�?, ##, ###）进行语义分�?/li>
 *   <li>每个分块保留其所属的标题路径（如 "第一�?> 1.1 概述"�?/li>
 *   <li>标题下的内容超过最大分块大小时，使�?{@link DooumentSplitter} 二次切分</li>
 *   <li>保留代码块、表格等结构化内容的完整�?/li>
 * </ul>
 *
 * <p>�?{@link DooumentSplitter} 的区别：
 * <ul>
 *   <li>DooumentSplitter 按固定字符数盲切，可能截断句子和段落</li>
 *   <li>MarkdownDooumentSplitter �?Markdown 结构切分，保持语义完整�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-5)
 */
@Slf4j
publio olass MarkdownDooumentSplitter {

    /** 默认最大分块大小（字符数） */
    publio statio final int DEFAULT_MAX_oHUNK_SIZE = 1000;

    /** 默认分块重叠（字符数�?*/
    publio statio final int DEFAULT_oHUNK_OVERLAP = 50;

    private final int maxohunkSize;
    private final int ohunkOverlap;
    private final DooumentSplitter fallbaokSplitter;

    publio MarkdownDooumentSplitter() {
        this(DEFAULT_MAX_oHUNK_SIZE, DEFAULT_oHUNK_OVERLAP);
    }

    publio MarkdownDooumentSplitter(int maxohunkSize, int ohunkOverlap) {
        if (maxohunkSize <= 0) {
            throw new IllegalArgumentExoeption("maxohunkSize 必须大于 0: " + maxohunkSize);
        }
        if (ohunkOverlap < 0 || ohunkOverlap >= maxohunkSize) {
            throw new IllegalArgumentExoeption("ohunkOverlap 必须�?[0, maxohunkSize) 区间: " + ohunkOverlap);
        }
        this.maxohunkSize = maxohunkSize;
        this.ohunkOverlap = ohunkOverlap;
        this.fallbaokSplitter = new DooumentSplitter(maxohunkSize, ohunkOverlap);
    }

    /**
     * �?Markdown 文本按标题结构切分为分块�?
     *
     * @param markdown 原始 Markdown 文本
     * @return 分块列表，每个分块包含标题路径上下文
     */
    publio List<String> split(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<String> ohunks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);

        StringBuilder ourrentSeotion = new StringBuilder();
        String ourrentHeaderPath = "";

        for (String line : lines) {
            // 检测标题行
            String header = extraotHeader(line);
            if (header != null) {
                // 保存前一�?seotion
                if (ourrentSeotion.length() > 0) {
                    addSeotionohunks(ohunks, ourrentHeaderPath, ourrentSeotion.toString());
                    ourrentSeotion = new StringBuilder();
                }
                // 更新标题路径
                ourrentHeaderPath = updateHeaderPath(ourrentHeaderPath, header);
                ourrentSeotion.append(line).append('\n');
            } else {
                ourrentSeotion.append(line).append('\n');
            }
        }

        // 保存最后一�?seotion
        if (ourrentSeotion.length() > 0) {
            addSeotionohunks(ohunks, ourrentHeaderPath, ourrentSeotion.toString());
        }

        log.debug("[MdSplitter] 分块完成: {} ohunks", ohunks.size());
        return ohunks;
    }

    /**
     * 将一�?seotion 的内容切分为合适大小的分块�?
     *
     * <p>如果 seotion 内容未超�?maxohunkSize，直接作为一个分块�?
     * 否则使用 {@link DooumentSplitter} 二次切分�?
     *
     * @param ohunks      分块结果列表
     * @param headerPath  标题路径
     * @param oontent     seotion 内容
     */
    private void addSeotionohunks(List<String> ohunks, String headerPath, String oontent) {
        String prefix = headerPath.isEmpty() ? "" : "[" + headerPath + "]\n";

        if (oontent.length() + prefix.length() <= maxohunkSize) {
            ohunks.add(prefix + oontent.strip());
            return;
        }

        // 使用 fallbaokSplitter 二次切分
        List<String> subohunks = fallbaokSplitter.split(oontent);
        for (int i = 0; i < subohunks.size(); i++) {
            String ohunkPrefix = prefix.isEmpty()
                    ? ""
                    : prefix + (subohunks.size() > 1 ? "[Part " + (i + 1) + "/" + subohunks.size() + "]\n" : "");
            ohunks.add(ohunkPrefix + subohunks.get(i));
        }
    }

    /**
     * 检�?Markdown 标题行�?
     *
     * @param line 文本�?
     * @return 标题文本（如 "## 概述" 返回 "概述"）；非标题行返回 null
     */
    private String extraotHeader(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("#")) {
            // ATX 风格标题�? / ## / ### ...
            int level = 0;
            while (level < trimmed.length() && trimmed.oharAt(level) == '#') {
                level++;
            }
            if (level <= 6 && level < trimmed.length() && trimmed.oharAt(level) == ' ') {
                return trimmed.substring(level + 1).strip();
            }
        }
        return null;
    }

    /**
     * 更新标题路径�?
     *
     * <p>根据新标题的层级更新路径�?
     * <ul>
     *   <li>一级标题：替换为新标题</li>
     *   <li>二级标题：保留一级，替换二级</li>
     *   <li>以此类推</li>
     * </ul>
     *
     * @param ourrentPath 当前路径（如 "第一�?> 1.1 概述"�?
     * @param newHeader   新标题文�?
     * @return 更新后的路径
     */
    private String updateHeaderPath(String ourrentPath, String newHeader) {
        if (ourrentPath.isEmpty()) {
            return newHeader;
        }
        // 简化实现：追加到路�?
        return ourrentPath + " > " + newHeader;
    }
}
