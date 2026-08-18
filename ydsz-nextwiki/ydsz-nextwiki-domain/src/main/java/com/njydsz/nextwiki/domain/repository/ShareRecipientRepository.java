package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;

/**
 * 分享目标用户仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link ShareRecipientVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link ShareRecipientDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ShareRecipientRepository {

  /**
   * 批量保存分享目标用户
   *
   * @param dtos 目标用户 DTO 列表
   */
  void saveBatch(List<ShareRecipientDTO> dtos);

  /**
   * 查询分享链接的目标用户列表
   *
   * @param shareId 分享链接ID
   * @return 目标用户 VO 列表
   */
  List<ShareRecipientVO> findByShareId(String shareId);

  /**
   * 查询用户作为接收者的分享列表
   *
   * @param recipientId 接收者用户ID
   * @return 分享接收记录 VO 列表
   */
  List<ShareRecipientVO> findByRecipientId(String recipientId);

  /**
   * 标记用户已查看分享
   *
   * @param shareId 分享链接ID
   * @param recipientId 接收者ID
   * @return 受影响行数
   */
  int markAsViewed(String shareId, String recipientId);
}
