package com.njydsz.system.infra.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.infra.mapper.VariableMapper;

/**
 * 系统变量仓储。
 *
 * <p>封装 {@link VariableMapper}，提供变量域的业务语义数据访问能力（P1-4 去透传化）： 按 key 高频查询以语义方法暴露。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class VariableRepository {

  /** 状态常量：启用 */
  public static final String STATUS_ENABLED = "ENABLED";

  private final VariableMapper variableMapper;

  /**
   * 按变量键查询启用的变量（走 {@code uk_variable_key} 索引）。
   *
   * @param variableKey 变量键
   * @return 变量实体；不存在返回 {@code null}
   */
  public Variable selectEnabledByKey(String variableKey) {
    return variableMapper.selectOne(
        new QueryWrapper<Variable>()
            .eq("variable_key", variableKey)
            .eq("status", STATUS_ENABLED)
            .last("LIMIT 1"));
  }

  /**
   * 按变量键查询变量（不区分状态，用于版本快照 / 回滚定位）。
   *
   * @param variableKey 变量键
   * @return 变量实体；不存在返回 {@code null}
   */
  public Variable selectByKeyIgnoreStatus(String variableKey) {
    return variableMapper.selectOne(
        new QueryWrapper<Variable>()
            .eq("variable_key", variableKey)
            .eq("deleted", 0)
            .last("LIMIT 1"));
  }

  /**
   * 获取原生 Mapper（用于分页等动态条件查询场景）。
   *
   * @return 系统变量 Mapper
   */
  public VariableMapper getVariableMapper() {
    return variableMapper;
  }
}
