package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.message.domain.entity.config.MsgUserChannel;

/**
 * 用户通道绑定 Repository。
 *
 * <p>封装 {@code ydsz_msg_user_channel} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgUserChannelRepository {

  /**
   * 插入通道绑定。
   *
   * @param entity 通道绑定实体
   * @return 影响行数
   */
  int insert(MsgUserChannel entity);

  /**
   * 按 ID 更新通道绑定。
   *
   * @param entity 通道绑定实体
   * @return 影响行数
   */
  int updateById(MsgUserChannel entity);

  /**
   * 按 ID 删除通道绑定。
   *
   * @param id 绑定 ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 按条件查询单条通道绑定。
   *
   * @param wrapper 查询条件
   * @return 通道绑定实体，不存在返回 null
   */
  MsgUserChannel selectOne(LambdaQueryWrapper<MsgUserChannel> wrapper);

  /**
   * 按条件查询通道绑定列表。
   *
   * @param wrapper 查询条件
   * @return 通道绑定列表
   */
  List<MsgUserChannel> selectList(LambdaQueryWrapper<MsgUserChannel> wrapper);
}
