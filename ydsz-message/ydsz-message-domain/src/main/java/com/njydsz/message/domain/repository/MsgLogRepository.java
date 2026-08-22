package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;

import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.MsgLogDTO;
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
 *   <li>查询入参使用领域 Query（{@link MessageLogQueryDTO}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link MsgLogDTO}），禁止 VO 混入</li>
 *   <li>返回值使用领域 VO（{@link MsgLogVO}）</li>
 *   <li>禁止在 domain 层引入 MyBatis-Plus Wrapper / IPage 等持久化细节
 *   <li>遵循云顶编码规范第 34 节：domain 层定义接口，Infra 层实现接口
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgLogRepository {

  // ===== 基本 CRUD =====

  /**
   * 保存消息发送日志（插入）。
   *
   * @param dto 消息发送日志 DTO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgLogDTO dto);

  /**
   * 保存消息发送日志（VO 重载，供 server 层直接使用 domain VO 插入）。
   *
   * @param vo 消息发送日志 VO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgLogVO vo);

  /**
   * 全量更新消息发送日志。
   *
   * @param dto 消息发送日志 DTO（必须包含主键 ID）
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgLogDTO dto);

  /**
   * 全量更新消息发送日志（VO 重载，供 server 层直接使用 domain VO 更新）。
   *
   * @param vo 消息发送日志 VO（必须包含主键 ID）
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgLogVO vo);

  /**
   * 批量删除消息发送日志。
   *
   * @param id 日志 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  // ===== 查询方法 =====

  /**
   * 根据主键 ID 查询消息发送日志 VO。
   *
   * @param id 日志 ID
   * @return 消息发送日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgLogVO> findById(String id);

  /**
   * 根据条件查询单条消息发送日志 VO。
   *
   * @param query 查询参数
   * @return 消息发送日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgLogVO> findOne(MessageLogQueryDTO query);

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
   * @param list 消息发送日志 DTO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgLogDTO> list);
}
