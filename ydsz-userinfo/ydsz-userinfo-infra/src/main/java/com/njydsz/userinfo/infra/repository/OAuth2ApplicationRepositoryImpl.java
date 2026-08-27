package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application;
import com.njydsz.userinfo.domain.oauth2.OAuth2Application.ApplicationStatus;
import com.njydsz.userinfo.domain.oauth2.OAuth2ApplicationRepository;
import com.njydsz.userinfo.infra.converter.OAuth2ApplicationConverter;
import com.njydsz.userinfo.infra.entity.OAuth2Application;
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
  public OAuth2Application save(OAuth2Application application) {
    OAuth2Application entity = converter.domainToEntity(application);
    if (application.id() == null) {
      oauth2ApplicationMapper.insert(entity);
    } else {
      oauth2ApplicationMapper.updateById(entity);
    }
    return converter.entityToDomain(entity);
  }

  @Override
  public Optional<OAuth2Application> findById(String id) {
    OAuth2Application entity = oauth2ApplicationMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public Optional<OAuth2Application> findByClientId(String clientId) {
    LambdaQueryWrapper<OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(OAuth2Application::getClientId, clientId);
    OAuth2Application entity = oauth2ApplicationMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToDomain);
  }

  @Override
  public PageResponse<List<OAuth2Application>> page(
      ApplicationStatus status,
      String keyword,
      int pageNum,
      int pageSize) {
    Page<OAuth2Application> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    if (status != null) {
      wrapper.eq(OAuth2Application::getStatus, status.name());
    }
    if (keyword != null && !keyword.isBlank()) {
      wrapper.and(w -> w.like(OAuth2Application::getClientId, keyword)
          .or()
          .like(OAuth2Application::getClientName, keyword));
    }
    wrapper.orderByDesc(OAuth2Application::getCreatedAt);
    Page<OAuth2Application> result = oauth2ApplicationMapper.selectPage(page, wrapper);
    List<OAuth2Application> applications = result.getRecords().stream()
        .map(converter::entityToDomain)
        .toList();
    return PageResponse.success(
        result.getTotal(),
        (long) pageNum,
        (long) pageSize,
        applications);
  }

  @Override
  public List<OAuth2Application> findAllEnabled() {
    LambdaQueryWrapper<OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(OAuth2Application::getStatus, ApplicationStatus.ENABLED.name());
    wrapper.orderByAsc(OAuth2Application::getCreatedAt);
    List<OAuth2Application> entities = oauth2ApplicationMapper.selectList(wrapper);
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
    LambdaQueryWrapper<OAuth2Application> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(OAuth2Application::getClientId, clientId);
    return oauth2ApplicationMapper.selectCount(wrapper) > 0;
  }
}
