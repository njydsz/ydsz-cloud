package com.njydsz.system.web.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.system.server.metrics.RedisMetricsService;

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
@ApiVersion("26.10.01")
@Slf4j
@RestController
@RequestMapping("/system/metrics")
@RequiredArgsConstructor
public class MetricsDashboardController {

  /** Nacos 服务发现客户端 */
  private final DiscoveryClient discoveryClient;

  /** Spring 运行环境（读取应用名 / 端口等） */
  private final Environment environment;

  /** Redis 指标采集服务（CACHE-P1-001 整改：Controller 层不再直接持有 RedisTemplate） */
  private final RedisMetricsService redisMetricsService;


  /** 默认 Map 初始容量（单层 Map 字段数） */
  private static final int DEFAULT_MAP_CAPACITY = 8;

  /** 小型 Map 初始容量（2 ~ 4 字段） */
  private static final int SMALL_MAP_CAPACITY = 4;

  /** 默认 List 初始容量（预估服务数） */
  private static final int DEFAULT_LIST_CAPACITY = 16;

  /** 字节到 MB 的转换因子 (1024 * 1024) */
  private static final long BYTES_PER_MB = 1024L * 1024L;

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
  @Operation(summary = "获取运维仪表盘完整数据")
  @GetMapping("/dashboard")
  public YdszResponse<Map<String, Object>> dashboard() {
    log.debug(I18n.message("system.web.metrics.dashboard"));

    Map<String, Object> result = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);

    // 1. Nacos 注册中心服务实例列表
    List<Map<String, Object>> serviceList = collectServices();
    result.put("services", serviceList);

    // 2. Redis 关键指标（Redis 未装配时降级返回空 Map）
    result.put("redis", redisMetricsService.collectRedisMetrics());

    // 3. 运行时信息
    result.put("runtime", collectRuntimeInfo());

    // 4. 汇总计数
    Map<String, Integer> summary = new LinkedHashMap<>(SMALL_MAP_CAPACITY);
    int totalServices = serviceList.size();
    int upServices = (int) serviceList.stream()
        .filter(s -> "UP".equals(s.get("status")))
        .count();
    summary.put("totalServices", totalServices);
    summary.put("upServices", upServices);
    summary.put("downServices", totalServices - upServices);
    result.put("summary", summary);

    // 5. 采集时间戳
    result.put("collectedAt", DateUtils.now());

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
    List<Map<String, Object>> serviceList = new ArrayList<>(DEFAULT_LIST_CAPACITY);

    List<String> services = discoveryClient.getServices();
    if (services == null || services.isEmpty()) {
      return serviceList;
    }

    for (String serviceId : services) {
      List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
      if (instances == null || instances.isEmpty()) {
        // 服务注册过但当前无实例
        Map<String, Object> svcInfo = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
        svcInfo.put("serviceId", serviceId);
        svcInfo.put("status", "DOWN");
        svcInfo.put("instanceCount", 0);
        serviceList.add(svcInfo);
        continue;
      }

      // Nacos 仅注册健康实例，有实例即视为 UP
      ServiceInstance primary = instances.get(0);
      Map<String, Object> svcInfo = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
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
   * 采集运行时信息（从 Spring Environment 和 java.lang.management 读取）。
   *
   * @return 运行时信息 Map
   */
  private Map<String, Object> collectRuntimeInfo() {
    Map<String, Object> runtime = new LinkedHashMap<>(DEFAULT_MAP_CAPACITY);
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

    Map<String, Object> memory = new LinkedHashMap<>(SMALL_MAP_CAPACITY);
    memory.put("usedMb", usedMemory / BYTES_PER_MB);
    memory.put("totalMb", totalMemory / BYTES_PER_MB);
    memory.put("maxMb", maxMemory / BYTES_PER_MB);
    double usagePercent = maxMemory > 0
        ? Math.round((double) usedMemory / maxMemory * 10000.0) / 100.0
        : 0.0;
    memory.put("usagePercent", usagePercent);
    runtime.put("memory", memory);

    return runtime;
  }
}
