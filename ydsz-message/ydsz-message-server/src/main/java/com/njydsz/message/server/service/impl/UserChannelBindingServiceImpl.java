package com.njydsz.message.server.service.impl.config;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.dto.UserChannelBindingDTO;
import com.njydsz.message.domain.query.MsgUserChannelQuery;
import com.njydsz.message.domain.repository.MsgUserChannelRepository;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.server.service.config.UserChannelBindingService;

/**
 * 用户渠道绑定服务实现。
 *
 * <p>管理用户在各渠道的地址/账号绑定 ({@code ydsz_msg_user_channel})：
 *
 * <p>手机号、邮箱、企业微信 UserID、IM UserID、WebSocket SessionId。
 *
 * <p>发送前根据用户 ID 解析实际地址，支持多渠道优先级。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserChannelBindingServiceImpl implements UserChannelBindingService {

  /** 用户-通道绑定 Repository */
  private final MsgUserChannelRepository msgUserChannelRepository;

  /**
   * {@inheritDoc}
   *
   * <p>按 userId + channelType 查找已有绑定：存在则更新 channelUserId/verified/isPrimary/extra，
   * 不存在则新建。channelType 统一转大写存储。
   *
   * @param dto 绑定参数
   * @return 绑定视图对象
   */
  @Override
  public MsgUserChannelVO upsert(UserChannelBindingDTO dto) {
    if (dto == null
        || !StringUtils.hasText(dto.getUserId())
        || !StringUtils.hasText(dto.getChannelType())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户ID和通道类型不能为空")
          .build();
    }
    String channelType = dto.getChannelType().trim().toUpperCase();

    // 查找已有绑定
    MsgUserChannelVO existing = getByUserAndChannel(dto.getUserId(), channelType);
    if (existing != null) {
      existing.setChannelUserId(dto.getChannelUserId());
      if (dto.getVerified() != null) {
        existing.setVerified(dto.getVerified());
      }
      if (dto.getIsPrimary() != null) {
        existing.setIsPrimary(dto.getIsPrimary());
      }
      if (dto.getExtra() != null) {
        existing.setExtra(dto.getExtra());
      }
      msgUserChannelRepository.update(existing);
      log.info(
          "[UserChannelBinding] 更新绑定: userId={} channel={} channelUserId={}",
          dto.getUserId(),
          channelType,
          dto.getChannelUserId());
      return existing;
    }

    MsgUserChannelVO vo = new MsgUserChannelVO();
    vo.setUserId(dto.getUserId());
    vo.setChannelType(channelType);
    vo.setChannelUserId(dto.getChannelUserId());
    vo.setVerified(dto.getVerified() != null ? dto.getVerified() : 0);
    vo.setIsPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : 0);
    vo.setExtra(dto.getExtra());
    msgUserChannelRepository.save(vo);
    log.info(
        "[UserChannelBinding] 新增绑定: userId={} channel={} channelUserId={}",
        dto.getUserId(),
        channelType,
        dto.getChannelUserId());
    return vo;
  }

  /**
   * {@inheritDoc}
   *
   * <p>按 ID 逻辑删除绑定记录，id 为空时直接返回。
   *
   * @param id 绑定 ID
   */
  @Override
  public void delete(String id) {
    if (!StringUtils.hasText(id)) {
      return;
    }
    msgUserChannelRepository.deleteById(id);
  }

  /**
   * {@inheritDoc}
   *
   * <p>按 userId 隔离，返回用户的所有通道绑定列表。
   *
   * @param userId 用户 ID
   * @return 绑定视图对象列表，userId 为空时返回空列表
   */
  @Override
  public List<MsgUserChannelVO> listByUser(String userId) {
    if (!StringUtils.hasText(userId)) {
      return List.of();
    }
    MsgUserChannelQuery query = new MsgUserChannelQuery();
    query.setUserId(userId);
    return msgUserChannelRepository.findList(query);
  }

  /**
   * {@inheritDoc}
   *
   * <p>channelType 统一转大写查询，按 userId 隔离，优先返回 isPrimary=1 的记录。
   *
   * @param userId 用户 ID
   * @param channelType 通道类型（不区分大小写）
   * @return 绑定视图对象；无绑定时返回 null
   */
  @Override
  public MsgUserChannelVO getByUserAndChannel(String userId, String channelType) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(channelType)) {
      return null;
    }
    MsgUserChannelQuery query = new MsgUserChannelQuery();
    query.setUserId(userId);
    query.setChannelType(channelType.trim().toUpperCase());
    query.setPrimaryFirst(true);
    return msgUserChannelRepository.findOne(query).orElse(null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>查询用户的通道绑定 channelUserId，无绑定时返回 null（降级使用原 receiver）， 绑定未验证时记 WARN 日志但仍返回。
   *
   * @param userId 用户 ID
   * @param channelType 通道类型
   * @return 通道用户 ID，无绑定时返回 null
   */
  @Override
  public String resolveChannelUserId(String userId, String channelType) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(channelType)) {
      return null;
    }
    MsgUserChannelVO binding = getByUserAndChannel(userId, channelType);
    if (binding == null) {
      log.debug(
          "[UserChannelBinding] 无通道绑定,降级使用原 receiver: userId={} channel={}", userId, channelType);
      return null;
    }
    if (binding.getVerified() != null && binding.getVerified() == 0) {
      log.warn(
          "[UserChannelBinding] 通道绑定未验证: userId={} channel={} channelUserId={}",
          userId,
          channelType,
          binding.getChannelUserId());
    }
    return binding.getChannelUserId();
  }
}
