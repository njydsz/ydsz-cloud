package com.njydsz.system.web.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * 运维指标仪表盘 REST 控制器。
 *
 * <p>聚合 Nacos 注册中心服务列表、Spring Boot 运行环境信息、
 * Redis 缓存命中率采样等数据，为前端 {@code /system/monitor/dashboard}
 * 页面提供一站式运维指标 JSON 接口。
 *
 * <h3>数据源</h3>
 * <ul>
 *   <li>{@link DiscoveryClient}: Nacos 注册中心实时服务实例列表
 *   <li>{@link Environment}: Spring 环境属性（应用名 / 端口 / 版本）
 *   <li>{@link StringRedisTemplate}: Redis INFO 统计（命令数 / 连接数 / 命中/未命中）
 * </ul>
 *
 * <h3>接口清单</h3>
 * <ul>
 *   <li>{@code GET /api/system/metrics/dashboard} — 完整仪表盘数据
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@ApiVersion("26.09.08")
@Slf4j
@RestController
@RequestMapping("/api/system/metrics")
@RequiredArgsConstructor
public class MetricsDashboardController {

  /** Nacos 服务发现客户端 */
  private final DiscoveryClient discoveryClient;

  /** Spring 运行环境（读取应用名 / 端口等） */
  private final Environment environment;

  /** Redis 操作模板（可选：未装配时跳过 Redis 指标采集） */
  private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

  /** 日期时间格式化器 */
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * 获取运维仪表盘完整数据。
   *
   * <p>返回结构包含：
   * <ul>
   *   <li>{@code services} — Nacos 已注册服务及其实例列表（ID / 主机 / 端口 / 状态）
   *   <li>{@code redis} — Redis 关键指标（总命令数 / 命中数 / 未命中数 / 连接数 / 命中率）
   *   <li>{@code runtime} — 运行时信息（Java 版本 / Spring Boot 版本 / 应用名 / 端口 / 内存）
   *   <li>{@code summary} — 汇总计数（总服务数 / UP 数 / DOWN 数）
   *   <li>{@code collectedAt} — 数据采集时间
   * </ul>
   *
   * @return 仪表盘数据
   */
  @GetMapping("/dashboard")
  public YdszResponse<Map<String, Object>> dashboard() {
    log.debug("[MetricsDashboard] 采集运维指标数据");

    Map<String, Object> result = new LinkedHashMap<>(8);

    // 1. Nacos 注册中心服务实例列表
    List<Map<String, Object>> serviceList = collectServices();
    result.put("services", serviceList);

    // 2. Redis 关键指标（Redis 未装配时降级返回 null）
    result.put("redis", collectRedisMetrics());

    // 3. 运行时信息
    result.put("runtime", collectRuntimeInfo());

    // 4. 汇总计数
    Map<String, Integer> summary = new LinkedHashMap<>(4);
    int totalServices = serviceList.size();
    int upServices = (int) serviceList.stream()
        .filter(s -> "UP".equals(s.get("status")))
        .count();
    summary.put("totalServices", totalServices);
    summary.put("upServices", upServices);
    summary.put("downServices", totalServices - upServices);
    result.put("summary", summary);

    // 5. 采集时间戳
    result.put("collectedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));

    return YdszResponse.success(result);
  }

  /**
   * 采集 Nacos 注册中心服务及其实例详情。
   *
   * <p>服务无实例时标记为 DOWN，否则取首个实例作为代表。
   *
   * @return 服务列表
   */
  private List<Map<String, Object>> collectServices() {
    List<Map<String, Object>> serviceList = new ArrayList<>(16);

    List<String> services = discoveryClient.getServices();
    if (services == null || services.isEmpty()) {
      return serviceList;
    }

    for (String serviceId : services) {
      List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
      if (instances == null || instances.isEmpty()) {
        // 服务注册过但当前无实例
        Map<String, Object> svcInfo = new LinkedHashMap<>(8);
        svcInfo.put("serviceId", serviceId);
        svcInfo.put("status", "DOWN");
        svcInfo.put("instanceCount", 0);
        serviceList.add(svcInfo);
        continue;
      }

      // Nacos 仅注册健康实例，有实例即视为 UP
      ServiceInstance primary = instances.get(0);
      Map<String, Object> svcInfo = new LinkedHashMap<>(8);
      svcInfo.put("serviceId", serviceId);
      svcInfo.put("status", "UP");
      svcInfo.put("instanceCount", instances.size());
      svcInfo.put("host", primary.getHost());
      svcInfo.put("port", primary.getPort());
      serviceList.add(svcInfo);
    }

    return serviceList;
  }

  /**
   * 采集 Redis 统计指标。
   *
   * <p>读取 INFO Stats 部分的以下字段：
   * <ul>
   *   <li>{@code total_commands_processed} — 启动以来处理命令总数
   *   <li>{@code keyspace_hits} — 键命中次数
   *   <li>{@code keyspace_misses} — 键未命中次数
   * </ul>
   * 并计算命中率 = hits / (hits + misses)。
   *
   * @return Redis 指标 Map；Redis 未装配时返回 null
   */
  private Map<String, Object> collectRedisMetrics() {
    StringRedisTemplate redisTemplate = stringRedisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return null;
    }

    try {
      Properties info = redisTemplate.getConnectionFactory().getConnection().info("stats");
      if (info == null) {
        return null;
      }

      long totalCommands = parseLongOrDefault(info.getProperty("total_commands_processed"), 0L);
      long keyspaceHits = parseLongOrDefault(info.getProperty("keyspace_hits"), 0L);
      long keyspaceMisses = parseLongOrDefault(info.getProperty("keyspace_misses"), 0L);
      long totalLookups = keyspaceHits + keyspaceMisses;
      double hitRate = totalLookups > 0
          ? Math.round((double) keyspaceHits / totalLookups * 10000.0) / 100.0
          : 0.0;

      Map<String, Object> redisMetrics = new LinkedHashMap<>(8);
      redisMetrics.put("totalCommands", totalCommands);
      redisMetrics.put("keyspaceHits", keyspaceHits);
      redisMetrics.put("keyspaceMisses", keyspaceMisses);
      redisMetrics.put("hitRate", hitRate);
      redisMetrics.put("available", true);
      return redisMetrics;
    } catch (Exception ex) {
      // Redis 指标采集异常不阻塞整体返回
      log.debug("[MetricsDashboard] Redis 指标采集异常: {}", ex.getMessage());
      Map<String, Object> fallback = new LinkedHashMap<>(4);
      fallback.put("available", false);
      fallback.put("reason", ex.getMessage());
      return fallback;
    }
  }

