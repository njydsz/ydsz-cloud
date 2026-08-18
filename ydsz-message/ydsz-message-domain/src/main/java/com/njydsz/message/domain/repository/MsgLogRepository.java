package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;

import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
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
 *   <li>返回领域 VO（{@link MsgLogVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link MessageLogQueryDTO}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgLogVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgLogRepository {

  /**
   * 保存消息发送日志（插入或更新）。
   *
   * @param vo 消息发送日志 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgLogVO vo);

  /**
   * 根据主键查询消息发送日志。
   *
   * @param id 日志 ID
   * @return 消息发送日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgLogVO> findById(String id);

  /**
   * 分页查询消息发送日志。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgLogVO>> findPage(MessageLogQueryDTO query);

  /**
   * 按条件查询消息发送日志列表。
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
   * 批量删除消息发送日志。
   *
   * @param id 日志 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 批量保存消息发送日志。
   *
   * @param list 消息发送日志 VO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgLogVO> list);
}
