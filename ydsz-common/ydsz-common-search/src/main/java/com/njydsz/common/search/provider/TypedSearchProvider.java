package com.njydsz.common.search.provider;

import com.njydsz.common.search.core.IndexDocument;

/**
 * 带运行时类型令牌的搜索提供者。
 *
 * <p>扩展 {@link SearchProvider}，暴露实体类型的运行时 {@link Class} 对象（{@link #getEntityType()}）， 使注册中心在运行时能安全地将任意实体转为
 * {@link IndexDocument}，而无需调用方显式强转。
 *
 * <p>典型用途（注册中心的泛型分发场景）：
 *
 * <pre>{@code
 * SearchProvider<?> raw = registry.getProvider("project");
 * if (raw instanceof TypedSearchProvider<?> typed) {
 *   Object entity = loadFromDB(typed.getEntityType(), id);
 *   IndexDocument doc = typed.toIndexDocumentUnsafe(entity);
 * }
 * }</pre>
 *
 * @param <T> 实体类型
 * @author ydsz-team
 * @since 26.09.21
 */
public interface TypedSearchProvider<T> extends SearchProvider<T> {

  /**
   * 获取实体类型的运行时 Class 对象（类型令牌）。
   *
   * <p>实现方式：匿名子类 + {@code getGenericInterfaces()} 解析； 或实现类构造时显式传入 {@code Class<T>}。
   *
   * @return 实体类型 Class，永不为 {@code null}
   */
  Class<T> getEntityType();

  /**
   * 将未知类型实体（通常为 {@code Object} 或从 DB 加载的业务实体）安全转为索引文档。
   *
   * <p>内部调用 {@link #getEntityType()} 进行类型校验，校验不通过时抛出
   * {@link ClassCastException}，避免因上下文中类型信息丢失导致的隐蔽 bug。
   *
   * @param entity 业务实体，不可为 {@code null}
   * @return 索引文档
   * @throws ClassCastException entity 类型与 {@link #getEntityType()} 不匹配
   */
  default IndexDocument toIndexDocumentUnsafe(Object entity) {
    if (entity == null) {
      throw new NullPointerException("[TypedSearchProvider] entity 不能为 null");
    }
    if (!getEntityType().isInstance(entity)) {
      throw new ClassCastException(
          "[TypedSearchProvider] 类型不匹配: 期望 "
              + getEntityType().getName()
              + ", 实际 "
              + entity.getClass().getName());
    }
    T typed = getEntityType().cast(entity);
    return toIndexDocument(typed);
  }
}
