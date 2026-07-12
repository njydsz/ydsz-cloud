package com.njydsz.pmis.common.redis.service.ops;

import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.pmis.common.util.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Redis Geo + HyperLogLog 操作服务
 *
 * <p>提供 Geo 地理位置操作和 HyperLogLog 基数估算操作
 *
 * 集成 {@link RedisMetricsCollector} 进行操作指标采集，与 {@link RedisStringOps} 保持一致。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGeoOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetricsCollector metricsCollector;

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null) {
            return null;
        }
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }

    /**
     * 批量格式化 Keys
     */
    private String[] formatKeys(String... keys) {
        if (keys == null) {
            return new String[0];
        }
        return Arrays.stream(keys).map(this::formatKey).toArray(String[]::new);
    }

    // ============================ Geo 地理位置操作 =============================

    /**
     * 添加地理位置
     *
     * @param key        键
     * @param member     成员
     * @param longitude  经度
     * @param latitude   纬度
     * @return true-添加成功
     */
    public boolean geoAdd(String key, Object member, double longitude, double latitude) {
        if (key == null || member == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("geoAdd", () -> {
                        Long result = redisTemplate.opsForGeo().add(formattedKey, new Point(longitude, latitude), member);
                        return result != null && result > 0;
                    })
                    : Optional.ofNullable(redisTemplate.opsForGeo().add(formattedKey, new Point(longitude, latitude), member))
                        .map(r -> r > 0).orElse(false);
        } catch (Exception e) {
            recordError("geoAdd", e);
            log.error("【Redis】GEOADD 操作失败 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 获取两个成员之间的距离
     *
     * @param key     键
     * @param member1 成员1
     * @param member2 成员2
     * @param unit    距离单位（使用 Metrics.KILOMETERS 等）
     * @return 距离
     */
    public Distance geoDistance(String key, Object member1, Object member2, Metrics unit) {
        if (key == null || member1 == null || member2 == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("geoDistance", () -> redisTemplate.opsForGeo().distance(formattedKey, member1, member2, unit))
                    : redisTemplate.opsForGeo().distance(formattedKey, member1, member2, unit);
        } catch (Exception e) {
            recordError("geoDistance", e);
            log.error("【Redis】GEODIST 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 获取成员的位置
     *
     * @param key    键
     * @param member 成员
     * @return 位置坐标
     */
    public Point geoPosition(String key, Object member) {
        if (key == null || member == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("geoPosition", () -> {
                        List<Point> positions = redisTemplate.opsForGeo().position(formattedKey, member);
                        if (positions == null || positions.isEmpty()) {
                            return null;
                        }
                        return positions.get(0);
                    })
                    : Optional.ofNullable(redisTemplate.opsForGeo().position(formattedKey, member))
                        .filter(CollectionUtils::isNotEmpty).map(list -> list.get(0)).orElse(null);
        } catch (Exception e) {
            recordError("geoPosition", e);
            log.error("【Redis】GEOPOS 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 根据坐标获取指定范围内的成员
     *
     * @param key       键
     * @param longitude 经度
     * @param latitude  纬度
     * @param radius    半径
     * @param unit      距离单位（使用 Metrics.KILOMETERS 等）
     * @return 成员列表
     */
    public GeoResults<RedisGeoCommands.GeoLocation<Object>> geoRadius(String key, double longitude, double latitude, double radius, Metrics unit) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("geoRadius", () -> {
                        Circle circle = new Circle(new Point(longitude, latitude), new Distance(radius, unit));
                        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()
                                .includeCoordinates()
                                .sortAscending();
                        return redisTemplate.opsForGeo().radius(formattedKey, circle, args);
                    })
                    : redisTemplate.opsForGeo().radius(formattedKey,
                        new Circle(new Point(longitude, latitude), new Distance(radius, unit)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance().includeCoordinates().sortAscending());
        } catch (Exception e) {
            recordError("geoRadius", e);
            log.error("【Redis】GEORADIUS 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    // ============================ HyperLogLog 操作 =============================

    /**
     * 添加 HyperLogLog 元素
     *
     * @param key    键
     * @param values 元素数组
     * @return true-添加成功
     */
    public boolean pfAdd(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("pfAdd", () -> {
                        Long result = redisTemplate.opsForHyperLogLog().add(formattedKey, values);
                        return result != null && result > 0;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHyperLogLog().add(formattedKey, values))
                        .map(r -> r > 0).orElse(false);
        } catch (Exception e) {
            recordError("pfAdd", e);
            log.error("【Redis】PFADD 操作失败 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 获取 HyperLogLog 的基数估算值
     *
     * @param keys 键数组
     * @return 基数估算值
     */
    public long pfCount(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0;
        }
        String[] formattedKeys = formatKeys(keys);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("pfCount", () -> {
                        Long count = redisTemplate.opsForHyperLogLog().size(formattedKeys);
                        return count != null ? count : 0;
                    })
                    : Optional.ofNullable(redisTemplate.opsForHyperLogLog().size(formattedKeys)).orElse(0L);
        } catch (Exception e) {
            recordError("pfCount", e);
            log.error("【Redis】PFCOUNT 操作失败 | keys={} | error={}", Arrays.toString(keys), e);
            return 0;
        }
    }

    /**
     * 合并多个 HyperLogLog
     *
     * @param destination 目标键
     * @param sources     源键数组
     * @return true-合并成功
     */
    public boolean pfMerge(String destination, String... sources) {
        if (destination == null || sources == null || sources.length == 0) {
            return false;
        }
        String formattedDest = formatKey(destination);
        String[] formattedSources = formatKeys(sources);
        try {
            if (metricsCollector != null) {
                metricsCollector.recordOperation("pfMerge", () -> redisTemplate.opsForHyperLogLog().union(formattedDest, formattedSources));
            } else {
                redisTemplate.opsForHyperLogLog().union(formattedDest, formattedSources);
            }
            return true;
        } catch (Exception e) {
            recordError("pfMerge", e);
            log.error("【Redis】PFMERGE 操作失败 | destination={} | error={}", destination, e);
            return false;
        }
    }

    /**
     * 记录指标错误
     */
    private void recordError(String operationType, Throwable e) {
        if (metricsCollector != null) {
            metricsCollector.recordError(operationType, e);
        }
    }
}
