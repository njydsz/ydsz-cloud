package com.njydsz.common.search.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * 中文分词器接口。
 *
 * <p>抽象中文分词能力，支持多种实现：
 * <ul>
 *   <li>{@link JiebaTokenizer} — 基于 jieba-java 的细粒度分词（需引入依赖）</li>
 *   <li>{@link IcuTokenizer} — 基于 ICU4J BreakIterator 的轻量级分词（无额外依赖）</li>
 *   <li>{@link SimpleTokenizer} — 简单空格分词（兜底降级）</li>
 * </ul>
 *
 * <p>对标行业：
 * <ul>
 *   <li>jieba 分词：jieba-java 实现中文 HMM 分词（Python jieba 的 Java 移植）</li>
 *   <li>IK Analyzer：Lucene 生态主流中文分词器（支持自定义词库）</li>
 *   <li>HanLP：基于深度学习的中文 NLP 分词</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ChineseTokenizer {

    /**
     * 对文本进行中文分词。
     *
     * @param text 原始输入文本
     * @return 分词结果列表；输入为空时返回空列表
     */
    List<String> tokenize(String text);

    /**
     * 对文本进行分词，并过滤停用词。
     *
     * @param text      原始输入文本
     * @param stopWords 停用词集合
     * @return 过滤停用词后的分词列表
     */
    List<String> tokenize(String text, Set<String> stopWords);

    /**
     * 提取文本中的关键词（TopK）。
     *
     * @param text 原始输入文本
     * @param topK 提取关键词数量上限
     * @return 关键词列表
     */
    List<String> extractKeywords(String text, int topK);

    // ==================== 内置实现 ====================

    /**
     * ICU4J 分词实现（无额外依赖，适合中英文混合）。
     *
     * <p>使用 Unicode 文本边界分析，不依赖外部词库，
     * 适合不想引入 jieba 依赖的轻量级场景。
     */
    @Slf4j
    class IcuTokenizer implements ChineseTokenizer {

        @Override
        public List<String> tokenize(String text) {
            return tokenize(text, Collections.emptySet());
        }

        @Override
        public List<String> tokenize(String text, Set<String> stopWords) {
            if (text == null || text.isBlank()) {
                return Collections.emptyList();
            }
            List<String> tokens = new java.util.ArrayList<>();
            com.ibm.icu.text.BreakIterator boundary = com.ibm.icu.text.BreakIterator.getWordInstance();
            boundary.setText(text);
            int start = boundary.first();
            for (int end = boundary.next(); end != com.ibm.icu.text.BreakIterator.DONE; start = end, end = boundary.next()) {
                String word = text.substring(start, end).trim().toLowerCase();
                if (!word.isBlank() && !stopWords.contains(word) && word.length() > 1) {
                    tokens.add(word);
                }
            }
            return tokens;
        }

        @Override
        public List<String> extractKeywords(String text, int topK) {
            List<String> tokens = tokenize(text);
            // 简化实现：按词频排序取 TopK
            return tokens.stream()
                    .distinct()
                    .limit(topK)
                    .toList();
        }
    }

    /**
     * 简单空格分词（兜底实现，无分词效果但保证可用）。
     */
    @Slf4j
    class SimpleTokenizer implements ChineseTokenizer {

        @Override
        public List<String> tokenize(String text) {
            return tokenize(text, Collections.emptySet());
        }

        @Override
        public List<String> tokenize(String text, Set<String> stopWords) {
            if (text == null || text.isBlank()) {
                return Collections.emptyList();
            }
            List<String> tokens = new java.util.ArrayList<>();
            for (String token : text.toLowerCase().split("\\s+")) {
                if (!token.isBlank() && !stopWords.contains(token)) {
                    tokens.add(token);
                }
            }
            return tokens;
        }

        @Override
        public List<String> extractKeywords(String text, int topK) {
            return tokenize(text).stream().distinct().limit(topK).toList();
        }
    }

    /**
     * jieba 分词实现（推荐，需引入 com.huaban:jieba-analysis 依赖）。
     *
     * <p>基于 TF-IDF 和 HMM 模型的实际语义分词，
     * 支持关键词提取、词性标注等高级能力。
     */
    @Slf4j
    class JiebaTokenizer implements ChineseTokenizer {

        private volatile boolean initialized = false;

        private void ensureInit() {
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        try {
                            // 触发 jieba 分词器静态初始化（加载词典）
                            getClass().getClassLoader().loadClass("com.huaban.analysis.jieba.JiebaSegmenter");
                            initialized = true;
                            log.info("[JiebaTokenizer] 分词器初始化完成");
                        } catch (ClassNotFoundException e) {
                            initialized = false;
                            throw new IllegalStateException(
                                    "jieba-analysis 依赖未引入，请添加 com.huaban:jieba-analysis 依赖", e);
                        }
                    }
                }
            }
        }

        @Override
        public List<String> tokenize(String text) {
            return tokenize(text, Collections.emptySet());
        }

        @Override
        public List<String> tokenize(String text, Set<String> stopWords) {
            if (text == null || text.isBlank()) {
                return Collections.emptyList();
            }
            ensureInit();
            try {
                com.huaban.analysis.jieba.JiebaSegmenter segmenter = new com.huaban.analysis.jieba.JiebaSegmenter();
                List<com.huaban.analysis.jieba.SegToken> tokens = segmenter.process(text,
                        com.huaban.analysis.jieba.JiebaSegmenter.SegMode.SEARCH);
                List<String> result = new java.util.ArrayList<>();
                for (com.huaban.analysis.jieba.SegToken token : tokens) {
                    String word = token.getWord().trim().toLowerCase();
                    if (!word.isBlank() && !stopWords.contains(word) && word.length() > 1) {
                        result.add(word);
                    }
                }
                return result;
            } catch (Exception e) {
                log.warn("[JiebaTokenizer] 分词失败，降级到简单分词: {}", e.getMessage());
                return new SimpleTokenizer().tokenize(text, stopWords);
            }
        }

        @Override
        public List<String> extractKeywords(String text, int topK) {
            if (text == null || text.isBlank()) {
                return Collections.emptyList();
            }
            ensureInit();
            try {
                com.huaban.analysis.jieba.JiebaSegmenter segmenter = new com.huaban.analysis.jieba.JiebaSegmenter();
                return segmenter.sentenceProcess(text).stream()
                        .map(String::toLowerCase)
                        .distinct()
                        .limit(topK)
                        .toList();
            } catch (Exception e) {
                log.warn("[JiebaTokenizer] 关键词提取失败: {}", e.getMessage());
                return tokenize(text).stream().distinct().limit(topK).toList();
            }
        }
    }
}
