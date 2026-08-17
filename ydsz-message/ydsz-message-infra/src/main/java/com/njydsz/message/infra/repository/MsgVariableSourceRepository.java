package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.config.MsgVariableSource;

/**
 * 消息变量数据源 Repository。
 *
 * <p>封装 {@code ydsz_msg_variable_source} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgVariableSourceRepository {

  /**
   * 按条件查询变量数据源列表。
   *
   * @param wrapper 查询条件
   * @return 变量数据源列表
   */
  List<MsgVariableSource> selectList(LambdaQueryWrapper<MsgVariableSource> wrapper);
}