  /**
   * 采集运行时信息（从 Spring Environment 和 java.lang.management 读取）。
   *
   * @return 运行时信息 Map
   */
  private Map<String, Object> collectRuntimeInfo() {
    Map<String, Object> runtime = new LinkedHashMap<>(8);
    runtime.put("applicationName", environment.getProperty("spring.application.name", "unknown"));
    runtime.put("serverPort", environment.getProperty("server.port", "unknown"));
    runtime.put("springBootVersion", environment.getProperty("spring-boot.version", "unknown"));
    runtime.put("javaVersion", System.getProperty("java.version", "unknown"));
    runtime.put("javaVendor", System.getProperty("java.vendor", "unknown"));
    runtime.put("osName", System.getProperty("os.name", "unknown"));
    runtime.put("availableCores", Runtime.getRuntime().availableProcessors());

    // JVM 内存信息
    Runtime rt = Runtime.getRuntime();
    long maxMemory = rt.maxMemory();
    long totalMemory = rt.totalMemory();
    long freeMemory = rt.freeMemory();
    long usedMemory = totalMemory - freeMemory;

    Map<String, Object> memory = new LinkedHashMap<>(4);
    memory.put("usedMb", usedMemory / (1024 * 1024));
    memory.put("totalMb", totalMemory / (1024 * 1024));
    memory.put("maxMb", maxMemory / (1024 * 1024));
    double usagePercent = maxMemory > 0
        ? Math.round((double) usedMemory / maxMemory * 10000.0) / 100.0
        : 0.0;
    memory.put("usagePercent", usagePercent);
    runtime.put("memory", memory);

    return runtime;
  }

  /**
   * 解析长整型字符串，解析失败返回默认值。
   *
   * @param value        字符串值
   * @param defaultValue 默认值
   * @return 解析结果或默认值
   */
  private static long parseLongOrDefault(String value, long defaultValue) {
    if (value == null || value.isEmpty()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
