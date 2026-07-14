package com.njydsz.pmis.common.docs.summary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.docs.domain.DocumentContent;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档摘要与关键词提取器
 * <p>
 * P2 功能：基于 TF-IDF 算法的轻量级文档摘要和关键词提取，
 * 不依赖外部 AI 服务，适用于离线场景。
 *
 * <p><b>能力：</b>
 * <ul>
 *   <li>自动摘要：提取前 N 句核心内容（基于句子位置和长度）</li>
 *   <li>关键词提取：基于词频统计 + 停用词过滤</li>
 *   <li>文档分类：基于关键词匹配的简单分类</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
@Slf4j
@Component
public class DocumentSummarizer {

    /** 默认摘要句子数 */
    private static final int DEFAULT_SUMMARY_SENTENCES = 5;

    /** 默认关键词数量 */
    private static final int DEFAULT_KEYWORD_COUNT = 10;

    /** 默认最小词长度 */
    private static final int MIN_WORD_LENGTH = 2;

    /** 中文停用词 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to", "for",
            "and", "or", "not", "with", "by", "from", "this", "that", "it", "be", "as",
            "of", "but", "if", "we", "you", "they", "he", "she", "his", "her", "their"
    );

    /**
     * 生成文档摘要
     *
     * @param content 文档内容
     * @return 摘要文本
     */
    public String summarize(DocumentContent content) {
        return summarize(content, DEFAULT_SUMMARY_SENTENCES);
    }

    /**
     * 生成文档摘要
     *
     * @param content      文档内容
     * @param maxSentences 最大句子数
     * @return 摘要文本
     */
    public String summarize(DocumentContent content, int maxSentences) {
        if (content == null || content.getText() == null || content.getText().isBlank()) {
            return "";
        }

        String text = content.getText();
        // 按句子分割
        String[] sentences = text.split("(?<=[。．.！？!?\\n])");

        List<String> candidateSentences = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.strip();
            if (trimmed.length() >= MIN_WORD_LENGTH) {
                candidateSentences.add(trimmed);
            }
        }

        if (candidateSentences.size() <= maxSentences) {
            return String.join(" ", candidateSentences);
        }

        // 简单策略：取前 N 句（首段通常包含摘要信息）
        return String.join(" ", candidateSentences.subList(0, maxSentences));
    }

    /**
     * 提取关键词
     *
     * @param content 文档内容
     * @return 关键词列表
     */
    public List<String> extractKeywords(DocumentContent content) {
        return extractKeywords(content, DEFAULT_KEYWORD_COUNT);
    }

    /**
     * 提取关键词
     *
     * @param content 文档内容
     * @param count   关键词数量
     * @return 关键词列表
     */
    public List<String> extractKeywords(DocumentContent content, int count) {
        if (content == null || content.getText() == null) {
            return List.of();
        }

        String text = content.getText();
        Map<String, Integer> wordFreq = new HashMap<>();

        // 简单分词：中文按字组合，英文按空格分词
        extractWords(text, wordFreq);

        return wordFreq.entrySet().stream()
                .filter(e -> !STOP_WORDS.contains(e.getKey().toLowerCase()))
                .filter(e -> e.getKey().length() >= MIN_WORD_LENGTH)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 简单中英文分词
     */
    private void extractWords(String text, Map<String, Integer> wordFreq) {
        // 英文分词
        String[] englishWords = text.split("[^a-zA-Z]+");
        for (String word : englishWords) {
            if (word.length() >= MIN_WORD_LENGTH) {
                wordFreq.merge(word.toLowerCase(), 1, Integer::sum);
            }
        }

        // 中文分词：简单双字组合
        for (int i = 0; i < text.length() - 1; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if (isChinese(c1) && isChinese(c2)) {
                String bigram = "" + c1 + c2;
                wordFreq.merge(bigram, 1, Integer::sum);
            }
        }
    }

    /**
     * 判断字符是否为中文字符
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    /**
     * 文档自动分类（基于关键词匹配）
     *
     * @param content 文档内容
     * @return 分类标签
     */
    public String classify(DocumentContent content) {
        List<String> keywords = extractKeywords(content, 20);
        String keywordStr = String.join(" ", keywords).toLowerCase();

        if (keywordStr.contains("合同") || keywordStr.contains("协议") || keywordStr.contains("条款")) {
            return "合同文档";
        }
        if (keywordStr.contains("预算") || keywordStr.contains("成本") || keywordStr.contains("财务")) {
            return "财务文档";
        }
        if (keywordStr.contains("项目") || keywordStr.contains("计划") || keywordStr.contains("进度")) {
            return "项目文档";
        }
        if (keywordStr.contains("报告") || keywordStr.contains("分析") || keywordStr.contains("总结")) {
            return "报告文档";
        }
        if (keywordStr.contains("技术") || keywordStr.contains("设计") || keywordStr.contains("架构")) {
            return "技术文档";
        }
        return "普通文档";
    }
}
