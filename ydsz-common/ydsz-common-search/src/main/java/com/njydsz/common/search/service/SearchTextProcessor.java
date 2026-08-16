package com.njydsz.common.search.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.search.config.SearchProperties;

/**
 * 搜索文本预处理器。
 *
 * <p>在请求进入引擎前对关键词做归一化，直接影响召回率。
 * 采用管道 + 插件模式：规范化与停用词由内部 {@link SearchPipeline} 完成，
 * 同义词扩展与拼音转换为本处理器独有的增强能力。
 *
 * <p>处理流程：
 * <ol>
 *   <li>NormalizerFilter — 标点清理、空白归一化、长度截断</li>
 *   <li>StopWordFilter — 基于内置停用词表过滤无意义词</li>
 *   <li>同义词扩展 — 加载同义词词典，扩展关键词提升召回</li>
 *   <li>拼音转换 — 将中文关键词转为拼音，支持拼音搜索</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SearchTextProcessor {

    private final SearchProperties properties;
    private final SearchPipeline pipeline;
    private final Map<String, List<String>> synonymMap = new HashMap<>();
    private SynonymTrie synonymTrie = new SynonymTrie();

    /** 常用停用词 */
    private static final Set<String> DEFAULT_STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有",
            "看", "好", "自己", "这"
    );

    public SearchTextProcessor(SearchProperties properties) {
        this.properties = properties;
        this.pipeline = SearchPipeline.builder()
                .addFilter(new SearchPipeline.NormalizerFilter())
                .addFilter(new SearchPipeline.StopWordFilter(DEFAULT_STOP_WORDS))
                .build();
        init();
    }

    private void init() {
        // 加载同义词词典
        if (properties.getTextProcessor().isSynonymEnabled()) {
            loadSynonyms(properties.getTextProcessor().getSynonymFile());
        }

        // 加载拼音词典
        if (properties.getTextProcessor().isPinyinEnabled()
                && properties.getTextProcessor().getPinyinFile() != null) {
            loadPinyinDictionary(properties.getTextProcessor().getPinyinFile());
        }

        log.info("[SearchTextProcessor] 初始化完成: synonyms={}, pinyin={}",
                synonymMap.size(), pinyinMap.size());
    }

    /**
     * 处理搜索关键词：规范化 → 停用词 → 同义词 → 拼音。
     *
     * @param keyword 原始关键词
     * @return 处理后的关键词（可能包含多个词，用空格连接）
     */
    public String process(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }

        // 管道处理：规范化 + 停用词过滤
        String result = pipeline.process(keyword);
        if (result == null || result.isBlank()) {
            result = keyword.trim();
        }

        // 同义词扩展
        if (properties.getTextProcessor().isSynonymEnabled()) {
            result = expandSynonyms(result);
        }

        // 拼音转换（简单实现：保留原始中文 + 追加拼音）
        if (properties.getTextProcessor().isPinyinEnabled()) {
            String pinyin = toPinyin(keyword);
            if (pinyin != null && !pinyin.isBlank() && !pinyin.equals(keyword)) {
                result = result + " " + pinyin;
            }
        }

        return result;
    }

    /**
     * 加载同义词词典。
     *
     * <p>词典格式（每行一组同义词，用逗号分隔）：
     * <pre>
     * 项目,工程,Project
     * 合同,Contract,协议
     * </pre>
     */
    private void loadSynonyms(String filePath) {
        try (InputStream is = resolveResource(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("[,，]");
                List<String> synonyms = List.of(parts).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                if (synonyms.size() >= 2) {
                    for (String word : synonyms) {
                        synonymMap.put(word.toLowerCase(), synonyms);
                    }
                }
            }
            log.info("[SearchTextProcessor] 同义词词典加载完成: {} 组", synonymMap.size());
            synonymTrie = new SynonymTrie();
            for (String key : synonymMap.keySet()) {
                synonymTrie.insert(key);
            }
        } catch (IOException e) {
            log.warn("[SearchTextProcessor] 同义词词典加载失败: {}", e.getMessage());
        } catch (NullPointerException e) {
            log.warn("[SearchTextProcessor] 同义词词典文件不存在: {}", filePath);
        }
    }

    /**
     * 加载拼音词典。
     *
     * <p>词典格式（每行一个映射）：汉字=拼音
     * <pre>
     * 项=xiang
     * 目=mu
     * 工=gong
     * </pre>
     */
    private void loadPinyinDictionary(String filePath) {
        try (InputStream is = resolveResource(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx > 0 && idx < line.length() - 1) {
                    String hanzi = line.substring(0, idx).trim();
                    String pinyin = line.substring(idx + 1).trim();
                    if (!hanzi.isEmpty() && !pinyin.isEmpty()) {
                        pinyinMap.put(hanzi, pinyin);
                    }
                }
            }
            log.info("[SearchTextProcessor] 拼音词典加载完成: {} 字", pinyinMap.size());
        } catch (IOException e) {
            log.warn("[SearchTextProcessor] 拼音词典加载失败: {}", e.getMessage());
        } catch (NullPointerException e) {
            log.warn("[SearchTextProcessor] 拼音词典文件不存在: {}", filePath);
        }
    }

    private InputStream resolveResource(String filePath) throws IOException {
        if (filePath.startsWith("classpath:")) {
            String path = filePath.substring("classpath:".length());
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) {
                throw new IOException("Resource not found: " + filePath);
            }
            return is;
        }
        return new FileInputStream(filePath);
    }

    private String expandSynonyms(String text) {
        StringBuilder result = new StringBuilder(text);
        String lowerText = text.toLowerCase();
        Set<String> matchedKeys = synonymTrie.matchAll(lowerText);
        for (String key : matchedKeys) {
            List<String> synonyms = synonymMap.get(key);
            if (synonyms != null) {
                for (String synonym : synonyms) {
                    if (!synonym.equalsIgnoreCase(text)) {
                        result.append(" ").append(synonym);
                    }
                }
            }
        }
        return result.toString();
    }

    private final Map<String, String> pinyinMap = new HashMap<>();

    /**
     * 同义词 Trie 树，用于高效多模式匹配。
     */
    private static final class SynonymTrie {

        private final TrieNode root = new TrieNode();

        void insert(String word) {
            if (word == null || word.isBlank()) {
                return;
            }
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                node = node.children.computeIfAbsent(c, k -> new TrieNode());
            }
            node.word = word;
        }

        Set<String> matchAll(String text) {
            Set<String> matches = new HashSet<>();
            if (text == null || text.isBlank()) {
                return matches;
            }
            for (int i = 0; i < text.length(); i++) {
                TrieNode node = root;
                for (int j = i; j < text.length(); j++) {
                    TrieNode child = node.children.get(text.charAt(j));
                    if (child == null) {
                        break;
                    }
                    node = child;
                    if (node.word != null) {
                        matches.add(node.word);
                    }
                }
            }
            return matches;
        }

        /**
         * 前缀树（Trie）节点。
         *
         * <p>用于关键词/同义词的快速前缀匹配检索；
         * {@code word} 非空表示该节点是一个完整词的终止点。
         */
        private static final class TrieNode {

            /** 子节点映射（字符 → 子节点） */
            final Map<Character, TrieNode> children = new HashMap<>();

            /** 完整词（非空表示该节点结束一个词） */
            String word = null;
        }
    }

    private String toPinyin(String text) {
        if (text == null || pinyinMap.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                String pinyin = pinyinMap.get(String.valueOf(c));
                sb.append(pinyin != null ? pinyin : c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
