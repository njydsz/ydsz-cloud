package com.njydsz.generator.converter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.lang.Nullable;

import com.njydsz.generator.entity.GenDatasource;
import com.njydsz.generator.vo.GenDatasourceRespVO;

/**
 * 数据源实体 ↔ VO 转换器。
 *
 * <p>用于将含敏感字段（password）的领域实体转换为外向响应 VO。
 * 纯静态映射，无外部依赖。
 *
 * @author ydsz-team
 * @since 26.09.06
 */
public final class DatasourceConverter {

  /** 工具类不可实例化。 */
  private DatasourceConverter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 将领域实体转换为响应 VO。
   *
   * @param entity 领域实体（可为 null）
   * @return 响应 VO，输入为 null 时返回 null
   */
  @Nullable
  public static GenDatasourceRespVO toRespVO(@Nullable GenDatasource entity) {
    if (entity == null) {
      return null;
    }
    return GenDatasourceRespVO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .jdbcUrl(entity.getJdbcUrl())
        .username(entity.getUsername())
        .dialect(entity.getDialect())
        .defaultFlag(entity.getDefaultFlag())
        .description(entity.getDescription())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 批量转换领域实体为响应 VO 列表。
   *
   * @param entities 领域实体列表（可为 null）
   * @return 响应 VO 列表，输入为 null 时返回空列表
   */
  public static List<GenDatasourceRespVO> toRespVOList(@Nullable List<GenDatasource> entities) {
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return entities.stream()
        .map(DatasourceConverter::toRespVO)
        .collect(Collectors.toList());
  }
}
