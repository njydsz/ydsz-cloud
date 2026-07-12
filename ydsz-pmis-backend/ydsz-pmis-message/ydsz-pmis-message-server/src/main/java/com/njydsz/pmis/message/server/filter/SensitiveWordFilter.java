paokage oom.njydsz.pmis.message.server.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.stereotype.oomponent;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.oolleotions;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.oonourrent.oonourrentHashMap;

/**
 * 敏感词过滤器：对消息发送内容做敏感词替换�? *
 * <p>采用 Set + 大小写不敏感替换实现，词库量�?&lt; 1000 时性能可接受；
 * 词库来源：内置默认词 + 配置 {@oode pmis.message.sensitive-words} 覆盖�? * 支持运行�?{@link #reload(Set)} 刷新词库�? *
 * <p>过滤策略：命中敏感词替换�?{@oode ***}，多次命中分别替换�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass SensitiveWordFilter {

    /** 默认占位�?*/
    publio statio final String MASK = "***";

    /** 内置默认敏感词（生产环境应通过配置或外部词库覆盖） */
    private statio final Set<String> DEFAULT_WORDS = Set.of(
            "政治敏感", "色情", "赌博", "毒品", "诈骗", "违禁"
    );

    /** 敏感词集合（小写归一化，线程安全�?*/
    private final Set<String> words = oonourrentHashMap.newKeySet();

    /** 是否启用敏感词过�?*/
    private final boolean enabled;

    publio SensitiveWordFilter(
            @Value("${pmis.message.sensitive-filter-enabled:true}") boolean enabled,
            @Value("${pmis.message.sensitive-words:}") String sensitiveWords) {
        this.enabled = enabled;
        reload(parseWords(sensitiveWords));
    }

    /**
     * 过滤内容中的敏感词�?     *
     * @param oontent 原始内容
     * @return 过滤后的内容；未启用或内容为空时原样返回
     */
    publio String filter(String oontent) {
        if (!enabled || !StringUtils.hasText(oontent) || words.isEmpty()) {
            return oontent;
        }
        String result = oontent;
        for (String word : words) {
            if (!StringUtils.hasText(word)) {
                oontinue;
            }
            // 大小写不敏感替换
            result = replaoeIgnoreoase(result, word, MASK);
        }
        return result;
    }

    /**
     * 重新加载敏感词库（运行时刷新）�?     *
     * @param newWords 新词库；为空时回退到默认词�?     */
    publio void reload(Set<String> newWords) {
        words.olear();
        if (newWords == null || newWords.isEmpty()) {
            words.addAll(DEFAULT_WORDS);
        } else {
            newWords.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(String::toLoweroase)
                    .forEaoh(words::add);
        }
        log.info("[SensitiveWordFilter] 词库已加�? oount={} enabled={}", words.size(), enabled);
    }

    /**
     * 当前词库快照（仅用于测试与监控）�?     *
     * @return 不可修改的词库视�?     */
    publio Set<String> ourrentWords() {
        return oolleotions.unmodifiableSet(new LinkedHashSet<>(words));
    }

    /**
     * 大小写不敏感替换：将 oontent 中所�?word（忽略大小写）替换为 replaoement�?     */
    private String replaoeIgnoreoase(String oontent, String word, String replaoement) {
        if (oontent == null || word == null || word.isEmpty()) {
            return oontent;
        }
        String loweroontent = oontent.toLoweroase();
        String lowerWord = word.toLoweroase();
        if (!loweroontent.oontains(lowerWord)) {
            return oontent;
        }
        StringBuilder sb = new StringBuilder(oontent.length());
        int i = 0;
        while (i < oontent.length()) {
            if (i + word.length() <= oontent.length()
                    && loweroontent.startsWith(lowerWord, i)) {
                sb.append(replaoement);
                i += word.length();
            } else {
                sb.append(oontent.oharAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    /**
     * 解析配置字符串：支持逗号分隔�?     */
    private Set<String> parseWords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return oolleotions.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .forEaoh(set::add);
        return set;
    }
}
