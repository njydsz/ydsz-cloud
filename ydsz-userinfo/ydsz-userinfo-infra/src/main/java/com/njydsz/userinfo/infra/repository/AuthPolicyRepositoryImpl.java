package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.AuthPolicyDTO;
import com.njydsz.userinfo.domain.query.AuthPolicyPageQuery;
import com.njydsz.userinfo.domain.repository.AuthPolicyRepository;
import com.njydsz.userinfo.domain.vo.AuthPolicyVO;
import com.njydsz.userinfo.infra.converter.AuthPolicyConverter;
import com.njydsz.userinfo.infra.entity.AuthPolicyDO;
import com.njydsz.userinfo.infra.mapper.AuthPolicyMapper;

/**
 * 认证策略仓储实现（P3-1）。
 *
 * <p>通过 {@link AuthPolicyMapper} 访问数据库，使用注入的 {@link AuthPolicyConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，替代静态单例 INSTANT 访问，提升可测试性。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthPolicyRepositoryImpl implements AuthPolicyRepository {

  private final AuthPolicyMapper mapper;
  private final AuthPolicyConverter authPolicyConverter;

  @Override
  public Optional<AuthPolicyVO> findByTenantId(String tenantId) {
    String tid = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
    AuthPolicyDO entity = mapper.selectByTenantId(tid);
    return entity != null
        ? Optional.of(authPolicyConverter.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public List<AuthPolicyVO> findByPage(AuthPolicyPageQuery query) {
    LambdaQueryWrapper<AuthPolicyDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AuthPolicyDO::getDeleted, false)
        .like(query.getName() != null && !query.getName().isBlank(),
            AuthPolicyDO::getName, query.getName())
        .orderByAsc(AuthPolicyDO::getTenantId);

    List<AuthPolicyDO> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(authPolicyConverter::entityToVo)
        .toList();
  }

  @Override
  public void save(AuthPolicyDTO dto) {
    String tid = (dto.getTenantId() == null || dto.getTenantId().isBlank()) ? null : dto.getTenantId();
    AuthPolicyDO existing = mapper.selectByTenantId(tid);

    if (existing == null) {
      // 创建场景
      AuthPolicyDO entity = new AuthPolicyDO();
      entity.setTenantId(dto.getTenantId());
      entity.setName(dto.getName());
      entity.setPasswordMinLength(dto.getPasswordMinLength());
      entity.setPasswordRequireUppercase(dto.getPasswordRequireUppercase());
      entity.setPasswordRequireDigit(dto.getPasswordRequireDigit());
      entity.setMfaEnabled(dto.getMfaEnabled());
      entity.setCaptchaEnabled(dto.getCaptchaEnabled());
      entity.setAllowedIdentityProviders(dto.getAllowedIdentityProviders());
      entity.setMaxSessionsPerUser(dto.getMaxSessionsPerUser());
      entity.setSessionTimeoutSeconds(dto.getSessionTimeoutSeconds());
      entity.setRemark(dto.getRemark());

      mapper.insert(entity);
      log.info("认证策略已创建: tenantId={}", dto.getTenantId());
    } else {
      // 更新场景（仅修改非 null 字段）
      if (dto.getName() != null) {
        existing.setName(dto.getName());
      }
      if (dto.getPasswordMinLength() != null) {
        existing.setPasswordMinLength(dto.getPasswordMinLength());
      }
      if (dto.getPasswordRequireUppercase() != null) {
        existing.setPasswordRequireUppercase(dto.getPasswordRequireUppercase());
      }
      if (dto.getPasswordRequireDigit() != null) {
        existing.setPasswordRequireDigit(dto.getPasswordRequireDigit());
      }
      if (dto.getMfaEnabled() != null) {
        existing.setMfaEnabled(dto.getMfaEnabled());
      }
      if (dto.getCaptchaEnabled() != null) {
        existing.setCaptchaEnabled(dto.getCaptchaEnabled());
      }
      if (dto.getAllowedIdentityProviders() != null) {
        existing.setAllowedIdentityProviders(dto.getAllowedIdentityProviders());
      }
      if (dto.getMaxSessionsPerUser() != null) {
        existing.setMaxSessionsPerUser(dto.getMaxSessionsPerUser());
      }
      if (dto.getSessionTimeoutSeconds() != null) {
        existing.setSessionTimeoutSeconds(dto.getSessionTimeoutSeconds());
      }
      if (dto.getRemark() != null) {
        existing.setRemark(dto.getRemark());
      }

      mapper.updateById(existing);
      log.info("认证策略已更新: tenantId={}", dto.getTenantId());
    }
  }

  @Override
  public void deleteByTenantId(String tenantId) {
    String tid = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
    AuthPolicyDO entity = mapper.selectByTenantId(tid);
    if (entity != null) {
      entity.setDeleted(true);
      mapper.updateById(entity);
      log.info("认证策略已删除: tenantId={}", tenantId);
    }
  }
}
