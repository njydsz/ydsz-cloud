package com.njydsz.workflow.server.engine;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.entity.FlowDefinition;
import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;

/**
 * 流程定义缓存预热器。
 *
 * <p>应用启动完成后（ApplicationReadyEvent），主动加载所有「在线」流程定义的节点和跳转规则到本地缓存，
 * 避免首批请求 Cache Miss 穿透到数据库造成启动瞬间的查询压力。
 *
 * <p><b>预热策略：</b>
 *
 * <ul>
 *   <li>仅预热 status=ONLINE 的流程定义（草稿/已下线无需预热）</li>
 *   <li>逐个加载，每个独立 try-catch，单个定义加载失败不中断其余</li>
 *   <li>加载完成后记录 total/warm/skip/fail 统计</li>
 * </ul>
 *
 * <p><b>降级：</b>若 FlowDefinitionRepository 查询异常（如数据库连接失败），整个预热跳过，
 * 不阻断应用启动。此时缓存以自然 Cache Miss 方式逐步回填。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowDefinitionCacheWarmUp {

  private final FlowDefinitionRepository flowDefinitionRepository;
  private final FlowDefinitionCacheService cacheService;

  /** 单次预热最大定义数（防内存溢出），-1 表示无上限 */
  private static final int WARM_MAX_DEFINITIONS = 200;

  /**
   * 应用启动完成后预热缓存。
   *
   * <p>使用 {@link EventListener} 而非 {@code @PostConstruct}，确保在 Spring 容器完全初始化、
   * 数据库连接就绪后执行。
   *
   * @param event 应用就绪事件（未使用，仅用于触发时机）
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    log.info("[FlowDefinitionCacheWarmUp] 开始流程定义缓存预热...");
    long start = System.currentTimeMillis();

    List<FlowDefinition> onlineDefinitions;
    try {
      onlineDefinitions = flowDefinitionRepository.findAllOnline();
    } catch (Exception e) {
      log.warn("[FlowDefinitionCacheWarmUp] 查询在线流程定义失败，跳过预热: {}", e.getMessage());
      return;
    }

    if (onlineDefinitions == null || onlineDefinitions.isEmpty()) {
      log.info("[FlowDefinitionCacheWarmUp] 无在线流程定义，无需预热");
      return;
    }

    int total = onlineDefinitions.size();
    int warm = 0;
    int fail = 0;

    int limit = WARM_MAX_DEFINITIONS > 0 ? Math.min(total, WARM_MAX_DEFINITIONS) : total;
    for (int i = 0; i < limit; i++) {
      FlowDefinition definition = onlineDefinitions.get(i);
      if (definition.getId() == null) {
        continue;
      }
      try {
        cacheService.getOrLoad(definition.getId());
        warm++;
      } catch (Exception e) {
        fail++;
        log.warn("[FlowDefinitionCacheWarmUp] 预热失败 definitionId={}: {}",
            definition.getId(), e.getMessage());
      }
    }

    long cost = System.currentTimeMillis() - start;
    log.info("[FlowDefinitionCacheWarmUp] 预热完成 total={} warm={} fail={} costMs={}",
        total, warm, fail, cost);
  }
}
