package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.SocialClientCreateDTO;
import com.njydsz.userinfo.domain.dto.SocialClientUpdateDTO;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.domain.repository.SocialClientRepository;
import com.njydsz.userinfo.domain.vo.SocialClientVO;
import com.njydsz.userinfo.infra.converter.SocialClientConverter;
import com.njydsz.userinfo.infra.entity.SocialClientDO;
import com.njydsz.userinfo.infra.mapper.SocialClientMapper;

/**
 * 社交平台客户端配置仓储实现（P1-1）。
 *
 * <p>通过 {@link SocialClientMapper} 访问数据库，使用 {@link SocialClientConverter} 完成 DO ↔ VO 转换。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SocialClientRepositoryImpl implements SocialClientRepository {

  private final SocialClientMapper mapper;
  private final PasswordEncoder passwordEncoder;

  @Override
  public List<SocialClientVO> findByPage(SocialClientPageQuery query) {
    LambdaQueryWrapper<SocialClientDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SocialClientDO::getDeleted, false)
        .like(query.getPlatform() != null && !query.getPlatform().isBlank(),
            SocialClientDO::getPlatform, query.getPlatform())
        .like(query.getPlatformName() != null && !query.getPlatformName().isBlank(),
            SocialClientDO::getPlatformName, query.getPlatformName())
        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
            SocialClientDO::getStatus, query.getStatus())
        .orderByAsc(SocialClientDO::getSortOrder);

    List<SocialClientDO> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(SocialClientConverter.INSTANT::entityToVo)
        .toList();
  }

  @Override
  public List<SocialClientVO> findEnabled() {
    List<SocialClientDO> entities = mapper.selectEnabledClients();
    return entities.stream()
        .map(SocialClientConverter.INSTANT::entityToVo)
        .toList();
  }

  @Override
  public Optional<SocialClientVO> findByPlatform(String platform) {
    SocialClientDO entity = mapper.selectByPlatform(platform.toUpperCase());
    return entity != null
        ? Optional.of(SocialClientConverter.INSTANT.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public void save(SocialClientCreateDTO dto) {
    SocialClientDO entity = new SocialClientDO();
    entity.setPlatform(dto.getPlatform().toUpperCase());
    entity.setPlatformName(dto.getPlatformName());
    entity.setAppId(dto.getAppId());
    // 应用密钥 BCrypt 加密存储
    if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
      entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
    }
    entity.setScope(dto.getScope());
    entity.setRedirectUri(dto.getRedirectUri());
    entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
    entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 100);
    entity.setRemark(dto.getRemark());

    mapper.insert(entity);
    log.info("社交平台客户端配置已创建: platform={}", dto.getPlatform());
  }

  @Override
  public void update(String platform, SocialClientUpdateDTO dto) {
    SocialClientDO entity = mapper.selectByPlatform(platform.toUpperCase());
    if (entity == null) {
      log.warn("尝试更新不存在的社交平台客户端配置: platform={}", platform);
      return;
    }

    if (dto.getPlatformName() != null) {
      entity.setPlatformName(dto.getPlatformName());
    }
    if (dto.getAppId() != null) {
      entity.setAppId(dto.getAppId());
    }
    // 应用密钥仅在传入时更新（BCrypt 加密）
    if (dto.getAppSecret() != null && !dto.getAppSecret().isBlank()) {
      entity.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
    }
    if (dto.getScope() != null) {
      entity.setScope(dto.getScope());
    }
    if (dto.getRedirectUri() != null) {
      entity.setRedirectUri(dto.getRedirectUri());
    }
    if (dto.getStatus() != null) {
      entity.setStatus(dto.getStatus());
    }
    if (dto.getSortOrder() != null) {
      entity.setSortOrder(dto.getSortOrder());
    }
    if (dto.getRemark() != null) {
      entity.setRemark(dto.getRemark());
    }

    mapper.updateById(entity);
    log.info("社交平台客户端配置已更新: platform={}", platform);
  }

  @Override
  public void deleteByPlatform(String platform) {
    LambdaQueryWrapper<SocialClientDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SocialClientDO::getPlatform, platform.toUpperCase())
        .eq(SocialClientDO::getDeleted, false);
    SocialClientDO entity = mapper.selectOne(wrapper);
    if (entity != null) {
      entity.setDeleted(true);
      mapper.updateById(entity);
      log.info("社交平台客户端配置已删除: platform={}", platform);
    }
  }
}
