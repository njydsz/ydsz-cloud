package com.njydsz.pmis.message.server.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感词过滤器：对消息发送内容做敏感词替换。
 *
 * <p>采用 Set + 大小写不敏感替换实现，词库量级 &lt; 1000 时性能可接受；
 * 词库来源：内置默认词 + 配置 {@code pmis.message.sensitive-words} 覆盖。
 * 支持运行时 {@link #reload(Set)} 刷新词库。
 *
 * <p>过滤策略：命中敏感词替换为 {@code ***}，多次命中分别替换。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** 默认占位符 */
    public static final String MASK = "***";

    /** 内置默认敏感词（生产环境应通过配置或外部词库覆盖） */
    private static final Set<String> DEFAULT_WORDS = Set.of(
            "政治敏感", "色情", "赌博", "毒品", "诈骗", "违禁"
    );

    /** 敏感词集合（小写归一化，线程安全） */
    private final Set<String> words = ConcurrentHashMap.newKeySet();

    /** 是否启用敏感词过滤 */
    private final boolean enabled;

    public SensitiveWordFilter(
            @Value("${pmis.message.sensitive-filter-enabled:true}") boolean enabled,
            @Value("${pmis.message.sensitive-words:}") String sensitiveWords) {
        this.enabled = enabled;
        reload(parseWords(sensitiveWords));
    }

    /**
     * 过滤内容中的敏感词。
     *
     * @param content 原始内容
     * @return 过滤后的内容；未启用或内容为空时原样返回
     */
    public String filter(String content) {
        if (!enabled || !StringUtils.hasText(content) || words.isEmpty()) {
            return content;
        }
        String result = content;
        for (String word : words) {
            if (!StringUtils.hasText(word)) {
                continue;
            }
            // 大小写不敏感替换
            result = replaceIgnoreCase(result, word, MASK);
        }
        return result;
    }

    /**
     * 重新加载敏感词库（运行时刷新）。
     *
     * @param newWords 新词库；为空时回退到默认词库
     */
    public void reload(Set<String> newWords) {
        words.clear();
        if (newWords == null || newWords.isEmpty()) {
            words.addAll(DEFAULT_WORDS);
        } else {
            newWords.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .forEach(words::add);
        }
        log.info("[SensitiveWordFilter] 词库已加载: count={} enabled={}", words.size(), enabled);
    }

    /**
     * 当前词库快照（仅用于测试与监控）。
     *
     * @return 不可修改的词库视图
     */
    public Set<String> currentWords() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(words));
    }

    /**
     * 大小写不敏感替换：将 content 中所有 word（忽略大小写）替换为 replacement。
     */
    private String replaceIgnoreCase(String content, String word, String replacement) {
        if (content == null || word == null || word.isEmpty()) {
            return content;
        }
        String lowerContent = content.toLowerCase();
        String lowerWord = word.toLowerCase();
        if (!lowerContent.contains(lowerWord)) {
            return content;
        }
        StringBuilder sb = new StringBuilder(content.length());
        int i = 0;
        while (i < content.length()) {
            if (i + word.length() <= content.length()
                    && lowerContent.startsWith(lowerWord, i)) {
                sb.append(replacement);
                i += word.length();
            } else {
                sb.append(content.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 解析配置字符串：支持逗号分隔。
     */
    private Set<String> parseWords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEach(set::add);
        return set;
    }
}
