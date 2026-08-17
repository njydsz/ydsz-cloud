package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.ShareRecipient;

/**
 * 分享目标用户仓储接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ShareRecipientRepository {

  /**
   * 批量保存分享目标用户。
   *
   * @param recipients 目标用户列表
   */
  void saveBatch(List<ShareRecipient> recipients);

  /**
   * 查询分享链接的目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户列表
   */
  List<ShareRecipient> findByShareId(String shareId);

  /**
   * 查询用户作为接收者的分享列表。
   *
   * @param recipientId 接收者用户 ID
   * @return 分享接收记录列表
   */
  List<ShareRecipient> findByRecipientId(String recipientId);

  /**
   * 标记用户已查看分享。
   *
   * @param shareId 分享链接 ID
   * @param recipientId 接收者 ID
   * @return 受影响行数
   */
  int markAsViewed(String shareId, String recipientId);
}
