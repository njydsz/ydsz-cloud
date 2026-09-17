package com.njydsz.agent.domain.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * 知识图谱存储接口。
 *
 * <p>支持实体和关系的 CRUD 与图遍历查询。实现可选择内存、Neo4j 或关系数据库。
 *
 * <p><b>线程安全</b>：实现须保证并发读写安全，查询返回列表不应暴露内部可变引用。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface KnowledgeGraphStore {

  // ===== 实体操作 =====

  /**
   * 添加或更新实体。
   *
   * <p>若实体 ID 已存在，则合并信息（更新描述和属性）。
   *
   * @param entity 待添加的实体
   */
  void addEntity(Entity entity);

  /**
   * 根据 ID 查找实体。
   *
   * @param entityId 实体 ID
   * @return 匹配的实体，不存在时返回空
   */
  Optional<Entity> findEntityById(String entityId);

  /**
   * 根据名称模糊查找实体（精确匹配，忽略大小写）。
   *
   * @param name 实体名称
   * @return 匹配的实体列表
   */
  List<Entity> findEntitiesByName(String name);

  /**
   * 根据类型查找实体。
   *
   * @param type 实体类型
   * @return 匹配类型的所有实体
   */
  List<Entity> findEntitiesByType(EntityType type);

  // ===== 关系操作 =====

  /**
   * 添加或更新关系。
   *
   * <p>若关系 ID 已存在，则更新置信度和属性。
   *
   * @param relation 待添加的关系
   */
  void addRelation(Relation relation);

  /**
   * 查找从指定实体出发的所有关系（出边）。
   *
   * @param entityId 实体 ID
   * @return 关系列表
   */
  List<Relation> findRelationsFrom(String entityId);

  /**
   * 查找指向指定实体的所有关系（入边）。
   *
   * @param entityId 实体 ID
   * @return 关系列表
   */
  List<Relation> findRelationsTo(String entityId);

  /**
   * 查找与指定实体相关的所有关系（出边 + 入边）。
   *
   * @param entityId 实体 ID
   * @return 关系列表
   */
  List<Relation> findRelations(String entityId);

  // ===== 图遍历 =====

  /**
   * BFS 遍历：从指定实体出发，返回 maxDepth 跳内的所有实体（包含起始实体）。
   *
   * <p>使用广度优先搜索逐层遍历边关系，回到已访问过的节点时不再重复入队。
   *
   * @param startEntityId 起始实体 ID
   * @param maxDepth 最大跳数（>= 0），0 表示仅返回起始实体
   * @return 范围内所有实体列表
   */
  List<Entity> traverse(String startEntityId, int maxDepth);

  /**
   * 子图查询：返回包含指定实体及其 N 跳邻域的实体和关系。
   *
   * <p>执行 BFS 遍历，收集范围内的所有实体和去重后的关系。
   *
   * @param entityId 中心实体 ID
   * @param depth 跳数（>= 0）
   * @return 子图（实体列表 + 关系列表）
   */
  SubGraph subgraph(String entityId, int depth);

  /**
   * 删除指定实体及其所有关系。
   *
   * @param entityId 实体 ID
   */
  void deleteEntity(String entityId);

  /**
   * 清空图谱。
   */
  void clear();

  /**
   * 获取实体总数。
   *
   * @return 实体数
   */
  long entityCount();

  /**
   * 获取关系总数。
   *
   * @return 关系数
   */
  long relationCount();

  /**
   * 子图值对象。
   *
   * @param entities 实体列表
   * @param relations 关系列表
   */
  record SubGraph(List<Entity> entities, List<Relation> relations) {}
}
