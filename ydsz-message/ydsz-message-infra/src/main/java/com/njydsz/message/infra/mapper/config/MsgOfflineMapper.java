package com.njydsz.message.infra.mapper.config;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.njydsz.message.infra.entity.MsgOffline;

/**
 * P0-3: 离线消息持久化 Mapper
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface MsgOfflineMapper extends BaseMapper<MsgOffline> {

  /**
   * P3-6: 批量插入离线消息（XML foreach 单条 INSERT VALUES (...), (...)）。
   *
   * <p>调用方需在传入前用 {@code IdWorker.getIdStr()} 预生成 ID 赋给每个 entity， 以保证批量 insert 后能拿到主键。
   *
   * @param list 离线消息实体列表
   * @return 影响行数
   */
  int insertBatch(@Param("list") List<MsgOffline> list);

  /**
   * 批量标记已推送。
   *
   * @param userId 用户 ID
   * @return 更新行数
   */
  @Update(
      "UPDATE ydsz_msg_offline SET status = 'PUSHED', pushed_at = NOW() "
          + "WHERE user_id = #{userId} AND status = 'PENDING' AND deleted = 0")
  int markPushedByUser(@Param("userId") String userId);

  /**
   * 清理过期消息（状态改为 EXPIRED）。
   *
   * @return 更新行数
   */
  @Update(
      "UPDATE ydsz_msg_offline SET status = 'EXPIRED' "
          + "WHERE status = 'PENDING' AND expired_at < NOW() AND deleted = 0")
  int markExpired();
}
