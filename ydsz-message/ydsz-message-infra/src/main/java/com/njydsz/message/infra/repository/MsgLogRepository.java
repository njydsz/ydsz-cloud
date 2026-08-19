package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.Param;

import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 消息发送日志仓储接口（Infra 层, 接受领域实体 {@link MsgLog}）。
 *
 * <p>提供 MyBatis-Plus 风格的数据库操作方法, 入参/出参使用领域实体 {@link MsgLog}, 内部转换为 {@code MsgLogDO} 后委托 {@link
 * com.njydsz.message.infra.mapper.core.MsgLogMapper} 执行。
 *
 * <p><b>设计定位：</b>Server 层服务(如 {@code MessageServiceImpl})直接操作领域实体, 无需在业务代码中手动 DO ↔ 领域
 * 实体转换, 由本接口封装持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.infra.mapper.core.MsgLogMapper MyBatis-Plus Mapper
 * @see com.njydsz.message.domain.entity.core.MsgLog 消息日志领域实体
 */
public interface MsgLogRepository {

  /**
   * 插入一条消息发送日志。
   *
   * @param log 消息日志领域实体
   * @return 影响行数
   */
  int insert(MsgLog log);

  /**
   * 根据 ID 更新消息发送日志。
   *
   * @param log 消息日志领域实体(必须包含主键 ID)
   * @return 影响行数
   */
  int updateById(MsgLog log);

  /**
   * 根据条件更新消息发送日志。
   *
   * @param entity 可为 null
   * @param updateWrapper 更新条件
   * @return 影响行数
   */
  int update(@Param(Constants.ENTITY) MsgLog entity, @Param(Constants.WRAPPER) Wrapper<MsgLog> updateWrapper);

  /**
   * 根据条件查询单条消息发送日志。
   *
   * @param queryWrapper 查询条件
   * @return 消息日志领域实体, 不存在返回 null
   */
  MsgLog selectOne(Wrapper<MsgLog> queryWrapper);

  /**
   * 根据主键 ID 查询消息发送日志。
   *
   * @param id 主键 ID
   * @return 消息日志领域实体, 不存在返回 null
   */
  MsgLog selectById(String id);

  /**
   * 根据条件查询消息发送日志列表。
   *
   * @param queryWrapper 查询条件
   * @return 消息日志领域实体列表
   */
  List<MsgLog> selectList(Wrapper<MsgLog> queryWrapper);

  /**
   * 根据条件统计消息发送日志数量。
   *
   * @param queryWrapper 查询条件
   * @return 数量
   */
  Long selectCount(Wrapper<MsgLog> queryWrapper);

  /**
   * 分页查询消息发送日志。
   *
   * @param page 分页参数
   * @param queryWrapper 查询条件
   * @return 分页结果(领域实体)
   */
  IPage<MsgLog> selectPage(IPage<MsgLog> page, Wrapper<MsgLog> queryWrapper);
}
