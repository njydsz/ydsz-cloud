package com.njydsz.pmis.common.cache.metrics;

import com.njydsz.pmis.common.cache.spring.SpringYdszCache;
import com.njydsz.pmis.common.cache.spring.YdszCacheManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 缓存可观测性自动配置
 *
 * <p>当 classpath 中存在 Micrometer 和 SpringYdszCache 时，
 * 自动为每个 SpringYdszCache 注册 {@link CacheMeterBinder} 指标。
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class CacheMetricsAutoConfiguration {

    @Bean
    public List<MeterBinder> remiCacheMeterBinders(YdszCacheManager cacheManager) {
        return cacheManager.getCacheNames().stream()
                .map(cacheManager::getCache)
                .map(springCache -> (MeterBinder) new CacheMeterBinder(
                        springCache.getNativeCache(),
                        springCache.getName()))
                .toList();
    }
}
