package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.core.response.PageResponse;

import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.entity.MsgLog;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息发送日志仓储接口（domain 层契约）。
 *
 * <p>定义消息发送日志的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgLogVO}）或领域实体（{@link MsgLog}）
 *   <li>CUD 入参使用领域实体（{@link MsgLog}），禁止接受 infra 实体
 *   <li>查询入参使用领域 Query（{@link MessageLogQueryDTO}）或 MyBatis-Plus Wrapper
 *   <li>遵循云顶编码规范第 34 节：domain 层定义接口，Infra 层实现接口
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgLogRepository {

  // ===== 基本 CRUD =====

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
   * @param log 消息日志领域实体（必须包含主键 ID）
   * @return 影响行数
   */
  int updateById(MsgLog log);

  /**
   * 根据条件更新消息发送日志。
   *
   * @param entity 领域实体（可为 null，仅用于类型信息）
   * @param updateWrapper 更新条件
   * @return 影响行数
   */
  int update(MsgLog entity, Wrapper<MsgLog> updateWrapper);

  /**
   * 保存消息发送日志（插入或更新）。
   *
   * @param vo 消息发送日志 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgLogVO vo);

  /**
   * 批量删除消息发送日志。
   *
   * @param id 日志 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  // ===== 查询方法 =====

  /**
   * 根据主键 ID 查询消息发送日志。
   *
   * @param id 主键 ID
   * @return 消息日志领域实体，不存在返回 null
   */
  MsgLog selectById(String id);

  /**
   * 根据 ID 查询消息发送日志 VO。
   *
   * @param id 日志 ID
   * @return 消息发送日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgLogVO> findById(String id);

  /**
   * 根据条件查询单条消息发送日志。
   *
   * @param queryWrapper 查询条件
   * @return 消息日志领域实体，不存在返回 null
   */
  MsgLog selectOne(Wrapper<MsgLog> queryWrapper);

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
   * @return 分页结果（领域实体）
   */
  IPage<MsgLog> selectPage(IPage<MsgLog> page, Wrapper<MsgLog> queryWrapper);

  /**
   * 分页查询消息返回值使用 VO。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgLogVO>> findPage(MessageLogQueryDTO query);

  /**
   * 按条件查询消息发送日志列表（返回 VO）。
   *
   * @param query 查询参数
   * @return 消息发送日志 VO 列表
   */
  List<MsgLogVO> findList(MessageLogQueryDTO query);

  /**
   * 按条件统计消息发送日志数量。
   *
   * @param query 查询参数
   * @return 数量
   */
  long count(MessageLogQueryDTO query);

  /**
   * 批量保存消息发送日志。
   *
   * @param list 消息发送日志 VO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgLogVO> list);
}
