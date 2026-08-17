package com.njydsz.gateway.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import com.njydsz.common.util.id.RandomUtils;

/**
 * 网关层灰度负载均衡器
 *
 * <p>基于 Spring Cloud LoadBalancer 的 {@link ReactorServiceInstanceLoadBalancer} 实现, 按请求头 {@code
 * X-Gray-Tag} 与 Nacos 实例 metadata 中的 {@code version} 标签 进行灰度流量分发。
 *
 * <h3>路由规则</h3>
 *
 * <ol>
 *   <li>读取请求头 {@code X-Gray-Tag}(值: {@code gray} / {@code stable} / 空)
 *   <li>读取 Nacos metadata 中实例的 {@code version} 标签 (metadata.key = "version", value = "gray" /
 *       "stable")
 *   <li>若 {@code X-Gray-Tag=gray},只选择 {@code metadata.version=gray} 的实例
 *   <li>若 {@code X-Gray-Tag=stable} 或无 Header,只选择 {@code metadata.version!=gray} 的实例
 *   <li>在候选实例中用轮询(RoundRobin)选择
 *   <li>若灰度实例不存在,降级到所有实例轮询(避免灰度实例下线时 503)
 * </ol>
 *
 * <h3>灰度标识来源(优先级从高到低)</h3>
 *
 * <ul>
 *   <li>请求头 {@code X-Gray-Tag}(由 {@link GrayLoadBalancerRequestFilter} 注入)
 *   <li>exchange attribute {@code X-Gray-Tag}(Filter 写入的备份)
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class GrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

  private static final Logger LOG = LoggerFactory.getLogger(GrayLoadBalancer.class);

  /** 灰度标签请求头名,同时作为 exchange attribute key */
  public static final String GRAY_TAG_HEADER = "X-Gray-Tag";

  /** exchange attribute key: 灰度路由结果（true=命中灰度实例, false=命中稳定实例） */
  public static final String GRAY_HIT_ATTR = "__gray_hit_result";

  /** 灰度标识值:灰度实例 */
  private static final String GRAY_TAG_GRAY = "gray";

  /** Nacos metadata 中版本标识 key */
  private static final String METADATA_VERSION = "version";

  /**
   * P3-5: Nacos metadata 中权重标识 key
   *
   * <p>实例 metadata 中 weight=10 表示该实例权重为 10（默认 1）。 加权随机时高权重实例获得更多请求。
   */
  private static final String METADATA_WEIGHT = "weight";

  /**
   * P1-6: 灰度流量比例 key（当 X-Gray-Tag 未指定时，按比例自动分流到灰度）
   *
   * <p>metadata 中 grayRatio=10 表示 10% 流量走灰度。
   */
  private static final String METADATA_GRAY_RATIO = "grayRatio";

  /** 服务实例列表供给者(延迟加载,每个 serviceId 对应独立的子上下文) */
  private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

  /** 当前负载均衡器所属服务 ID */
  private final String serviceId;

  /** 轮询位置计数器(AtomicInteger 保证线程安全) */
  private final AtomicInteger position;

  /**
   * P2-7: 按服务 ID 缓存预计算的 Alias Method 表。
   *
   * <p>当候选实例列表不变时（同一 filtered 大小 + 同一实例集合）， 复用预计算的 Alias 表，将加权随机选择从 O(n) 降至 O(1)。 实例列表变化时（Nacos
   * 推送刷新）自动失效，下一次请求重新构建。
   */
  private final ConcurrentHashMap<String, AliasTable> aliasTableCache = new ConcurrentHashMap<>();

  /**
   * 构造灰度负载均衡器
   *
   * @param supplierProvider 服务实例列表供给者
   * @param serviceId 服务 ID
   */
  public GrayLoadBalancer(
      ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId) {
    this.supplierProvider = supplierProvider;
    this.serviceId = serviceId;
    // 初始位置随机化,避免多实例启动时首轮都命中同一实例
    this.position = new AtomicInteger(RandomUtils.randomInt(1000));
  }

  /**
   * 响应式选择实例(带请求上下文)
   *
   * <p>从请求上下文中提取灰度标识,然后按灰度规则过滤实例并轮询选择。
   *
   * @param request 负载均衡请求(携带 HTTP 头与 exchange attributes)
   * @return 实例响应 Mono
   */
  @Override
  public Mono<Response<ServiceInstance>> choose(Request request) {
    String grayTag = resolveGrayTag(request);

    ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
    if (supplier == null) {
      LOG.warn("[GrayLB] 服务 {} 无可用 ServiceInstanceListSupplier", serviceId);
      return Mono.just(new EmptyResponse());
    }

    return supplier
        .get(request)
        .next()
        .map(instances -> getInstanceResponse(instances, grayTag))
        .onErrorResume(
            e -> {
              LOG.warn("[GrayLB] 服务 {} 获取实例列表失败: {}", serviceId, e.getMessage());
              return Mono.just(new EmptyResponse());
            });
  }

  /**
   * 响应式选择实例(无请求上下文)
   *
   * <p>父接口默认方法会传入空的 DefaultRequest,此处复用 {@link #choose(Request)}。
   *
   * @return 实例响应 Mono
   */
  @Override
  public Mono<Response<ServiceInstance>> choose() {
    return choose(null);
  }

  /**
   * 从请求上下文中解析灰度标识
   *
   * <p>解析顺序:
   *
   * <ol>
   *   <li>HTTP 请求头 {@code X-Gray-Tag}
   *   <li>exchange attribute {@code X-Gray-Tag}(由 GrayLoadBalancerRequestFilter 写入)
   * </ol>
   *
   * @param request 负载均衡请求
   * @return 灰度标识({@code gray} / {@code stable} / {@code null})
   */
  private String resolveGrayTag(Request request) {
    if (request == null) {
      return null;
    }
    Object context = request.getContext();
    if (!(context instanceof RequestDataContext rdc)) {
      return null;
    }
    RequestData data = rdc.getClientRequest();
    if (data == null) {
      return null;
    }

    // 1. 优先从 HTTP Header 读取
    HttpHeaders headers = data.getHeaders();
    if (headers != null) {
      String headerTag = headers.getFirst(GRAY_TAG_HEADER);
      if (headerTag != null && !headerTag.isEmpty()) {
        return headerTag;
      }
    }

    // 2. 回退到 exchange attributes(Filter 写入的备份)
    Map<String, Object> attrs = data.getAttributes();
    if (attrs != null) {
      Object attrTag = attrs.get(GRAY_TAG_HEADER);
      if (attrTag instanceof String s && !s.isEmpty()) {
        return s;
      }
    }
    return null;
  }

  /**
   * 按灰度规则过滤并轮询选择实例
   *
   * <p>步骤:
   *
   * <ol>
   *   <li>按灰度标识过滤候选实例
   *   <li>过滤后为空则降级使用全量实例(避免灰度实例下线时 503)
   *   <li>在候选实例中轮询选择
   * </ol>
   *
   * @param instances 全量服务实例
   * @param grayTag 灰度标识
   * @return 实例响应
   */
  private Response<ServiceInstance> getInstanceResponse(
      List<ServiceInstance> instances, String grayTag) {
    if (instances == null || instances.isEmpty()) {
      LOG.warn("[GrayLB] 服务 {} 无可用实例", serviceId);
      return new EmptyResponse();
    }

    boolean wantGray = GRAY_TAG_GRAY.equalsIgnoreCase(grayTag);

    // 按灰度标签过滤
    List<ServiceInstance> filtered = new ArrayList<>(instances.size());
    for (ServiceInstance inst : instances) {
      Map<String, String> meta = inst.getMetadata();
      String version = meta == null ? null : meta.get(METADATA_VERSION);
      boolean isGray = GRAY_TAG_GRAY.equalsIgnoreCase(version);
      if (wantGray) {
        // 灰度请求:只选 version=gray 的实例
        if (isGray) {
          filtered.add(inst);
        }
      } else {
        // 稳定请求或无标识:只选 version!=gray 的实例
        if (!isGray) {
          filtered.add(inst);
        }
      }
    }

    // 降级:灰度实例不存在时使用全量实例,避免 503
    if (filtered.isEmpty()) {
      if (wantGray) {
        LOG.warn("[GrayLB] 服务 {} 灰度实例不存在,降级到全量实例(共 {} 个)", serviceId, instances.size());
      }
      filtered = instances;
    }

    // P0-B3: 加权随机选择（Alias Method，读取 Nacos metadata 中的 weight 字段）
    ServiceInstance selected = selectByWeight(filtered);

    if (LOG.isDebugEnabled()) {
      Map<String, String> meta = selected.getMetadata();
      String selectedVersion = meta == null ? null : meta.get(METADATA_VERSION);
      String selectedWeight = meta == null ? null : meta.get(METADATA_WEIGHT);
      LOG.debug(
          "[GrayLB] 服务 {} 选择实例 {} (grayTag={}, version={}, weight={}, 候选 {} 个)",
          serviceId,
          selected.getInstanceId(),
          grayTag,
          selectedVersion,
          selectedWeight,
          filtered.size());
    }
    return new DefaultResponse(selected);
  }

  /**
   * P0-B3: 加权随机选择（Alias Method，非加权轮询）
   *
   * <p>P2-7: 使用 Alias Method 优化为 O(1) 选择（实例列表不变时复用预计算表）。 首次构建表为 O(n)，后续每次选择为 O(1)。
   * 等权重场景退化为轮询。
   *
   * @param instances 候选实例列表
   * @return 选中的实例
   */
  private ServiceInstance selectByWeight(List<ServiceInstance> instances) {
    if (instances.size() == 1) {
      return instances.get(0);
    }

    // 所有实例等权重时，使用轮询避免构建 Alias 表
    if (allSameWeight(instances)) {
      int idx = Math.abs(position.incrementAndGet()) % instances.size();
      return instances.get(idx);
    }

    // P2-7: 构建或复用 Alias 表，O(1) 选择
    AliasTable table = getOrCreateAliasTable(instances);
    int col = RandomUtils.randomInt(table.prob.length);
    // 以 prob[col] 概率选 col，否则选 alias[col]
    int idx = (RandomUtils.randomInt(1000) < table.prob[col]) ? col : table.alias[col];
    return instances.get(idx);
  }

  /** P2-7: 检查所有实例是否权重相同（等权重场景无需构建 Alias 表） */
  private boolean allSameWeight(List<ServiceInstance> instances) {
    if (instances.size() <= 1) return true;
    int first = getInstanceWeight(instances.get(0));
    for (int i = 1; i < instances.size(); i++) {
      if (getInstanceWeight(instances.get(i)) != first) return false;
    }
    return true;
  }

  /** P2-7: 获取或创建 Alias 表（基于实例列表大小和 ID 哈希作为缓存键） */
  private AliasTable getOrCreateAliasTable(List<ServiceInstance> instances) {
    String cacheKey = buildCacheKey(instances);
    AliasTable table = aliasTableCache.get(cacheKey);
    if (table != null) {
      return table;
    }
    table = buildAliasTable(instances);
    aliasTableCache.put(cacheKey, table);
    // 仅保留最近 10 个服务的缓存表（防止实例 ID/Nacos key 变化导致泄漏）
    if (aliasTableCache.size() > 10) {
      aliasTableCache.keySet().iterator().remove();
    }
    return table;
  }

  /** P2-7: 基于实例 ID 和列表大小构建缓存键 */
  private String buildCacheKey(List<ServiceInstance> instances) {
    StringBuilder sb = new StringBuilder();
    sb.append(instances.size()).append(':');
    for (ServiceInstance inst : instances) {
      sb.append(inst.getInstanceId().hashCode()).append(',');
    }
    return sb.toString();
  }

  /**
   * P2-7: 使用 Vose's Alias Method 构建 O(1) 加权选择表。
   *
   * <p>算法流程：
   *
   * <ol>
   *   <li>归一化权重到 [0, n) 范围
   *   <li>用小堆/大堆分别收集 underfull 和 overfull 的列
   *   <li>配对填充 alias 表，直到所有列都恰好"满"
   * </ol>
   *
   * @param instances 候选实例列表
   * @return 预计算的 Alias 表
   */
  private AliasTable buildAliasTable(List<ServiceInstance> instances) {
    int n = instances.size();
    int[] weights = new int[n];
    for (int i = 0; i < n; i++) {
      weights[i] = getInstanceWeight(instances.get(i));
    }

    int totalWeight = 0;
    for (int w : weights) {
      totalWeight += w;
    }
    if (totalWeight <= 0) {
      // 所有权重为 0，等概率随机
      return new AliasTable(new int[n], new int[n]);
    }

    // prob[i] = 第 i 列选中自己的概率（单位：千分比 0-1000）
    int[] prob = new int[n];
    int[] alias = new int[n];
    Arrays.fill(alias, -1);

    // 归一化：prob[i] = weights[i] * n / totalWeight * 1000
    double[] scaled = new double[n];
    for (int i = 0; i < n; i++) {
      scaled[i] = (double) weights[i] * n / totalWeight;
    }

    // 使用小堆/大堆配对
    int[] small = new int[n];
    int[] large = new int[n];
    int ns = 0, nl = 0;

    for (int i = 0; i < n; i++) {
      if (scaled[i] < 1.0) {
        small[ns++] = i;
      } else {
        large[nl++] = i;
      }
    }

    while (ns > 0 && nl > 0) {
      int s = small[--ns];
      int l = large[--nl];
      prob[s] = (int) (scaled[s] * 1000);
      alias[s] = l;
      scaled[l] = scaled[l] + scaled[s] - 1.0;
      if (scaled[l] < 1.0) {
        small[ns++] = l;
      } else {
        large[nl++] = l;
      }
    }

    // 剩余列（精度误差处理）
    while (nl > 0) {
      prob[large[--nl]] = 1000;
    }
    while (ns > 0) {
      prob[small[--ns]] = 1000;
    }

    return new AliasTable(prob, alias);
  }

  /**
   * P2-7: Alias Method 预计算表（prob 和 alias 数组）。
   *
   * <p>prob[i] 表示以千分比表示的选中自己的概率（0-1000）。 alias[i] 表示当不选自己时，回退到哪个列。 选择算法：随机选列 i，以 prob[i]/1000 概率返回
   * i，否则返回 alias[i]。
   */
  private static class AliasTable {
    final int[] prob;
    final int[] alias;

    AliasTable(int[] prob, int[] alias) {
      this.prob = prob;
      this.alias = alias;
    }
  }

  /**
   * P3-5: 获取实例权重
   *
   * @param instance 服务实例
   * @return 权重值（默认 1）
   */
  private int getInstanceWeight(ServiceInstance instance) {
    Map<String, String> meta = instance.getMetadata();
    if (meta == null) {
      return 1;
    }
    String weightStr = meta.get(METADATA_WEIGHT);
    if (weightStr == null || weightStr.isBlank()) {
      return 1;
    }
    try {
      int w = Integer.parseInt(weightStr.trim());
      return w > 0 ? w : 1;
    } catch (NumberFormatException e) {
      return 1;
    }
  }
}
