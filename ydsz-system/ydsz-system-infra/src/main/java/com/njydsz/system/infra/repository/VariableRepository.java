package com.njydsz.system.infra.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.mapper.VariableMapper;

/**
 * 系统变量仓储。
 *
 * <p>封装 VariableMapper，提供系统变量数据访问能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class VariableRepository {

  private final VariableMapper variableMapper;

  /**
   * 获取原生 Mapper。
   *
   * @return 系统变量 Mapper
   */
  public VariableMapper getVariableMapper() {
    return variableMapper;
  }
}
