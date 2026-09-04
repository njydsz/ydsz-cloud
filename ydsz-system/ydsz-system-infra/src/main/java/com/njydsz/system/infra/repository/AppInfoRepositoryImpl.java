package com.njydsz.system.infra.repository;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.query.AppInfoPageQuery;
import com.njydsz.system.domain.repository.AppInfoRepository;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.entity.AppInfo;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.infra.mapper.AppInfoMapper;




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
 * @since 26.09.01
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
    Long count = appInfoMapper.selectCount(
        new LambdaQueryWrapper<AppInfo>().eq(AppInfo::getAppKey, appKey).eq(AppInfo::getDeleted, 0));
    return count != null && count > 0;
  }

  @Override
  public Optional<AppInfoVO> findById(String id) {
    return Optional.ofNullable(appInfoMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<AppInfoVO>> findByPage(AppInfoPageQuery query) {
    Page<AppInfo> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<AppInfo> wrapper = new LambdaQueryWrapper<>();
    if (query.getAppName() != null && !query.getAppName().isBlank()) {
      wrapper.like(AppInfo::getAppName, query.getAppName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(AppInfo::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(AppInfo::getCreatedAt);
    IPage<AppInfo> result = appInfoMapper.selectPage(page, wrapper);
    List<AppInfoVO> vos = converter.appInfoListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public List<AppInfoVO> findAll() {
    return converter.appInfoListToVO(appInfoMapper.selectList(null));
  }

  @Override
  public boolean insert(AppInfoDTO dto) {
    AppInfo entity = converter.dtoToEntity(dto);
    boolean success = appInfoMapper.insert(entity) > 0;
    // MyBatis-Plus 回填 snowflake ID 到 entity，需同步回 DTO
    if (success && entity.getId() != null) {
      dto.setId(entity.getId());
    }
    return success;
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
