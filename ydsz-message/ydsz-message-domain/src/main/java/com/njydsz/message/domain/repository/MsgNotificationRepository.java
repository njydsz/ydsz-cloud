package com.njydsz.message.domain.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.njydsz.message.domain.dto.core.NotificationQueryDTO;
import com.njydsz.message.domain.vo.MsgNotificationVO;

/**
 * 站内通知仓储接口（domain 层契约）。
 *
 * <p>定义站内通知的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link MsgNotificationVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link NotificationQueryDTO}）或具体字段
 *   <li>CUD 入参使用领域 VO（{@link MsgNotificationVO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MsgNotificationRepository {

  /**
   * 批量保存站内通知。
   *
   * @param list 站内通知 VO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgNotificationVO> list);

  /**
   * 根据主键查询站内通知。
   *
   * @param id 通知 ID
   * @return 站内通知 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgNotificationVO> findById(String id);

  /**
   * 更新站内通知。
   *
   * @param vo 站内通知 VO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgNotificationVO vo);

  /**
   * 分页查询站内通知。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  IPage<MsgNotificationVO> findPage(NotificationQueryDTO query);

  /**
   * 按条件查询站内通知列表。
   *
   * @param query 查询参数
   * @return 站内通知 VO 列表
   */
  List<MsgNotificationVO> findList(NotificationQueryDTO query);

  /**
   * 按条件统计站内通知数量。
   *
   * @param query 查询参数
   * @return 数量
   */
  long count(NotificationQueryDTO query);

  /**
   * 根据主键删除站内通知。
   *
   * @param id 通知 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteById(String id);

  /**
   * 标记单条通知为已读。
   *
   * @param id 通知 ID
   * @param userId 接收人 ID
   * @return 影响行数
   */
  int markRead(String id, String userId);

  /**
   * 标记该用户所有未读通知为已读（分批）。
   *
   * @param userId 接收人 ID
   * @param batchSize 单批最大处理条数
   * @return 本批影响行数
   */
  int markAllRead(String userId, int batchSize);

  /**
   * 统计用户未读通知数。
   *
   * @param userId 接收人 ID
   * @return 未读数量
   */
  long countUnread(String userId);
}
