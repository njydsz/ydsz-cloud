package com.njydsz.system.server.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.search.sync.SearchIndexEventBridge;

/**
 * 系统模块搜索索引同步器（P1-5：将搜索索引同步从各 ServiceImpl 中收敛为单一组件）。
 *
 * <p>封装 {@link SearchIndexEventBridge} 的可选依赖注入与降级逻辑，供 Config / DictItem / Variable 等
 * Service 在写操作后统一调用，避免各 Service 重复编写 {@code ObjectProvider} 样板代码（SRP 拆分）。
 *
 * <p><b>降级语义：</b>未启用 common-search（或引擎不支持索引）时静默跳过，不影响业务主流程。
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see SearchIndexEventBridge 搜索索引事件桥接器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexSyncer {

  /** 可选依赖：搜索索引桥接器（未启用搜索模块时为 null） */
  private final ObjectProvider<SearchIndexEventBridge> bridgeProvider;

  /**
   * 异步 UPSERT 实体到搜索索引。
   *
   * @param type 实体类型标识（如 config / dict / variable）
   * @param entity 业务实体
   * @param <T> 实体类型
   */
  public <T> void upsert(String type, T entity) {
    SearchIndexEventBridge bridge = bridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexUpsert(type, entity);
    }
  }

  /**
   * 异步删除索引文档。
   *
   * @param type 实体类型标识
   * @param documentId 文档 ID
   */
  public void delete(String type, String documentId) {
    SearchIndexEventBridge bridge = bridgeProvider.getIfAvailable();
    if (bridge != null) {
      bridge.indexDelete(type, documentId);
    }
  }
}
