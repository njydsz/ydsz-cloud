package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.entity.ApiKey;
import com.njydsz.userinfo.domain.query.ApiKeyPageQuery;
import com.njydsz.userinfo.domain.repository.ApiKeyRepository;
import com.njydsz.userinfo.domain.vo.ApiKeyVO;
import com.njydsz.userinfo.infra.mapper.ApiKeyMapper;

/**
 * API Key Repository 实现。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Repository
@RequiredArgsConstructor
public class ApiKeyRepositoryImpl implements ApiKeyRepository {

  private final ApiKeyMapper apiKeyMapper;

  @Override
  public Optional<ApiKey> findByKeyHash(String apiKeyHash) {
    LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ApiKey::getApiKeyHash, apiKeyHash);
    wrapper.eq(ApiKey::getDeleted, false);
    return Optional.ofNullable(apiKeyMapper.selectOne(wrapper));
  }

  @Override
  public Optional<ApiKeyVO> findById(Long id) {
    ApiKey entity = apiKeyMapper.selectById(id);
    if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
      return Optional.empty();
    }
    return Optional.of(entityToVO(entity));
  }

  @Override
  public PageResponse<List<ApiKeyVO>> page(ApiKeyPageQuery query) {
    Page<ApiKey> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<ApiKey> wrapper = buildWrapper(query);
    Page<ApiKey> result = apiKeyMapper.selectPage(page, wrapper);
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        result.getRecords().stream().map(this::entityToVO).toList());
  }

  @Override
  public List<ApiKeyVO> listByUserId(String userId) {
    LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ApiKey::getUserId, userId);
    wrapper.eq(ApiKey::getDeleted, false);
    wrapper.orderByDesc(ApiKey::getCreatedAt);
    return apiKeyMapper.selectList(wrapper).stream().map(this::entityToVO).toList();
  }

  @Override
  public ApiKey save(ApiKey apiKey) {
    apiKeyMapper.insert(apiKey);
    return apiKey;
  }

  @Override
  public int revokeByIds(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return apiKeyMapper.revokeByIds(ids);
  }

  @Override
  public int updateEnabled(Long id, boolean enabled) {
    ApiKey entity = new ApiKey();
    entity.setId(id);
    entity.setEnabled(enabled);
    return apiKeyMapper.updateById(entity);
  }

  @Override
  public int updateLastUsedAt(Long id, LocalDateTime lastUsedAt) {
    return apiKeyMapper.updateLastUsedAt(id, lastUsedAt);
  }

  @Override
  public long countExpired(LocalDateTime now) {
    LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
    wrapper.isNotNull(ApiKey::getExpireAt);
    wrapper.lt(ApiKey::getExpireAt, now);
    wrapper.eq(ApiKey::getDeleted, false);
    return apiKeyMapper.selectCount(wrapper);
  }

  @Override
  public int deleteExpired(LocalDateTime now) {
    return apiKeyMapper.deleteExpired(now);
  }

  private LambdaQueryWrapper<ApiKey> buildWrapper(ApiKeyPageQuery query) {
    LambdaQueryWrapper<ApiKey> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ApiKey::getDeleted, false);
    if (query.getKeyName() != null && !query.getKeyName().isBlank()) {
      wrapper.like(ApiKey::getKeyName, query.getKeyName());
    }
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq(ApiKey::getUserId, query.getUserId());
    }
    if (query.getEnabled() != null) {
      wrapper.eq(ApiKey::getEnabled, query.getEnabled());
    }
    wrapper.orderByDesc(ApiKey::getCreatedAt);
    return wrapper;
  }

  private ApiKeyVO entityToVO(ApiKey entity) {
    ApiKeyVO vo = new ApiKeyVO();
    vo.setId(entity.getId());
    vo.setApiKeyPrefix(entity.getApiKeyPrefix());
    vo.setKeyName(entity.getKeyName());
    vo.setScopes(entity.getScopes());
    vo.setExpireAt(entity.getExpireAt());
    vo.setLastUsedAt(entity.getLastUsedAt());
    vo.setRateLimit(entity.getRateLimit());
    vo.setEnabled(entity.getEnabled());
    vo.setCreatedAt(entity.getCreatedAt());
    // apiKey 字段返回 null（仅创建时返回明文）
    return vo;
  }
}
