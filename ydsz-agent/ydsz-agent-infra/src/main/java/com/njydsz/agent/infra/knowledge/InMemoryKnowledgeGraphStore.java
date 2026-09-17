package com.njydsz.agent.infra.knowledge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.knowledge.Entity;
import com.njydsz.agent.domain.knowledge.EntityType;
import com.njydsz.agent.domain.knowledge.KnowledgeGraphStore;
import com.njydsz.agent.domain.knowledge.Relation;

/**
 * 基于内存的知识图谱存储实现 — 开发/测试环境默认。
 *
 * <p>使用 {@link ConcurrentHashMap} 存储实体和邻接关系（from → Relation list）。
 * 图遍历使用 BFS 算法，通过 {@link ReadWriteLock} 保证读写并发安全。
 *
 * <p><b>限制：</b>数据在应用重启后丢失，不适合大规模生产使用。
 * 生产环境可替换为 Neo4j 或关系数据库实现。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class InMemoryKnowledgeGraphStore implements KnowledgeGraphStore {

  /** 存储类型标识 */
  private static final String STORE_TYPE = "memory";

  /** 默认最大遍历深度 */
  private static final int DEFAULT_MAX_TRAVERSE_DEPTH = 3;

  /** 实体存储：entityId → Entity */
  private final ConcurrentHashMap<String, Entity> entityStore = new ConcurrentHashMap<>(32);

  /** 邻接表：fromEntityId → Relation list（出边） */
  private final ConcurrentHashMap<String, List<Relation>> adjacencyOut = new ConcurrentHashMap<>(32);

  /** 反向邻接表：toEntityId → Relation list（入边） */
  private final ConcurrentHashMap<String, List<Relation>> adjacencyIn = new ConcurrentHashMap<>(32);

  /** 读写锁，保护遍历和批量操作的一致性 */
  private final ReadWriteLock graphLock = new ReentrantReadWriteLock();

  /** 实体计数器 */
  private final AtomicLong entityCounter = new AtomicLong(0);

  /** 关系计数器 */
  private final AtomicLong relationCounter = new AtomicLong(0);

  @Override
  public void addEntity(Entity entity) {
    graphLock.writeLock().lock();
    try {
      Objects.requireNonNull(entity, "entity 不能为 null");
      String entityId = entity.id();
      Entity existing = entityStore.get(entityId);
      if (existing == null) {
        entityStore.put(entityId, entity);
        adjacencyOut.computeIfAbsent(entityId, k -> new ArrayList<>());
        adjacencyIn.computeIfAbsent(entityId, k -> new ArrayList<>());
        entityCounter.incrementAndGet();
        log.debug("[KG-Memory] 添加实体: {}", entityId);
      } else {
        // 合并：保留新描述的较长者，合并属性
        Entity merged = mergeEntity(existing, entity);
        entityStore.put(entityId, merged);
        log.debug("[KG-Memory] 合并实体: {}", entityId);
      }
    } finally {
      graphLock.writeLock().unlock();
    }
  }

  @Override
  public Optional<Entity> findEntityById(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(entityStore.get(entityId));
  }

  @Override
  public List<Entity> findEntitiesByName(String name) {
    if (name == null || name.isBlank()) {
      return Collections.emptyList();
    }
    String lowerName = name.trim().toLowerCase();
    return entityStore.values().stream()
        .filter(e -> e.name().toLowerCase().contains(lowerName))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public List<Entity> findEntitiesByType(EntityType type) {
    if (type == null) {
      return Collections.emptyList();
    }
    return entityStore.values().stream()
        .filter(e -> e.type() == type)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  @Override
  public void addRelation(Relation relation) {
    graphLock.writeLock().lock();
    try {
      Objects.requireNonNull(relation, "relation 不能为 null");
      String fromId = relation.fromEntityId();
      String toId = relation.toEntityId();
      String relationId = relation.id();

      // 检查是否已存在（依据 ID）
      List<Relation> existingRelations = adjacencyOut.computeIfAbsent(fromId, k -> new ArrayList<>());
      boolean alreadyExists = existingRelations.stream().anyMatch(r -> r.id().equals(relationId));
      if (!alreadyExists) {
        existingRelations.add(relation);
        // 添加到反向邻接表
        adjacencyIn.computeIfAbsent(toId, k -> new ArrayList<>()).add(relation);
        relationCounter.incrementAndGet();
        log.debug("[KG-Memory] 添加关系: {} -> {} [{}]", fromId, relation.type(), toId);
      } else {
        log.debug("[KG-Memory] 关系已存在，跳过: {}", relationId);
      }
    } finally {
      graphLock.writeLock().unlock();
    }
  }

  @Override
  public List<Relation> findRelationsFrom(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return Collections.emptyList();
    }
    return adjacencyOut.getOrDefault(entityId, Collections.emptyList());
  }

  @Override
  public List<Relation> findRelationsTo(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return Collections.emptyList();
    }
    return adjacencyIn.getOrDefault(entityId, Collections.emptyList());
  }

  @Override
  public List<Relation> findRelations(String entityId) {
    if (entityId == null || entityId.isBlank()) {
      return Collections.emptyList();
    }
    Set<Relation> allRelations = new HashSet<>();
    allRelations.addAll(adjacencyOut.getOrDefault(entityId, Collections.emptyList()));
    allRelations.addAll(adjacencyIn.getOrDefault(entityId, Collections.emptyList()));
    return new ArrayList<>(allRelations);
  }

  @Override
  public List<Entity> traverse(String startEntityId, int maxDepth) {
    graphLock.readLock().lock();
    try {
      return traverseBfs(startEntityId, maxDepth);
    } finally {
      graphLock.readLock().unlock();
    }
  }

  @Override
  public SubGraph subgraph(String entityId, int depth) {
    graphLock.readLock().lock();
    try {
      // BFS 收集范围内实体
      List<Entity> entities = traverseBfs(entityId, depth);
      Set<String> entityIds = entities.stream().map(Entity::id).collect(Collectors.toSet());

      // 收集去重关系：仅包含实体都在范围内的关系
      Set<Relation> relationSet = new HashSet<>();
      for (String id : entityIds) {
        adjacencyOut.getOrDefault(id, Collections.emptyList()).stream()
            .filter(r -> entityIds.contains(r.toEntityId()))
            .forEach(relationSet::add);
      }

      return new SubGraph(entities, new ArrayList<>(relationSet));
    } finally {
      graphLock.readLock().unlock();
    }
  }

  @Override
  public void deleteEntity(String entityId) {
    graphLock.writeLock().lock();
    try {
      if (entityStore.remove(entityId) == null) {
        return;
      }
      // 从邻接表中移除相关关系
      List<Relation> removedOut = adjacencyOut.remove(entityId);
      if (removedOut != null) {
        for (Relation r : removedOut) {
          List<Relation> reverseList = adjacencyIn.get(r.toEntityId());
          if (reverseList != null) {
            reverseList.removeIf(rel -> rel.id().equals(r.id()));
          }
        }
      }
      List<Relation> removedIn = adjacencyIn.remove(entityId);
      if (removedIn != null) {
        for (Relation r : removedIn) {
          List<Relation> forwardList = adjacencyOut.get(r.fromEntityId());
          if (forwardList != null) {
            forwardList.removeIf(rel -> rel.id().equals(r.id()));
          }
        }
      }
      // 清理其他实体邻接表中对已删除实体的引用
      for (String otherId : adjacencyOut.keySet()) {
        adjacencyOut.get(otherId).removeIf(r -> r.toEntityId().equals(entityId));
      }
      for (String otherId : adjacencyIn.keySet()) {
        adjacencyIn.get(otherId).removeIf(r -> r.fromEntityId().equals(entityId));
      }
      log.debug("[KG-Memory] 删除实体及其关系: {}", entityId);
    } finally {
      graphLock.writeLock().unlock();
    }
  }

  @Override
  public void clear() {
    graphLock.writeLock().lock();
    try {
      entityStore.clear();
      adjacencyOut.clear();
      adjacencyIn.clear();
      entityCounter.set(0);
      relationCounter.set(0);
      log.info("[KG-Memory] 已清空知识图谱");
    } finally {
      graphLock.writeLock().unlock();
    }
  }

  @Override
  public long entityCount() {
    return entityCounter.get();
  }

  @Override
  public long relationCount() {
    return relationCounter.get();
  }

  // ===== 私有辅助方法 =====

  /**
   * BFS 遍历实现。
   *
   * @param startEntityId 起始实体 ID
   * @param maxDepth 最大跳数
   * @return 范围内实体列表
   */
  private List<Entity> traverseBfs(String startEntityId, int maxDepth) {
    if (startEntityId == null || startEntityId.isBlank()) {
      return Collections.emptyList();
    }
    if (maxDepth < 0) {
      maxDepth = DEFAULT_MAX_TRAVERSE_DEPTH;
    }
    List<Entity> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>();

    // 起始实体入队
    visited.add(startEntityId);
    queue.add(startEntityId);
    // BFS 层级遍历，按 depth 控制
    int currentDepth = 0;
    while (!queue.isEmpty() && currentDepth <= maxDepth) {
      int levelSize = queue.size();
      for (int i = 0; i < levelSize; i++) {
        String currentId = queue.poll();
        Entity entity = entityStore.get(currentId);
        if (entity != null) {
          result.add(entity);
        }
        // 只有不是最后一层时才扩展邻居
        if (currentDepth < maxDepth) {
          List<Relation> outRelations = adjacencyOut.getOrDefault(currentId, Collections.emptyList());
          for (Relation rel : outRelations) {
            String neighborId = rel.toEntityId();
            if (visited.add(neighborId)) {
              queue.add(neighborId);
            }
          }
        }
      }
      currentDepth++;
    }
    return result;
  }

  /**
   * 合并两个相同 ID 的实体。
   *
   * <p>规则：保留较长描述，合并属性（新值覆盖旧值）。
   *
   * @param existing 已存在的实体
   * @param incoming 新入库的实体
   * @return 合并后的实体
   */
  private Entity mergeEntity(Entity existing, Entity incoming) {
    String description = existing.description();
    if (incoming.description() != null
        && (description == null || incoming.description().length() > description.length())) {
      description = incoming.description();
    }
    // 合并属性：新覆盖旧
    Map<String, String> mergedProps = new HashMap<>(existing.properties());
    mergedProps.putAll(incoming.properties());
    return new Entity(existing.id(), existing.name(), existing.type(), description,
        incoming.sourceDocId() != null ? incoming.sourceDocId() : existing.sourceDocId(),
        mergedProps);
  }
}
