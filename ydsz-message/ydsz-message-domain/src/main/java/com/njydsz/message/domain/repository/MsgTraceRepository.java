package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.config.MsgTrace;

/**
 * 消息轨迹 Repository。
 *
 * <p>封装 {@code ydsz_msg_trace} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgTraceRepository {

  /**
   * 插入轨迹记录。
   *
   * @param entity 轨迹实体
   * @return 影响行数
   */
  int insert(MsgTrace entity);

  /**
   * 按条件查询轨迹列表。
   *
   * @param wrapper 查询条件
   * @return 轨迹列表
   */
  List<MsgTrace> selectList(LambdaQueryWrapper<MsgTrace> wrapper);
}
