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

import com.njydsz.common.search.config.SearchProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 搜索文本处理器
 * <p>
 * 提供同义词扩展、停用词过滤和拼音转换能力，增强搜索召回率。
 *
 * <p><b>同义词：</b>加载同义词词典，搜索时扩展关键词。
 * <p><b>停用词：</b>过滤无意义的高频词（的、了、是等）。
 * <p><b>拼音：</b>将中文关键词转换为拼音，支持拼音搜索。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Slf4j
public class SearchTextProcessor {

    private final SearchProperties properties;
    private final Map<String, List<String>> synonymMap = new HashMap<>();
    private final Set<String> stopWords = new HashSet<>();

    /** 常用停用词 */
    private static final Set<String> DEFAULT_STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一",
            "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有",
            "看", "好", "自己", "这"
    );

    public SearchTextProcessor(SearchProperties properties) {
        this.properties = properties;
        init();
    }

    private void init() {
        // 加载停用词
        stopWords.addAll(DEFAULT_STOP_WORDS);

        // 加载同义词词典
        if (properties.getSynonym().isEnabled()) {
            loadSynonyms(properties.getSynonym().getFile());
        }

        // P1-5: 加载拼音词典
        if (properties.getPinyin().isEnabled() && properties.getPinyin().getFile() != null) {
            loadPinyinDictionary(properties.getPinyin().getFile());
        }

        log.info("[SearchTextProcessor] 初始化完成: synonyms={}, stopWords={}, pinyin={}",
                synonymMap.size(), stopWords.size(), pinyinMap.size());
    }

    /**
     * 处理搜索关键词
     * <p>
     * 1. 过滤停用词
     * 2. 扩展同义词
     * 3. 拼音转换（可选）
     *
     * @param keyword 原始关键词
     * @return 处理后的关键词（可能包含多个词，用空格连接）
     */
    public String process(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }

        String result = keyword.trim();

        // 停用词过滤（仅对长词过滤，短词保留）
        if (result.length() > 2) {
            result = filterStopWords(result);
        }

        // 同义词扩展
        if (properties.getSynonym().isEnabled()) {
            result = expandSynonyms(result);
        }

        // 拼音转换（简单实现：保留原始中文 + 追加拼音）
        if (properties.getPinyin().isEnabled()) {
            String pinyin = toPinyin(keyword);
            if (pinyin != null && !pinyin.isBlank() && !pinyin.equals(keyword)) {
                result = result + " " + pinyin;
            }
        }

        return result;
    }

    /**
     * 加载同义词词典
     * <p>
     * 词典格式（每行一组同义词，用逗号分隔）：
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
                if (line.isEmpty() || line.startsWith("#")) continue;

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
        } catch (IOException e) {
            log.warn("[SearchTextProcessor] 同义词词典加载失败: {}", e.getMessage());
        } catch (NullPointerException e) {
            log.warn("[SearchTextProcessor] 同义词词典文件不存在: {}", filePath);
        }
    }

    /**
     * P1-5: 加载拼音词典
     * <p>
     * 词典格式（每行一个映射）：汉字=拼音
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
                if (line.isEmpty() || line.startsWith("#")) continue;
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

    private String filterStopWords(String text) {
        String result = text;
        for (String stopWord : stopWords) {
            result = result.replace(stopWord, " ");
        }
        return result.replaceAll("\\s+", " ").trim();
    }

    private String expandSynonyms(String text) {
        StringBuilder result = new StringBuilder(text);
        String lowerText = text.toLowerCase();
        for (Map.Entry<String, List<String>> entry : synonymMap.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                for (String synonym : entry.getValue()) {
                    if (!synonym.equalsIgnoreCase(text)) {
                        result.append(" ").append(synonym);
                    }
                }
            }
        }
        return result.toString();
    }

    /**
     * P1-5: 拼音转换 — 基于词典文件加载汉字到拼音的映射
     * <p>
     * 词典格式（每行一个映射）：汉字=拼音
     * 如果词典未加载，返回原始文本（拼音功能降级）。
     */
    private final Map<String, String> pinyinMap = new HashMap<>();

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
