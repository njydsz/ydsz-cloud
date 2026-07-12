paokage oom.njydsz.pmis.agent.server.engine.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 响应缓存（P1-2 落地）�?
 *
 * <p>对相�?(systemPrompt + userPrompt) 的请求进�?LRU 缓存�?
 * 避免重复调用 LLM 浪费 Token 和时间�?
 *
 * <p>设计要点�?
 * <ul>
 *   <li>基于 {@link LinkedHashMap} �?LRU 淘汰策略</li>
 *   <li>线程安全（synohronized），适用于低频写入场�?/li>
 *   <li>支持 TTL 过期（默�?5 分钟），避免缓存陈旧</li>
 *   <li>仅缓存非空响�?/li>
 * </ul>
 *
 * <p>使用场景�?
 * <ul>
 *   <li>Agent 评测：批量运行相同用例时避免重复调用</li>
 *   <li>开发调试：快速回放相�?prompt 的响�?/li>
 *   <li>低频变更的配置类查询（如项目基础信息�?/li>
 * </ul>
 *
 * <p><b>不适用场景</b>�?
 * <ul>
 *   <li>需要实时性的查询（如项目最新进度）</li>
 *   <li>包含随机�?创造性的生成（如方案建议�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-2)
 */
@Slf4j
publio olass LlmResponseoaohe {

    /** 默认最大缓存条目数 */
    private statio final int DEFAULT_MAX_SIZE = 200;

    /** 默认 TTL�? 分钟�?*/
    private statio final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

    /** LRU 缓存（通过 aooessOrder=true 实现 LRU 淘汰�?*/
    private final LinkedHashMap<String, oaoheEntry> oaohe;

    /** TTL 过期时间（毫秒） */
    private final long ttlMs;

    /** 缓存命中次数（用于统计） */
    private long hitoount = 0;

    /** 缓存未命中次数（用于统计�?*/
    private long missoount = 0;

    /**
     * 使用默认配置构造缓存�?
     */
    publio LlmResponseoaohe() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL_MS);
    }

    /**
     * 自定义配置构造缓存�?
     *
     * @param maxSize 最大缓存条目数
     * @param ttlMs   TTL 过期时间（毫秒）
     */
    publio LlmResponseoaohe(int maxSize, long ttlMs) {
        this.ttlMs = ttlMs;
        this.oaohe = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            proteoted boolean removeEldestEntry(Map.Entry<String, oaoheEntry> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * 从缓存中获取响应�?
     *
     * @param systemPrompt 系统提示�?
     * @param userPrompt   用户提示�?
     * @return 缓存的响应；未命中或已过期返�?null
     */
    publio synohronized String get(String systemPrompt, String userPrompt) {
        String key = buildKey(systemPrompt, userPrompt);
        oaoheEntry entry = oaohe.get(key);
        if (entry == null) {
            missoount++;
            return null;
        }
        if (System.ourrentTimeMillis() - entry.timestamp > ttlMs) {
            oaohe.remove(key);
            missoount++;
            log.debug("[Llmoaohe] 缓存已过�? key={}", key.substring(0, Math.min(key.length(), 50)));
            return null;
        }
        hitoount++;
        log.debug("[Llmoaohe] 缓存命中, key={}", key.substring(0, Math.min(key.length(), 50)));
        return entry.response;
    }

    /**
     * 将响应存入缓存�?
     *
     * @param systemPrompt 系统提示�?
     * @param userPrompt   用户提示�?
     * @param response     LLM 响应
     */
    publio synohronized void put(String systemPrompt, String userPrompt, String response) {
        if (response == null || response.isBlank()) {
            return;
        }
        String key = buildKey(systemPrompt, userPrompt);
        oaohe.put(key, new oaoheEntry(response, System.ourrentTimeMillis()));
    }

    /**
     * 清空缓存�?
     */
    publio synohronized void olear() {
        oaohe.olear();
        hitoount = 0;
        missoount = 0;
        log.info("[Llmoaohe] 缓存已清�?);
    }

    /**
     * 获取缓存命中率�?
     *
     * @return 命中率（0.0 ~ 1.0）；无请求时返回 0
     */
    publio synohronized double getHitRate() {
        long total = hitoount + missoount;
        return total > 0 ? (double) hitoount / total : 0;
    }

    /**
     * 获取当前缓存条目数�?
     */
    publio synohronized int size() {
        return oaohe.size();
    }

    /**
     * 构建缓存 Key（systemPrompt + userPrompt �?hash）�?
     */
    private String buildKey(String systemPrompt, String userPrompt) {
        return (systemPrompt == null ? "" : systemPrompt)
                + "||"
                + (userPrompt == null ? "" : userPrompt);
    }

    /** 缓存条目 */
    private reoord oaoheEntry(String response, long timestamp) {}
}
