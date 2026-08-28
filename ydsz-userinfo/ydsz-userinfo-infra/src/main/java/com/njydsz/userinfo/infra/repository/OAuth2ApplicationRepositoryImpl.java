package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.oauth2.OAuth2ApplicationRepository;
import com.njydsz.userinfo.infra.converter.OAuth2ApplicationConverter;
import com.njydsz.userinfo.infra.mapper.OAuth2ApplicationMapper;

/**
 * OAuth2 应用 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 domain 层 {@link OAuth2ApplicationRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class OAuth2ApplicationRepositoryImpl implements OAuth2ApplicationRepository {

  private final OAuth2ApplicationMapper oauth2ApplicationMapper;
  private final OAuth2ApplicationConverter converter;

  @Override
  public com.njydsz.userinfo.domain.oauth2.OAuth2Application save(com.njydsz.userinfo.domain.oauth2.OAuth2Application application) {
    com.njydsz.userinfo.infra.entity.OAuth2Application entity = converter.domainToEntity(application);
    if (application.id() == null) {
      oauth2ApplicationMapper.insert(entity);
    } else {
      oauth2ApplicationMapper.updateById(entity);
    }
    return converter.entityToDomain(entity);
  }

  @Override
  public Optional<com.njydsz.userinfo.domain.oauth2.OAuth2Application> findById(String id) {
    com.njydsz.userinfo.infra.entity.OAuth2Application entity = oauth2ApplicationMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public Optional<com.njydsz.userinfo.domain.oauth2.OAuth2Application> findByClientId(String clientId) {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(com.njydsz.userinfo.infra.entity.OAuth2Application::getClientId, clientId);
    com.njydsz.userinfo.infra.entity.OAuth2Application entity = oauth2ApplicationMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public PageResponse<List<com.njydsz.userinfo.domain.oauth2.OAuth2Application>> page(
      com.njydsz.userinfo.domain.oauth2.OAuth2Application.ApplicationStatus status,
      String keyword,
      int pageNum,
      int pageSize) {
    Page<com.njydsz.userinfo.infra.entity.OAuth2Application> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    if (status != null) {
      wrapper.eq(com.njydsz.userinfo.infra.entity.OAuth2Application::getStatus, status.name());
    }
    if (keyword != null && !keyword.isBlank()) {
      wrapper.and(w -> w.like(com.njydsz.userinfo.infra.entity.OAuth2Application::getClientId, keyword)
          .or()
          .like(com.njydsz.userinfo.infra.entity.OAuth2Application::getClientName, keyword));
    }
    wrapper.orderByDesc(com.njydsz.userinfo.infra.entity.OAuth2Application::getCreatedAt);
    Page<com.njydsz.userinfo.infra.entity.OAuth2Application> result = oauth2ApplicationMapper.selectPage(page, wrapper);
    List<com.njydsz.userinfo.domain.oauth2.OAuth2Application> applications = result.getRecords().stream()
        .map(converter::entityToDomain)
        .toList();
    return PageResponse.success(
        result.getTotal(),
        (long) pageNum,
        (long) pageSize,
        applications);
  }

  @Override
  public List<com.njydsz.userinfo.domain.oauth2.OAuth2Application> findAllEnabled() {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(
        com.njydsz.userinfo.infra.entity.OAuth2Application::getStatus,
        com.njydsz.userinfo.domain.oauth2.OAuth2Application.ApplicationStatus.ENABLED
            .name());
    wrapper.orderByAsc(com.njydsz.userinfo.infra.entity.OAuth2Application::getCreatedAt);
    List<com.njydsz.userinfo.infra.entity.OAuth2Application> entities = oauth2ApplicationMapper.selectList(wrapper);
    return entities.stream()
        .map(converter::entityToDomain)
        .toList();
  }

  @Override
  public boolean deleteById(String id) {
    return oauth2ApplicationMapper.deleteById(id) > 0;
  }

  @Override
  public boolean existsByClientId(String clientId) {
    LambdaQueryWrapper<com.njydsz.userinfo.infra.entity.OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(com.njydsz.userinfo.infra.entity.OAuth2Application::getClientId, clientId);
    return oauth2ApplicationMapper.selectCount(wrapper) > 0;
  }
}
