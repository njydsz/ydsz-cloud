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
import com.njydsz.userinfo.infra.entity.AuthPolicy;
import com.njydsz.userinfo.infra.mapper.AuthPolicyMapper;

/**
 * 认证策略仓储实现（P3-1）。
 *
 * <p>通过 {@link AuthPolicyMapper} 访问数据库，使用注入的 {@link AuthPolicyConverter} 完成 DO ↔ VO 转换。
 * P1-2: 升级为 Spring 注入模式，替代静态单例 INSTANT 访问，提升可测试性。
 *
 * @author ydsz-team
 * @since 26.09.01
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
    AuthPolicy entity = mapper.selectByTenantId(tid);
    return entity != null
        ? Optional.of(authPolicyConverter.entityToVo(entity))
        : Optional.empty();
  }

  @Override
  public List<AuthPolicyVO> findByPage(AuthPolicyPageQuery query) {
    LambdaQueryWrapper<AuthPolicy> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(AuthPolicy::getDeleted, false)
        .like(query.getName() != null && !query.getName().isBlank(),
            AuthPolicy::getName, query.getName())
        .orderByAsc(AuthPolicy::getTenantId);

    List<AuthPolicy> entities = mapper.selectList(wrapper);
    return entities.stream()
        .map(authPolicyConverter::entityToVo)
        .toList();
  }

  @Override
  public void save(AuthPolicyDTO dto) {
    String tid = (dto.getTenantId() == null || dto.getTenantId().isBlank()) ? null : dto.getTenantId();
    AuthPolicy existing = mapper.selectByTenantId(tid);
    if (existing == null) {
      createNew(dto);
    } else {
      updateExisting(existing, dto);
    }
  }

  /**
   * 创建认证策略：全部字段写入。
   *
   * @param dto 待创建数据
   */
  private void createNew(AuthPolicyDTO dto) {
    AuthPolicy entity = new AuthPolicy();
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
  }

  /**
   * 更新认证策略：仅修改非 null 字段。
   *
   * @param existing 已有实体
   * @param dto 待更新数据
   */
  private void updateExisting(AuthPolicy existing, AuthPolicyDTO dto) {
    applyBasicFields(existing, dto);
    applySecurityFields(existing, dto);
    mapper.updateById(existing);
    log.info("认证策略已更新: tenantId={}", dto.getTenantId());
  }

  /**
   * 更新基础字段（名称/备注）。
   *
   * @param existing 已有实体
   * @param dto 待更新数据
   */
  private void applyBasicFields(AuthPolicy existing, AuthPolicyDTO dto) {
    if (dto.getName() != null) {
      existing.setName(dto.getName());
    }
    if (dto.getRemark() != null) {
      existing.setRemark(dto.getRemark());
    }
  }

  /**
   * 更新安全策略字段（密码策略/MFA/验证码/会话等）。
   *
   * @param existing 已有实体
   * @param dto 待更新数据
   */
  private void applySecurityFields(AuthPolicy existing, AuthPolicyDTO dto) {
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
  }

  @Override
  public void deleteByTenantId(String tenantId) {
    String tid = (tenantId == null || tenantId.isBlank()) ? null : tenantId;
    AuthPolicy entity = mapper.selectByTenantId(tid);
    if (entity != null) {
      entity.setDeleted(1);
      mapper.updateById(entity);
      log.info("认证策略已删除: tenantId={}", tenantId);
    }
  }
}
