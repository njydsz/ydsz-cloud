package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.DictItemMapper;
import com.njydsz.system.infra.mapper.DictTypeMapper;

/**
 * 字典类型仓储。
 *
 * <p>封装 DictTypeMapper / DictItemMapper，提供字典类型与字典项数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class DictRepository {

  private final DictTypeMapper dictTypeMapper;

  private final DictItemMapper dictItemMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 字典类型 Mapper
   */
  public DictTypeMapper getDictTypeMapper() {
    return dictTypeMapper;
  }

  /**
   * 获取字典项 Mapper。
   *
   * @return 字典项 Mapper
   */
  public DictItemMapper getDictItemMapper() {
    return dictItemMapper;
  }
}
