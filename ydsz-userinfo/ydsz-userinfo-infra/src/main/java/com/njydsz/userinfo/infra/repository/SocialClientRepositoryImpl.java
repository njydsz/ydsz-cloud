package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.SocialClientDTO;
import com.njydsz.userinfo.domain.query.SocialClientPageQuery;
import com.njydsz.userinfo.domain.repository.SocialClientRepository;
import com.njydsz.userinfo.domain.vo.SocialClientVO;
import com.njydsz.userinfo.infra.converter.SocialClientConverter;
import com.njydsz.userinfo.infra.entity.SocialClient;
import com.njydsz.userinfo.infra.mapper.SocialClientMapper;

/**
 * 社交平台客户端配置仓储实现（P1-1）。
 *
 * <p>通过 {@link SocialClientMapper} 访问数据库，使用注入的 {@link SocialClientConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，替代静态单例 INSTANT 访问，提升可测试性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SocialClientRepositoryImpl implements SocialClientRepository {

  private final SocialClientMapper mapper;
  private final SocialClientConverter socialClientConverter;
  private final PasswordEncoder passwordEncoder;

  @Override
  public List<SocialClientVO> findByPage(SocialClientPageQuery query) {
    LambdaQueryWrapper<SocialClient> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SocialClient::getDeleted, false)
        .like(query.getPlatform() != null && !query.getPlatform().isBlank(),
            SocialClient::getPlatform, query.getPlatform())
        .like(query.getPlatformName() != null && !query.getPlatformName().isBlank(),
            SocialClient::getPlatformName, query.getPlatformName())
        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
            SocialClient::getStatus, query.getStatus())
        .orderByAsc(SocialClient::getSortOrder);

    List<SocialClient> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(socialClientConverter::entityToVo)
        .toList();
  }

  @Override
  public List<SocialClientVO> findEnabled() {
    List<SocialClient> entities = mapper.selectEnabledClients();
    return entities.stream()
        .map(socialClientConverter::entityToVo)
        .toList();
  }

  @Override
  public Optional<SocialClientVO> findByPlatform(String platform) {
    SocialClient entity = mapper.selectByPlatform(platform.toUpperCase());
    return entity != null
        ? Optional.of(socialClientConverter.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public void save(SocialClientDTO dto) {
    SocialClient existing = mapper.selectByPlatform(dto.getPlatform().toUpperCase());
    if (existing != null) {
      updateExisting(existing, dto);
    } else {
      createNew(dto);
    }
  }

  /**
   * 更新已有配置：仅在字段非空时更新。
   *
   * @param existing 已有实体
   * @param dto 待更新数据
   */
  private void updateExisting(SocialClient existing, SocialClientDTO dto) {
    if (dto.getPlatformName() != null) {
      existing.setPlatformName(dto.getPlatformName());
    }
    if (dto.getAppId() != null) {
      existing.setAppId(dto.getAppId());
    }
    if (hasText(dto.getAppSecret())) {
      existing.setAppSecret(passwordEncoder.encode(dto.getAppSecret()));
    }
    if (dto.getScope() != null) {
      existing.setScope(dto.getScope());
    }
    if (dto.getRedirectUri() != null) {
      existing.setRedirectUri(dto.getRedirectUri());
    }
    if (dto.getStatus() != null) {
      existing.setStatus(dto.getStatus());
    }
    if (dto.getSortOrder() != null) {
      existing.setSortOrder(dto.getSortOrder());
    }
    if (dto.getRemark() != null) {
      existing.setRemark(dto.getRemark());
    }
    mapper.updateById(existing);
    log.info("社交平台客户端配置已更新: platform={}", dto.getPlatform());
  }

  /**
   * 创建新配置：全部字段写入。
   *
   * @param dto 待创建数据
   */
  private void createNew(SocialClientDTO dto) {
    SocialClient entity = new SocialClient();
    entity.setPlatform(dto.getPlatform().toUpperCase());
    entity.setPlatformName(dto.getPlatformName());
    entity.setAppId(dto.getAppId());
    if (hasText(dto.getAppSecret())) {
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

  /**
   * 判断字符串是否非空且非空白。
   *
   * @param value 待判断字符串
   * @return true 表示非空且非空白
   */
  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Override
  public void deleteByPlatform(String platform) {
    LambdaQueryWrapper<SocialClient> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SocialClient::getPlatform, platform.toUpperCase())
        .eq(SocialClient::getDeleted, false);
    SocialClient entity = mapper.selectOne(wrapper);
    if (entity != null) {
      entity.setDeleted(1);
      mapper.updateById(entity);
      log.info("社交平台客户端配置已删除: platform={}", platform);
    }
  }
}
