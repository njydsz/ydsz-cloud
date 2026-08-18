package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.system.infra.converter.SystemConverter;
import com.njydsz.system.infra.entity.AppInfo;
import com.njydsz.system.infra.mapper.AppInfoMapper;
import com.njydsz.system.domain.repository.AppInfoRepository;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.query.AppInfoPageQuery;
import com.njydsz.system.domain.vo.AppInfoVO;

/**
 * 应用信息仓储实现（Infra 层）。
 *
 * <p>实现 {@link AppInfoRepository} 接口，封装 {@link AppInfoMapper} 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link SystemConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link SystemConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class AppInfoRepositoryImpl implements AppInfoRepository {

  private final AppInfoMapper appInfoMapper;

  private final SystemConverter converter;

  @Override
  public Optional<AppInfoVO> findEnabledByAppKey(String appKey) {
    return Optional.ofNullable(appInfoMapper.selectEnabledByAppKey(appKey))
        .map(converter::entityToVO);
  }

  @Override
  public boolean existsByAppKey(String appKey) {
    Long count = appInfoMapper.selectCount(new QueryWrapper<AppInfo>().eq("app_key", appKey));
    return count != null && count > 0;
  }

  @Override
  public Optional<AppInfoVO> findById(String id) {
    return Optional.ofNullable(appInfoMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public IPage<AppInfoVO> findByPage(AppInfoPageQuery query) {
    Page<AppInfo> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<AppInfo> wrapper = new QueryWrapper<>();
    if (query.getAppName() != null && !query.getAppName().isBlank()) {
      wrapper.like("app_name", query.getAppName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.orderByDesc("created_at");
    IPage<AppInfo> entityPage = appInfoMapper.selectPage(page, wrapper);
    // DO → VO 转换
    List<AppInfoVO> vos = converter.appInfoListToVO(entityPage.getRecords());
    Page<AppInfoVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public List<AppInfoVO> findAll() {
    return converter.appInfoListToVO(appInfoMapper.selectList(null));
  }

  @Override
  public boolean insert(AppInfoDTO dto) {
    AppInfo entity = converter.dtoToEntity(dto);
    return appInfoMapper.insert(entity) > 0;
  }

  @Override
  public boolean updateById(AppInfoDTO dto) {
    AppInfo entity = converter.dtoToEntityWithId(dto);
    return appInfoMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return appInfoMapper.deleteById(id) > 0;
  }
}
