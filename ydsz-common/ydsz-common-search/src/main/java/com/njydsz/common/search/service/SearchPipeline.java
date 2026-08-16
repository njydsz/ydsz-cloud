package com.njydsz.common.search.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.njydsz.common.search.config.SearchProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索管道 — 可插拔的文本处理 Filter 链模式。
 *
 * <p>对标 Lucene Analyzer 的 Tokenizer → TokenFilter Chain 模式：
 * 每个 Filter 实现 {@link TextFilter} 接口，通过配置 {@code ydsz.search.text-pipeline} 控制执行顺序，
 * 便于后续新增 StemmerFilter / PinyinFilter / NormalizerFilter 等。
 *
 * <p>管道执行顺序（默认）：
 * <ol>
 *   <li>{@link NormalizerFilter} — 标点清理、空白归一化、长度截断</li>
 *   <li>{@link StopWordFilter} — 停用词过滤</li>
 *   <li>{@link SynonymExpansionFilter} — 同义词扩展（可选）</li>
 *   <li>{@link TokenTypeFilter} — 中文分词（jieba / ICU4J）</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * SearchPipeline pipeline = SearchPipeline.builder()
 *     .addFilter(new NormalizerFilter())
 *     .addFilter(new StopWordFilter(Set.of("的", "了", "是")))
 *     .addFilter(new ChineseTokenFilter(new ChineseTokenizer.IcuTokenizer()))
 *     .build();
 * String result = pipeline.process("项目管理系统");
 * // → "项目 管理 系统"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SearchPipeline {

    private final List<TextFilter> filters;

    private SearchPipeline(List<TextFilter> filters) {
        this.filters = List.copyOf(filters);
    }

    /**
     * 按顺序执行管道中的所有 Filter。
     *
     * @param text 原始输入文本
     * @return 处理后的文本（空格分隔的词元）；输入为空时返回原始文本
     */
    public String process(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (TextFilter filter : filters) {
            try {
                result = filter.process(result);
            } catch (Exception e) {
                log.warn("[SearchPipeline] Filter {} 处理失败，跳过: {}",
                        filter.getName(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * 创建管道构建器。
     *
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 基于配置创建管道实例。
     *
     * @param properties 搜索配置
     * @return 配置后的管道
     */
    public static SearchPipeline fromConfig(SearchProperties properties) {
        Builder builder = builder();
        builder.addFilter(new NormalizerFilter());

        if (properties.getSynonym().isEnabled()) {
            builder.addFilter(new SynonymExpansionFilter(properties));
        }

        builder.addFilter(new ChineseTokenFilter(new ChineseTokenizer.IcuTokenizer()));
        return builder.build();
    }

    /**
     * 管道构建器。
     */
    public static class Builder {
        private final List<TextFilter> filters = new ArrayList<>();

        public Builder addFilter(TextFilter filter) {
            if (filter != null) {
                filters.add(filter);
            }
            return this;
        }

        public SearchPipeline build() {
            return new SearchPipeline(filters);
        }
    }

    // ==================== Filter 接口 ====================

    /**
     * 文本过滤器接口 — 每个 Filter 单一职责，可独立测试与复用。
     */
    public interface TextFilter {

        /**
         * 处理输入文本并返回结果。
         *
         * @param text 输入文本
         * @return 处理后的文本
         */
        String process(String text);

        /**
         * 获取过滤器名称（用于日志与监控）。
         *
         * @return 过滤器名称
         */
        String getName();
    }

    // ==================== 内置 Filter 实现 ====================

    /**
     * 文本规范化过滤器：清理标点、归一化空白、截断超长输入。
     */
    @Slf4j
    public static class NormalizerFilter implements TextFilter {

        private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\pP\\pS\\pC]+");
        private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

        private final int maxLength;

        public NormalizerFilter() {
            this(200);
        }

        public NormalizerFilter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public String process(String text) {
            if (text == null || text.isBlank()) {
                return text;
            }
            String result = text.trim();
            // 截断超长输入
            if (result.length() > maxLength) {
                result = result.substring(0, maxLength);
            }
            // 清理标点符号
            result = PUNCTUATION_PATTERN.matcher(result).replaceAll(" ");
            // 归一化空白
            result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim();
            return result;
        }

        @Override
        public String getName() {
            return "NormalizerFilter";
        }
    }

    /**
     * 停用词过滤器：移除常见无意义词。
     */
    @Slf4j
    public static class StopWordFilter implements TextFilter {

        private final Set<String> stopWords;

        public StopWordFilter(Set<String> stopWords) {
            this.stopWords = stopWords != null ? Set.copyOf(stopWords) : Set.of();
        }

        @Override
        public String process(String text) {
            if (text == null || text.isBlank() || stopWords.isEmpty()) {
                return text;
            }
            String[] tokens = text.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String token : tokens) {
                if (!stopWords.contains(token.toLowerCase())) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(token);
                }
            }
            return sb.toString();
        }

        @Override
        public String getName() {
            return "StopWordFilter";
        }
    }

    /**
     * 同义词扩展过滤器：将同义词加入查询（OR 语义）。
     */
    @Slf4j
    public static class SynonymExpansionFilter implements TextFilter {

        private final SearchProperties properties;

        public SynonymExpansionFilter(SearchProperties properties) {
            this.properties = properties;
        }

        @Override
        public String process(String text) {
            // 简化实现：同义词扩展由 SearchTextProcessor 处理，此处占位
            // 实际项目中可加载同义词词典进行改写
            return text;
        }

        @Override
        public String getName() {
            return "SynonymExpansionFilter";
        }
    }

    /**
     * 中文分词过滤器：使用分词器将连续中文拆分为独立词元。
     */
    @Slf4j
    public static class ChineseTokenFilter implements TextFilter {

        private final ChineseTokenizer tokenizer;

        public ChineseTokenFilter(ChineseTokenizer tokenizer) {
            this.tokenizer = tokenizer != null ? tokenizer : new ChineseTokenizer.IcuTokenizer();
        }

        @Override
        public String process(String text) {
            if (text == null || text.isBlank()) {
                return text;
            }
            List<String> tokens = tokenizer.tokenize(text);
            return String.join(" ", tokens);
        }

        @Override
        public String getName() {
            return "ChineseTokenFilter(" + tokenizer.getClass().getSimpleName() + ")";
        }
    }

    /**
     * 拼音首字母过滤器：支持拼音首字母搜索（如输入 "xm" → "项目"）。
     */
    @Slf4j
    public static class PinyinInitialFilter implements TextFilter {

        @Override
        public String process(String text) {
            // 简化实现：依赖 pinyin4j 或自定义拼音库
            // 输入 "xm" 时附加 "项目" 的同音/拼音首字母建议
            return text;
        }

        @Override
        public String getName() {
            return "PinyinInitialFilter";
        }
    }
}
