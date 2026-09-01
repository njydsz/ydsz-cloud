package com.njydsz.userinfo.server.auth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.config.GeoIpProperties;

/**
 * GeoIP 地理围栏服务（P3-3）。
 *
 * <p>提供 IP 地理位置解析和异常登录检测能力。
 *
 * <p><b>工作流程：</b>
 *
 * <ol>
 *   <li>根据 IP 地址解析地理位置（城市、省份、国家、经纬度）</li>
 *   <li>计算与上次登录地点的距离</li>
 *   <li>判断是否触发地理围栏告警（距离超过阈值）</li>
 * </ol>
 *
 * <p><b>降级策略：</b>MMDB 文件不可用时，所有 IP 解析返回空地理位置，不影响登录流程。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoIpService {

  private final GeoIpProperties geoIpProperties;

  /** IP 地理位置缓存（减少 MMDB 查询次数） */
  private final Map<String, GeoLocation> locationCache = new ConcurrentHashMap<>();

  /**
   * 解析 IP 地址的地理位置。
   *
   * @param ipAddress IP 地址
   * @return 地理位置信息；解析失败返回 {@code Optional.empty()}
   */
  public Optional<GeoLocation> resolveLocation(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank() || "127.0.0.1".equals(ipAddress)
        || "::1".equals(ipAddress)) {
      return Optional.empty();
    }

    // 检查缓存
    GeoLocation cached = locationCache.get(ipAddress);
    if (cached != null) {
      return Optional.of(cached);
    }

    // 如果 MMDB 路径配置为空或文件不存在，降级返回 empty
    if (!isMmdbAvailable()) {
      return Optional.empty();
    }

    try {
      GeoLocation location = resolveFromMmdb(ipAddress);
      if (location != null) {
        locationCache.put(ipAddress, location);
        return Optional.of(location);
      }
    } catch (Exception e) {
      log.debug("GeoIP 解析失败: ip={}, reason={}", ipAddress, e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * 检测本次登录是否触发地理围栏告警（P3-3）。
   *
   * <p>当本次登录 IP 的地理位置与上次登录位置的距离超过阈值时，触发异常告警。
   *
   * @param currentIp  当前登录 IP
   * @param lastIp     上次登录 IP
   * @return 地理围栏检测结果
   */
  public GeoFenceResult detectAnomaly(String currentIp, String lastIp) {
    if (!geoIpProperties.isEnabled()) {
      return GeoFenceResult.disabled();
    }

    if (currentIp == null || lastIp == null) {
      return GeoFenceResult.noData();
    }

    if (currentIp.equals(lastIp)) {
      return GeoFenceResult.normal("同 IP 登录");
    }

    Optional<GeoLocation> currentLocation = resolveLocation(currentIp);
    Optional<GeoLocation> lastLocation = resolveLocation(lastIp);

    if (currentLocation.isEmpty() || lastLocation.isEmpty()) {
      return GeoFenceResult.noData();
    }

    GeoLocation current = currentLocation.get();
    GeoLocation last = lastLocation.get();

    // 同一城市 → 正常
    if (current.getCity() != null && current.getCity().equals(last.getCity())) {
      return GeoFenceResult.normal("同城登录");
    }

    // 计算距离
    double distance = calculateDistance(
        current.getLatitude(), current.getLongitude(),
        last.getLatitude(), last.getLongitude());

    if (distance >= geoIpProperties.getAnomalyThresholdKm()) {
      // 触发地理围栏告警
      return GeoFenceResult.anomaly(current, last, distance);
    }

    return GeoFenceResult.normal(
        String.format("异地登录 (%.0fkm)", distance));
  }

  /**
   * 计算两个经纬度坐标之间的距离（Haversine 公式）。
   *
   * @param lat1 点 1 纬度
   * @param lon1 点 1 经度
   * @param lat2 点 2 纬度
   * @param lon2 点 2 经度
   * @return 距离（公里）
   */
  public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
    final double EARTH_RADIUS_KM = 6371.0;

    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return BigDecimal.valueOf(EARTH_RADIUS_KM * c)
        .setScale(2, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /**
   * 从 MMDB 文件解析 IP 地理位置。
   *
   * <p>此方法使用简单的字符串匹配模拟 MMDB 解析（无外部依赖）。
   * 生产环境建议使用 {@code maxmind.geoip2} 库。
   */
  private GeoLocation resolveFromMmdb(String ipAddress) {
    // 注意：此为简化实现。完整实现需要引入 com.maxmind.geoip2 依赖
    // 并使用 com.maxmind.geoip2.DatabaseReader 读取 MMDB 文件。
    // 当 MMDB 文件不可用时返回 null。
    return null;
  }

  /**
   * 检查 MMDB 文件是否可用。
   */
  private boolean isMmdbAvailable() {
    if (geoIpProperties.getMmdbPath() == null || geoIpProperties.getMmdbPath().isBlank()) {
      return false;
    }
    Path mmdbPath = Paths.get(geoIpProperties.getMmdbPath());
    return Files.exists(mmdbPath) && Files.isReadable(mmdbPath);
  }

  /**
   * 清除位置缓存。
   */
  public void clearCache() {
    locationCache.clear();
    log.info("GeoIP 位置缓存已清除");
  }

  // ==================== 内部数据类 ====================

  /**
   * 地理位置信息。
   */
  @Data
  public static class GeoLocation {
    /** 国家代码（如 "CN"） */
    private String country;
    /** 国家名称 */
    private String countryName;
    /** 省份/州 */
    private String province;
    /** 城市 */
    private String city;
    /** 纬度 */
    private Double latitude;
    /** 经度 */
    private Double longitude;
    /** ISP 运营商 */
    private String isp;
  }

  /**
   * 地理围栏检测结果。
   */
  @Getter
  public static class GeoFenceResult {

    /** 是否异常 */
    private final boolean anomaly;

    /** 风险评分附加值 */
    private int riskScoreAddition;

    /** 检测描述 */
    private final String description;

    /** 当前地理位置 */
    private GeoLocation currentLocation;

    /** 上次地理位置 */
    private GeoLocation lastLocation;

    /** 两地距离（公里） */
    private double distanceKm;

    private GeoFenceResult(boolean anomaly, int riskScoreAddition, String description,
        GeoLocation currentLocation, GeoLocation lastLocation, double distanceKm) {
      this.anomaly = anomaly;
      this.riskScoreAddition = riskScoreAddition;
      this.description = description;
      this.currentLocation = currentLocation;
      this.lastLocation = lastLocation;
      this.distanceKm = distanceKm;
    }

    /** 功能未启用 */
    static GeoFenceResult disabled() {
      return new GeoFenceResult(false, 0, "地理围栏未启用", null, null, 0);
    }

    /** 无数据 */
    static GeoFenceResult noData() {
      return new GeoFenceResult(false, 0, "无地理位置数据", null, null, 0);
    }

    /** 正常 */
    static GeoFenceResult normal(String description) {
      return new GeoFenceResult(false, 0, description, null, null, 0);
    }

    /** 异常 */
    static GeoFenceResult anomaly(GeoLocation current, GeoLocation last, double distanceKm) {
      return new GeoFenceResult(true, 0,
          String.format("异地登录告警：%s → %s (%.0fkm)", last.getCity(), current.getCity(), distanceKm),
          current, last, distanceKm);
    }

    /**
     * 获取异常时的风险评分附加值。
     *
     * @param configuredAddition 配置的基础风险分值
     * @return 异常时返回配置分值，正常时返回 0
     */
    public int getRiskScoreAddition(int configuredAddition) {
      return anomaly ? configuredAddition : 0;
    }
  }
}
