package com.njydsz.common.feign.assembler;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * NameAssembler 兜底空实现。
 *
 * <p>当业务模块未注册自定义 NameAssembler 时作为兜底， 所有解析请求返回空映射或跳过处理（调用方需自行处理）。
 *
 * <p>通过 {@code @ConditionalOnMissingBean} 确保仅在无其他实现时生效。
 *
 * TODO: NoOpNameAssembler 不属于 Feign 职责，应迁移至 ydsz-common-core。
 *     当前保留以兼容 ydsz-workflow 调用方。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NoOpNameAssembler implements NameAssembler {

  @Override
  public Map<String, String> batchResolveNames(NameType type, Collection<String> ids) {
    return Collections.emptyMap();
  }

  @Override
  public String resolveName(NameType type, String id) {
    return null;
  }

  @Override
  public <T> void enrich(
      Collection<T> objects,
      Function<T, String> idGetter,
      BiConsumer<T, String> nameSetter,
      NameType type) {
    // 兜底：不做任何操作
  }

  @Override
  public <T> void enrichOne(
      T obj, Function<T, String> idGetter, BiConsumer<T, String> nameSetter, NameType type) {
    // 兜底：不做任何操作
  }
}
