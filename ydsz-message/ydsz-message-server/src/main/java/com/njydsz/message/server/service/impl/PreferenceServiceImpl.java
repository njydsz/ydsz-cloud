package com.njydsz.message.server.service.impl.config;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.PreferenceUpsertDTO;
import com.njydsz.message.domain.query.MsgPreferenceQuery;
import com.njydsz.message.domain.repository.MsgPreferenceRepository;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.server.service.config.PreferenceService;

/**
 * 通知偏好服务实现。
 *
 * <p>管理用户/租户的通知偏好设置：免打扰时段、渠道白/黑名单、模板订阅/退订、频次上限。
 *
 * <p>偏好检查在消息发送前触发，未通过则跳过发送并打上 SKIPPED 状态。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

  /** 用户消息偏好 Repository */
  private final MsgPreferenceRepository msgPreferenceRepository;

  /**
   * {@inheritDoc}
   *
   * <p>按 (userId, channel, bizType) 查找已有偏好：存在则更新，不存在则新建。 bizType 为空时默认 {@link
   * MessageConstants#DEFAULT_BIZ_TYPE}。
   */
  @Override
  public MsgPreferenceVO upsert(PreferenceUpsertDTO dto) {
    if (dto == null
        || !StringUtils.hasText(dto.getUserId())
        || !StringUtils.hasText(dto.getChannel())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID 与通道不能为空")
          .build();
    }
    String bizType =
        StringUtils.hasText(dto.getBizType())
            ? dto.getBizType()
            : MessageConstants.DEFAULT_BIZ_TYPE;
    MsgPreferenceQuery query = new MsgPreferenceQuery();
    query.setUserId(dto.getUserId());
    query.setChannel(dto.getChannel());
    query.setBizType(bizType);
    Optional<MsgPreferenceVO> existing = msgPreferenceRepository.findOne(query);
    if (existing.isEmpty()) {
      MsgPreferenceVO vo = new MsgPreferenceVO();
      vo.setUserId(dto.getUserId());
      vo.setChannel(dto.getChannel());
      vo.setBizType(bizType);
      vo.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
      vo.setDndEnabled(dto.getDndEnabled() == null ? 0 : dto.getDndEnabled());
      vo.setDndStart(dto.getDndStart());
      vo.setDndEnd(dto.getDndEnd());
      vo.setDailyLimit(dto.getDailyLimit());
      vo.setHourlyLimit(dto.getHourlyLimit());
      vo.setDigestEnabled(dto.getDigestEnabled() == null ? 0 : dto.getDigestEnabled());
      vo.setDigestFrequency(dto.getDigestFrequency());
      vo.setLocale(dto.getLocale());
      vo.setExtra(dto.getExtra());
      msgPreferenceRepository.save(vo);
      log.info(
          "[Preference] 新建偏好: user={} channel={} bizType={}",
          dto.getUserId(),
          dto.getChannel(),
          bizType);
      return vo;
    }
    MsgPreferenceVO vo = existing.get();
    vo.setEnabled(dto.getEnabled() == null ? vo.getEnabled() : dto.getEnabled());
    vo.setDndEnabled(
        dto.getDndEnabled() == null ? vo.getDndEnabled() : dto.getDndEnabled());
    vo.setDndStart(dto.getDndStart());
    vo.setDndEnd(dto.getDndEnd());
    vo.setDailyLimit(dto.getDailyLimit());
    vo.setHourlyLimit(dto.getHourlyLimit());
    vo.setDigestEnabled(
        dto.getDigestEnabled() == null ? vo.getDigestEnabled() : dto.getDigestEnabled());
    vo.setDigestFrequency(dto.getDigestFrequency());
    vo.setLocale(dto.getLocale());
    vo.setExtra(dto.getExtra());
    msgPreferenceRepository.update(vo);
    return vo;
  }

  /**
   * {@inheritDoc}
   *
   * <p>优先按精确 bizType 查询，未命中时回退 {@link MessageConstants#DEFAULT_BIZ_TYPE} 默认偏好。
   *
   * @param userId 用户 ID
   * @param channel 通道类型
   * @param bizType 业务类型（可选，为空时使用默认）
   * @return 偏好记录，不存在时返回 null
   */
  @Override
  public MsgPreferenceVO getByUser(String userId, String channel, String bizType) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(channel)) {
      return null;
    }
    String bt = StringUtils.hasText(bizType) ? bizType : MessageConstants.DEFAULT_BIZ_TYPE;
    // 优先精确 bizType
    MsgPreferenceQuery query = new MsgPreferenceQuery();
    query.setUserId(userId);
    query.setChannel(channel);
    query.setBizType(bt);
    Optional<MsgPreferenceVO> entity = msgPreferenceRepository.findOne(query);
    if (entity.isPresent()) {
      return entity.get();
    }
    // 回退默认
    if (!MessageConstants.DEFAULT_BIZ_TYPE.equals(bt)) {
      query.setBizType(MessageConstants.DEFAULT_BIZ_TYPE);
      entity = msgPreferenceRepository.findOne(query);
    }
    return entity.orElse(null);
  }

  /**
   * {@inheritDoc}
   *
   * @param userId 用户 ID
   * @return 偏好列表，按 channel 升序排列
   */
  @Override
  public List<MsgPreferenceVO> listByUser(String userId) {
    if (!StringUtils.hasText(userId)) {
      return List.of();
    }
    MsgPreferenceQuery query = new MsgPreferenceQuery();
    query.setUserId(userId);
    return msgPreferenceRepository.findList(query);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void delete(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("偏好 ID 不能为空")
          .build();
    }
    msgPreferenceRepository.deleteById(id);
  }
}
