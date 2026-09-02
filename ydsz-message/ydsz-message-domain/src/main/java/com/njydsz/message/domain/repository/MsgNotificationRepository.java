package com.njydsz.message.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MsgNotificationDTO;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
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
 *   <li>CUD 入参使用领域 DTO（{@link MsgNotificationDTO}），禁止 VO 混入
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgNotificationRepository {

  /**
   * 保存单条站内通知。
   *
   * @param dto 站内通知 DTO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgNotificationDTO dto);

  /**
   * 批量保存站内通知。
   *
   * @param list 站内通知 DTO 列表
   * @return 保存成功返回 {@code true}
   */
  boolean saveBatch(List<MsgNotificationDTO> list);

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
   * @param dto 站内通知 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgNotificationDTO dto);

  /**
   * 分页查询站内通知。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<MsgNotificationVO>> findPage(NotificationQueryDTO query);

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
   * 标记用户全部未读通知为已读（可按业务类型过滤）。
   *
   * <p>仅更新 {@code readStatus=0 且 recallStatus='NONE'} 的通知。
   *
   * @param userId 接收人 ID
   * @param bizType 业务类型（可选，为空表示全部业务）
   * @return 实际更新的通知条数
   */
  int markAllReadByBizType(String userId, String bizType);

  /**
   * 统计用户未读通知数。
   *
   * @param userId 接收人 ID
   * @return 未读数量
   */
  long countUnread(String userId);

  /**
   * 标记过期通知为已删除。
   *
   * <p>仅更新 {@code expiredAt < :now 且 deleted=0} 的通知。
   *
   * @param now 当前时间
   * @return 实际更新的通知条数
   */
  int markExpired(LocalDateTime now);
}
