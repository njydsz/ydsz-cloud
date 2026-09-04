package com.njydsz.userinfo.infra.repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.converter.UserInfoUserConverter;
import com.njydsz.userinfo.domain.dto.SocialAccountDTO;
import com.njydsz.userinfo.domain.entity.SocialAccount;
import com.njydsz.userinfo.domain.repository.SocialAccountRepository;
import com.njydsz.userinfo.domain.vo.SocialAccountVO;
import com.njydsz.userinfo.infra.mapper.SocialAccountMapper;

/**
 * 社交账号绑定 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link SocialAccountRepository} 接口。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class SocialAccountRepositoryImpl implements SocialAccountRepository {

  private final SocialAccountMapper socialAccountMapper;
  private final UserInfoUserConverter converter;

  @Override
  public Optional<SocialAccountVO> findByPlatformAndOpenId(String platform, String openId) {
    SocialAccount entity = socialAccountMapper.selectByPlatformAndOpenId(platform, openId);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<SocialAccountVO> findByUserIdAndPlatform(String userId, String platform) {
    SocialAccount entity = socialAccountMapper.selectByUserIdAndPlatform(userId, platform);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<SocialAccountVO> listByUserId(String userId) {
    List<SocialAccount> entities = socialAccountMapper.selectByUserId(userId);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.socialAccountListToVO(entities);
  }

  @Override
  public void save(SocialAccountDTO dto) {
    SocialAccount entity = converter.dtoToEntity(dto);
    socialAccountMapper.insert(entity);
  }

  @Override
  public void deleteByUserIdAndPlatform(String userId, String platform) {
    socialAccountMapper.logicDeleteByUserIdAndPlatform(userId, platform);
  }
}
