package com.njydsz.message.server.service.config;

import java.util.List;

import com.njydsz.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.message.domain.entity.config.MsgUserChannel;

/**
 * 用户渠道绑定服务接口。
 *
 * <p>维护用户在各渠道的地址/账号绑定。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserChannelBindingService {

  /**
   * 新增或更新通道绑定（按 userId + channelType 唯一约束 upsert）。
   *
   * @param dto 绑定参数
   * @return 绑定实体
   */
  MsgUserChannel upsert(UserChannelBindingDTO dto);

  /**
   * 删除通道绑定（逻辑删除）。
   *
   * @param id 绑定 ID
   */
  void delete(String id);

  /**
   * 查询用户所有通道绑定。
   *
   * @param userId 用户 ID
   * @return 绑定列表
   */
  List<MsgUserChannel> listByUser(String userId);

  /**
   * 按用户 + 通道类型查询绑定（优先返回主绑定）。
   *
   * @param userId 用户 ID
   * @param channelType 通道类型
   * @return 绑定实体；无绑定时返回 null
   */
  MsgUserChannel getByUserAndChannel(String userId, String channelType);

  /**
   * P0-1 核心方法：按用户 + 通道类型解析通道用户标识。
   *
   * <p>优先返回 is_primary=1 的绑定；若无主绑定则返回第一条； 若无任何绑定则返回 null（调用方降级为原 receiver 值）。
   *
   * @param userId 用户 ID
   * @param channelType 通道类型（大写）
   * @return 通道用户标识（手机号/邮箱/钉钉userId 等）；无绑定时返回 null
   */
  String resolveChannelUserId(String userId, String channelType);
}
