package com.njydsz.pmis.common.redis.ops;

import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Geo 操作组件
 *
 * <p>封装地理位置相关操作，支持 nearby 查询、距离计算等。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Component
public class GeoOps {

    private final StringRedisTemplate redis;

    public GeoOps(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 添加地理位置
     *
     * @param key     Geo 集合键
     * @param member  成员标识
     * @param longitude 经度
     * @param latitude  纬度
     * @return 新增数量
     */
    public long add(String key, String member, double longitude, double latitude) {
        Long count = redis.opsForGeo().add(key, new Point(longitude, latitude), member);
        return count != null ? count : 0;
    }

    /**
     * 获取成员的经纬度
     */
    public Point position(String key, String member) {
        List<Point> points = redis.opsForGeo().position(key, member);
        return (points != null && !points.isEmpty()) ? points.get(0) : null;
    }

    /**
     * 计算两个成员之间的距离（米）
     */
    public Distance distance(String key, String member1, String member2) {
        return redis.opsForGeo().distance(key, member1, member2, MetricsUnit.KILOMETERS);
    }

    /**
     * 查询指定坐标半径范围内的成员
     *
     * @param key       Geo 集合键
     * @param longitude 中心经度
     * @param latitude  中心纬度
     * @param radius    半径（千米）
     * @param limit     最大返回数量
     * @return 成员列表（含距离）
     */
    public List<RedisGeoCommands.GeoLocation<String>> radius(String key,
                                                               double longitude, double latitude,
                                                               double radius, int limit) {
        Circle circle = new Circle(new Point(longitude, latitude),
                new Distance(radius, MetricsUnit.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeCoordinates()
                .sortAscending()
                .limit(limit);
        return redis.opsForGeo().radius(key, circle, args).getContent()
                .stream()
                .map(r -> new RedisGeoCommands.GeoLocation<>(r.getContent().getName(), r.getContent().getPoint()))
                .toList();
    }

    /**
     * 查询指定成员半径范围内的其他成员
     *
     * @param key    Geo 集合键
     * @param member 中心成员
     * @param radius 半径（千米）
     * @param limit  最大返回数量
     * @return 成员列表
     */
    public List<String> radiusByMember(String key, String member, double radius, int limit) {
        Distance distance = new Distance(radius, MetricsUnit.KILOMETERS);
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .sortAscending()
                .limit(limit);
        return redis.opsForGeo().radius(key, member, distance, args)
                .getContent()
                .stream()
                .map(r -> r.getContent().getName())
                .toList();
    }

    /**
     * 移除成员
     */
    public long remove(String key, String... members) {
        Long count = redis.opsForGeo().remove(key, (Object[]) members);
        return count != null ? count : 0;
    }
}
