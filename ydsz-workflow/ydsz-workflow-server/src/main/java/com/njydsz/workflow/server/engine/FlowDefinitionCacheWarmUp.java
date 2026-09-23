package com.njydsz.workflow.server.engine;

import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.repository.FlowDefinitionRepository;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;

/**
 * 流程定义缓存预热器。
 *
 * <p>应用启动完成后（ApplicationReadyEvent），主动加载所有「在线」流程定义的节点和跳转规则到本地缓存，
 * 避免首批请求 Cache Miss 穿透到数据库造成启动瞬间的查询压力。
 *
 * <p><b>预热策略：</b>
 *
 * <ul>
 *   <li>仅预热已发布（publishStatus=1）的流程定义</li>
 *   <li>逐个加载，每个独立 try-catch，单个定义加载失败不中断其余</li>
 *   <li>加载完成后记录 total/warm/fail 统计</li>
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

  private static final int WARM_MAX_DEFINITIONS = 200;
  private static final int DEFAULT_PAGE_SIZE = 100;

  private final FlowDefinitionRepository flowDefinitionRepository;
  private final FlowDefinitionCacheService cacheService;

  /**
   * 应用启动完成后预热缓存。
   *
   * @param event 应用就绪事件（用于触发时机）
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady(ApplicationReadyEvent event) {
    log.info("[FlowCacheWarmUp] 开始流程定义缓存预热...");
    long start = System.currentTimeMillis();

    List<FlowDefinitionVO> onlineDefinitions;
    try {
      // 分页查询已发布的流程定义
      onlineDefinitions = flowDefinitionRepository.findActivePage(
          1, DEFAULT_PAGE_SIZE, null, null);
    } catch (Exception e) {
      log.warn("[FlowCacheWarmUp] 查询在线流程定义失败，跳过预热: {}", e.getMessage());
      return;
    }

    if (onlineDefinitions == null || onlineDefinitions.isEmpty()) {
      log.info("[FlowCacheWarmUp] 无在线流程定义，无需预热");
      return;
    }

    int total = onlineDefinitions.size();
    int warm = 0;
    int fail = 0;

    // 限制单次预热定义数
    List<FlowDefinitionVO> toWarm = total > WARM_MAX_DEFINITIONS
        ? onlineDefinitions.subList(0, WARM_MAX_DEFINITIONS)
        : onlineDefinitions;

    for (FlowDefinitionVO definition : toWarm) {
      if (definition.getId() == null) {
        continue;
      }
      try {
        // 通过获取节点列表触发缓存加载（metadataCache.get 内部自动 fallBack 到 loadMetadata）
        cacheService.getAllNodes(definition.getId());
        warm++;
      } catch (Exception e) {
        fail++;
        log.warn("[FlowCacheWarmUp] 预热失败 definitionId={}: {}",
            definition.getId(), e.getMessage());
      }
    }

    long cost = System.currentTimeMillis() - start;
    log.info("[FlowCacheWarmUp] 预热完成 total={} warm={} fail={} costMs={}",
        total, warm, fail, cost);
  }
}
