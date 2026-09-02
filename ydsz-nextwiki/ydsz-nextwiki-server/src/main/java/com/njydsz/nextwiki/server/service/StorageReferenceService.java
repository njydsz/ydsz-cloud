package com.njydsz.nextwiki.domain.service;

/**
 * 存储引用计数服务。
 *
 * <p>维护 storageKey → 引用计数的映射。秒传、复制场景下多 FileNodeVO 共享同一 storageKey，
 * 需通过引用计数确保物理对象仅在最后一个引用移除后才被安全删除，避免悬空引用/误删。
 *
 * <p>实现类应保证原子性：并发场景下 {@link #increment} / {@link #decrement} 需通过 Redis INCR/DECR 等原子操作保证计数准确。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface StorageReferenceService {

  /**
   * 增加 storageKey 的引用计数（普通上传 / 秒传命中 / 复制时调用）。
   *
   * @param storageKey 底层存储对象键，不可为 {@code null} / blank
   * @return 增加后的当前计数
   */
  long increment(String storageKey);

  /**
   * 减少 storageKey 的引用计数（删除 / 覆盖时调用）。
   *
   * <p>返回 0 表示最后一个引用已移除，调用方可安全执行物理删除。
   *
   * @param storageKey 底层存储对象键，不可为 {@code null} / blank
   * @return 减少后的当前计数
   */
  long decrement(String storageKey);

  /**
   * 查询 storageKey 的当前引用计数。
   *
   * @param storageKey 底层存储对象键
   * @return 当前计数；key 不存在返回 0
   */
  long getCount(String storageKey);
}
