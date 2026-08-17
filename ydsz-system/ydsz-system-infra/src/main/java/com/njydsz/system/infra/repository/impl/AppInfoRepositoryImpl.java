package com.njydsz.system.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.infra.repository.AppInfoRepository;

/**
 * 应用信息仓储实现（Infra 层）。
 *
 * <p>实现 {@link AppInfoRepository} 接口，封装 {@link AppInfoMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>返回领域实体，由 Service 层负责转换为 VO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AppInfoRepositoryImpl implements AppInfoRepository {

  private final AppInfoMapper appInfoMapper;

  @Override
  public Optional<AppInfo> findEnabledByAppKey(String appKey) {
    return Optional.ofNullable(appInfoMapper.selectEnabledByAppKey(appKey));
  }

  @Override
  public boolean existsByAppKey(String appKey) {
    Long count = appInfoMapper.selectCount(new QueryWrapper<AppInfo>().eq("app_key", appKey));
    return count != null && count > 0;
  }

  @Override
  public Optional<AppInfo> findById(String id) {
    return Optional.ofNullable(appInfoMapper.selectById(id));
  }

  @Override
  public IPage<AppInfo> findByPage(Page<AppInfo> page, String appName, String status) {
    QueryWrapper<AppInfo> wrapper = new QueryWrapper<>();
    if (appName != null && !appName.isBlank()) {
      wrapper.like("app_name", appName);
    }
    if (status != null && !status.isBlank()) {
      wrapper.eq("status", status);
    }
    wrapper.orderByDesc("created_at");
    return appInfoMapper.selectPage(page, wrapper);
  }

  @Override
  public List<AppInfo> findAll() {
    return appInfoMapper.selectList(null);
  }

  @Override
  public boolean insert(AppInfo entity) {
    return appInfoMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(AppInfo entity) {
    return appInfoMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return appInfoMapper.deleteById(id) > 0;
  }
}
