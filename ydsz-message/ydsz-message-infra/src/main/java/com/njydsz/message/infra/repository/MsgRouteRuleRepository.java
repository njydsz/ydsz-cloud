package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.message.domain.entity.config.MsgRouteRule;

/**
 * 消息路由规则 Repository。
 *
 * <p>封装 {@code ydsz_msg_route_rule} 表的数据访问操作，为 server 层提供统一的数据访问接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgRouteRuleRepository {

  /**
   * 插入路由规则。
   *
   * @param entity 路由规则实体
   * @return 影响行数
   */
  int insert(MsgRouteRule entity);

  /**
   * 按 ID 查询路由规则。
   *
   * @param id 规则 ID
   * @return 路由规则实体，不存在返回 null
   */
  MsgRouteRule selectById(String id);

  /**
   * 按 ID 更新路由规则。
   *
   * @param entity 路由规则实体
   * @return 影响行数
   */
  int updateById(MsgRouteRule entity);

  /**
   * 按 ID 删除路由规则。
   *
   * @param id 规则 ID
   * @return 影响行数
   */
  int deleteById(String id);

  /**
   * 按条件查询路由规则列表。
   *
   * @param wrapper 查询条件
   * @return 路由规则列表
   */
  List<MsgRouteRule> selectList(LambdaQueryWrapper<MsgRouteRule> wrapper);

  /**
   * 分页查询路由规则。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  Page<MsgRouteRule> selectPage(Page<MsgRouteRule> page, LambdaQueryWrapper<MsgRouteRule> wrapper);
}
