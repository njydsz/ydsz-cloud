package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.DictVersionMapper;

/**
 * 字典版本快照仓储。
 *
 * <p>封装 DictVersionMapper，提供字典版本数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class DictVersionRepository {

  private final DictVersionMapper dictVersionMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 字典版本 Mapper
   */
  public DictVersionMapper getDictVersionMapper() {
    return dictVersionMapper;
  }
}
