package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.SamlIdpCreateDTO;
import com.njydsz.userinfo.domain.dto.SamlIdpUpdateDTO;
import com.njydsz.userinfo.domain.query.SamlIdpPageQuery;
import com.njydsz.userinfo.domain.repository.SamlIdpConfigRepository;
import com.njydsz.userinfo.domain.vo.SamlIdpConfigVO;
import com.njydsz.userinfo.infra.converter.SamlIdpConfigConverter;
import com.njydsz.userinfo.infra.entity.SamlIdpConfigDO;
import com.njydsz.userinfo.infra.mapper.SamlIdpConfigMapper;

/**
 * SAML 身份提供者配置仓储实现（P2-1）。
 *
 * <p>通过 {@link SamlIdpConfigMapper} 访问数据库，使用注入的 {@link SamlIdpConfigConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，替代静态单例 INSTANT 访问，提升可测试性。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SamlIdpConfigRepositoryImpl implements SamlIdpConfigRepository {

  private final SamlIdpConfigMapper mapper;
  private final SamlIdpConfigConverter samlIdpConfigConverter;

  @Override
  public Optional<SamlIdpConfigVO> findByEntityId(String entityId) {
    SamlIdpConfigDO entity = mapper.selectByEntityId(entityId);
    return entity != null
        ? Optional.of(samlIdpConfigConverter.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public List<SamlIdpConfigVO> findByPage(SamlIdpPageQuery query) {
    LambdaQueryWrapper<SamlIdpConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SamlIdpConfigDO::getDeleted, false)
        .like(query.getName() != null && !query.getName().isBlank(),
            SamlIdpConfigDO::getName, query.getName())
        .eq(query.getStatus() != null && !query.getStatus().isBlank(),
            SamlIdpConfigDO::getStatus, query.getStatus())
        .orderByAsc(SamlIdpConfigDO::getSortOrder);

    List<SamlIdpConfigDO> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(samlIdpConfigConverter::entityToVo)
        .toList();
  }

  @Override
  public List<SamlIdpConfigVO> findEnabled() {
    List<SamlIdpConfigDO> entities = mapper.selectEnabledConfigs();
    return entities.stream()
        .map(samlIdpConfigConverter::entityToVo)
        .toList();
  }

  @Override
  public void save(SamlIdpCreateDTO dto) {
    SamlIdpConfigDO entity = new SamlIdpConfigDO();
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

  @Override
  public void update(String entityId, SamlIdpUpdateDTO dto) {
    SamlIdpConfigDO entity = mapper.selectByEntityId(entityId);
    if (entity == null) {
      log.warn("尝试更新不存在的 SAML IdP 配置: entityId={}", entityId);
      return;
    }

    if (dto.getName() != null) {
      entity.setName(dto.getName());
    }
    if (dto.getSsoUrl() != null) {
      entity.setSsoUrl(dto.getSsoUrl());
    }
    if (dto.getCertificate() != null) {
      entity.setCertificate(dto.getCertificate());
    }
    if (dto.getEmailAttribute() != null) {
      entity.setEmailAttribute(dto.getEmailAttribute());
    }
    if (dto.getDisplayNameAttribute() != null) {
      entity.setDisplayNameAttribute(dto.getDisplayNameAttribute());
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
    log.info("SAML IdP 配置已更新: entityId={}", entityId);
  }

  @Override
  public void deleteByEntityId(String entityId) {
    LambdaQueryWrapper<SamlIdpConfigDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(SamlIdpConfigDO::getEntityId, entityId)
        .eq(SamlIdpConfigDO::getDeleted, false);
    SamlIdpConfigDO entity = mapper.selectOne(wrapper);
    if (entity != null) {
      entity.setDeleted(true);
      mapper.updateById(entity);
      log.info("SAML IdP 配置已删除: entityId={}", entityId);
    }
  }
}
