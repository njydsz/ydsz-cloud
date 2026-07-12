package com.njydsz.pmis.common.feign.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Feign 閰嶇疆鍒锋柊鐩戝惉鍣ㄣ€?
 *
 * <p>鐩戝惉 Spring Cloud 鐨?{@link EnvironmentChangeEvent} 浜嬩欢锛?
 * 褰?Feign 鐩稿叧閰嶇疆鍙戠敓鍙樺寲鏃讹紝鑷姩閲嶅缓 Feign 瀹㈡埛绔疄渚嬨€?
 *
 * <p>宸ヤ綔鍘熺悊锛?
 * <ol>
 *   <li>鐩戝惉鐜閰嶇疆鍙樻洿浜嬩欢</li>
 *   <li>鍒ゆ柇鍙樻洿鐨勯厤缃槸鍚︿笌 Feign 鐩稿叧</li>
 *   <li>濡傛灉鏄紝鍒欓€氳繃 {@link DynamicFeignClientFactory} 閲嶅缓瀹㈡埛绔?/li>
 * </ol>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see DynamicFeignClientFactory
 * @see FeignProperties
 */
@Slf4j
@Order(100)
public class FeignConfigRefresher implements ApplicationListener<EnvironmentChangeEvent> {

    private static final String FEIGN_CONFIG_PREFIX = "remi.feign";

    private final ApplicationContext applicationContext;
    private final DynamicFeignClientFactory clientFactory;

    /**
     * 鏋勯€犲嚱鏁般€?
     *
     * @param applicationContext Spring 搴旂敤涓婁笅鏂?
     * @param clientFactory      鍔ㄦ€?Feign 瀹㈡埛绔伐鍘?
     */
    public FeignConfigRefresher(ApplicationContext applicationContext,
                                DynamicFeignClientFactory clientFactory) {
        this.applicationContext = applicationContext;
        this.clientFactory = clientFactory;
    }

    /**
     * 澶勭悊鐜閰嶇疆鍙樻洿浜嬩欢锛屽綋 Feign 鐩稿叧閰嶇疆鍙戠敓鍙樺寲鏃跺埛鏂板鎴风銆?
     *
     * @param event 鐜閰嶇疆鍙樻洿浜嬩欢
     */
    @Override
    public void onApplicationEvent(@NonNull EnvironmentChangeEvent event) {
        FeignProperties properties = applicationContext.getBean(FeignProperties.class);
        if (!properties.getRefresh().isEnabled()) {
            log.debug("[Feign] 鍔ㄦ€佸埛鏂版湭鍚敤锛岃烦杩囬厤缃埛鏂?);
            return;
        }

        Set<String> changedKeys = event.getKeys();
        Set<String> relevantKeys = filterRelevantKeys(changedKeys);
        if (relevantKeys.isEmpty()) {
            return;
        }

        log.info("[Feign] 妫€娴嬪埌 Feign 閰嶇疆鍙樻洿锛屽紑濮嬪埛鏂? {}", relevantKeys);
        refreshFeignClients(relevantKeys, new HashSet<>(properties.getRefresh().getExclude()));
    }

    /**
     * 杩囨护鍑轰笌 Feign 鐩稿叧鐨勯厤缃彉鏇撮敭銆?
     *
     * @param keys 鎵€鏈夊彉鏇寸殑閰嶇疆閿?
     * @return 涓?Feign 鐩稿叧鐨勯厤缃敭闆嗗悎
     */
    private Set<String> filterRelevantKeys(Set<String> keys) {
        Set<String> relevant = new HashSet<>();
        for (String key : keys) {
            if (key.startsWith(FEIGN_CONFIG_PREFIX)) {
                relevant.add(key);
            }
        }
        return relevant;
    }

    /**
     * 鍒锋柊 Feign 瀹㈡埛绔€?
     *
     * @param changedKeys 鍙樻洿鐨勯厤缃敭
     * @param excludeSet 鎺掗櫎鐨勫鎴风鍚嶇О闆嗗悎
     */
    private void refreshFeignClients(Set<String> changedKeys, Set<String> excludeSet) {
        // 娓呴櫎缂撳瓨骞堕噸寤?Feign Builder
        clientFactory.clearCache(excludeSet);

        log.info("[Feign] Feign 瀹㈡埛绔埛鏂板畬鎴愶紝鍙楀奖鍝嶇殑閰嶇疆: {}", changedKeys);
    }
}
