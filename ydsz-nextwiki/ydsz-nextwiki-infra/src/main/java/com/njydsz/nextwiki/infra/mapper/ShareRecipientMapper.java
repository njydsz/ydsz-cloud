package com.njydsz.nextwiki.infra.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.infra.entity.ShareRecipientDO;

/**
 * 分享目标用户 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ShareRecipientMapper extends BaseMapper<ShareRecipientDO> {

  /**
   * 查询分享链接的目标用户列表。
   *
   * @param shareId 分享链接 ID
   * @return 目标用户列表
   */
  List<ShareRecipientDO> selectByShareId(@Param("shareId") String shareId);

  /**
   * 查询用户作为接收者的分享列表。
   *
   * @param recipientId 接收者用户 ID
   * @return 分享接收记录列表
   */
  List<ShareRecipientDO> selectByRecipientId(@Param("recipientId") String recipientId);

  /**
   * 标记用户已查看分享。
   *
   * @param shareId 分享链接 ID
   * @param recipientId 接收者 ID
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_share_recipient SET status = 'VIEWED', viewed_at = NOW(), "
          + "updated_at = NOW() WHERE share_id = #{shareId} AND recipient_id = #{recipientId} "
          + "AND status = 'ACTIVE' AND deleted = 0")
  int markAsViewed(@Param("shareId") String shareId, @Param("recipientId") String recipientId);
}
