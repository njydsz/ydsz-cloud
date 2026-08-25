package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.SamlIdpDTO;
import com.njydsz.userinfo.domain.query.SamlIdpPageQuery;
import com.njydsz.userinfo.domain.repository.SamlIdpConfigRepository;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.infra.converter.SamlIdpConfigConverter;
import com.njydsz.userinfo.infra.entity.SamlIdpConfig;
import com.njydsz.userinfo.infra.mapper.SamlIdpConfigMapper;

/**
 * SAML 身份提供者配置仓储实现（P2-1）。
 *
 * <p>通过 {@link SamlIdpConfigMapper} 访问数据库，使用注入的 {@link SamlIdpConfigConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，替代静态单例 INSTANT 访问，提升可测试性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SamlIdpConfigRepositoryImpl implements SamlIdpConfigRepository {

  private final SamlIdpConfigMapper mapper;
  private final SamlIdpConfigConverter samlIdpConfigConverter;

  @Override
  public Optional<SamlIdpConfigVO> findByEntityId(String entityId) {
    SamlIdpConfig entity = mapper.selectByEntityId(entityId);
    return entity != null
        ? Optional.of(samlIdpConfigConverter.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public List<SamlIdpConfigVO> findByPage(SamlIdpPageQuery query) {
    LambdaQueryWrapper<SamlIdpConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SamlIdpConfig::getDeleted, false)
        .like(query.getName() != null && !query.getName().isBlank(),
            SamlIdpConfig::getName, query.getName())
        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
            SamlIdpConfig::getStatus, query.getStatus())
        .orderByAsc(SamlIdpConfig::getSortOrder);

    List<SamlIdpConfig> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(samlIdpConfigConverter::entityToVo)
        .toList();
  }

  @Override
  public List<SamlIdpConfigVO> findEnabled() {
    List<SamlIdpConfig> entities = mapper.selectEnabledConfigs();
    return entities.stream()
        .map(samlIdpConfigConverter::entityToVo)
        .toList();
  }

  @Override
  public void save(SamlIdpDTO dto) {
    SamlIdpConfig existing = mapper.selectByEntityId(dto.getEntityId());
    if (existing == null) {
      createNew(dto);
    } else {
      updateExisting(existing, dto);
    }
  }

  /**
   * 创建 SAML IdP 配置：全部字段写入（含默认值兜底）。
   *
   * @param dto 待创建数据
   */
  private void createNew(SamlIdpDTO dto) {
    SamlIdpConfig entity = new SamlIdpConfig();
    entity.setName(dto.getName());
    entity.setEntityId(dto.getEntityId());
    entity.setSsoUrl(dto.getSsoUrl());
    entity.setCertificate(dto.getCertificate());
    entity.setEmailAttribute(dto.getEmailAttribute() != null ? dto.getEmailAttribute() : "email");
    entity.setDisplayNameAttribute(
        dto.getDisplayNameAttribute() != null ? dto.getDisplayNameAttribute() : "displayName");
    entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
    entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 100);
    entity.setRemark(dto.getRemark());

    mapper.insert(entity);
    log.info("SAML IdP 配置已创建: entityId={}", dto.getEntityId());
  }

  /**
   * 更新 SAML IdP 配置：仅修改非 null 字段。
   *
   * @param existing 已有实体
   * @param dto 待更新数据
   */
  private void updateExisting(SamlIdpConfig existing, SamlIdpDTO dto) {
    if (dto.getName() != null) {
      existing.setName(dto.getName());
    }
    if (dto.getSsoUrl() != null) {
      existing.setSsoUrl(dto.getSsoUrl());
    }
    if (dto.getCertificate() != null) {
      existing.setCertificate(dto.getCertificate());
    }
    if (dto.getEmailAttribute() != null) {
      existing.setEmailAttribute(dto.getEmailAttribute());
    }
    if (dto.getDisplayNameAttribute() != null) {
      existing.setDisplayNameAttribute(dto.getDisplayNameAttribute());
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
    log.info("SAML IdP 配置已更新: entityId={}", dto.getEntityId());
  }

  @Override
  public void deleteByEntityId(String entityId) {
    LambdaQueryWrapper<SamlIdpConfig> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SamlIdpConfig::getEntityId, entityId)
        .eq(SamlIdpConfig::getDeleted, false);
    SamlIdpConfig entity = mapper.selectOne(wrapper);
    if (entity != null) {
      entity.setDeleted(1);
      mapper.updateById(entity);
      log.info("SAML IdP 配置已删除: entityId={}", entityId);
    }
  }
}
