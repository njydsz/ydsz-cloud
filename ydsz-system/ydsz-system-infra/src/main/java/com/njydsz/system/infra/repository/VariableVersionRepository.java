package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.VariableVersionMapper;

/**
 * 变量版本快照仓储。
 *
 * <p>封装 VariableVersionMapper，提供变量版本数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class VariableVersionRepository {

  private final VariableVersionMapper variableVersionMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 变量版本 Mapper
   */
  public VariableVersionMapper getVariableVersionMapper() {
    return variableVersionMapper;
  }
}
