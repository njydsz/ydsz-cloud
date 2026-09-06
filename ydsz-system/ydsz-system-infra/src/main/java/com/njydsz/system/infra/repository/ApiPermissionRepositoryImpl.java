package com.njydsz.system.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.converter.SystemConverter;
import com.njydsz.system.domain.dto.ApiPermissionDTO;
import com.njydsz.system.domain.entity.ApiPermission;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.repository.ApiPermissionRepository;
import com.njydsz.system.domain.vo.ApiPermissionVO;
import com.njydsz.system.infra.mapper.ApiPermissionMapper;



/**
 * 接口权限仓储实现（Infra 层）。
 *
 * <p>实现 {@link ApiPermissionRepository} 接口，封装 {@link ApiPermissionMapper} 数据访问细节。
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
public class ApiPermissionRepositoryImpl implements ApiPermissionRepository {

  /** 状态常量：启用 */
  private static final String STATUS_ENABLED = "ENABLED";

  /** 逻辑删除标志：未删除 */
  private static final int NOT_DELETED = 0;

  private final ApiPermissionMapper apiPermissionMapper;

  private final SystemConverter converter;

  @Override
  public PageResponse<List<ApiPermissionVO>> findByPage(ApiPermissionQuery query) {
    Page<ApiPermission> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<ApiPermission> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ApiPermission::getDeleted, NOT_DELETED);
    if (query.getApiCode() != null && !query.getApiCode().isBlank()) {
      wrapper.like(ApiPermission::getApiCode, query.getApiCode());
    }
    if (query.getApiName() != null && !query.getApiName().isBlank()) {
      wrapper.like(ApiPermission::getApiName, query.getApiName());
    }
    if (query.getControllerClass() != null && !query.getControllerClass().isBlank()) {
      wrapper.like(ApiPermission::getControllerClass, query.getControllerClass());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(ApiPermission::getStatus, query.getStatus());
    }
    wrapper.orderByDesc(ApiPermission::getCreatedAt);
    var result = apiPermissionMapper.selectPage(page, wrapper);
    List<ApiPermissionVO> vos = converter.apiPermissionListToVO(result.getRecords());
    return PageResponse.success(result.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public Optional<ApiPermissionVO> findById(String id) {
    return Optional.ofNullable(apiPermissionMapper.selectById(id)).map(converter::apiPermissionToVO);
  }

  @Override
  public Optional<ApiPermissionVO> findByTenantAndApiCode(String tenantId, String apiCode) {
    return Optional.ofNullable(
            apiPermissionMapper.selectOne(
                new LambdaQueryWrapper<ApiPermission>()
                    .eq(ApiPermission::getTenantId, tenantId)
                    .eq(ApiPermission::getApiCode, apiCode)
                    .eq(ApiPermission::getDeleted, NOT_DELETED)
                    .last("LIMIT 1")))
        .map(converter::apiPermissionToVO);
  }

  @Override
  public List<ApiPermissionVO> listAllByTenant(String tenantId) {
    return converter.apiPermissionListToVO(apiPermissionMapper.selectList(
        new LambdaQueryWrapper<ApiPermission>()
            .eq(ApiPermission::getTenantId, tenantId)
            .eq(ApiPermission::getDeleted, NOT_DELETED)
            .orderByDesc(ApiPermission::getCreatedAt)));
  }

  @Override
  public boolean insert(ApiPermissionDTO dto) {
    ApiPermission entity = converter.apiPermissionDtoToEntity(dto);
    boolean success = apiPermissionMapper.insert(entity) > 0;
    if (success && entity.getId() != null) {
      dto.setId(entity.getId());
    }
    return success;
  }

  @Override
  public boolean updateById(ApiPermissionDTO dto) {
    ApiPermission entity = converter.apiPermissionDtoToEntityWithId(dto);
    return apiPermissionMapper.updateById(entity) > 0;
  }

  @Override
  public boolean removeById(String id) {
    return apiPermissionMapper.deleteById(id) > 0;
  }

  @Override
  public int insertBatchSkipExisting(List<ApiPermissionDTO> dtos) {
    List<ApiPermission> entities = converter.apiPermissionDtosToEntities(dtos);
    return apiPermissionMapper.insertBatchSkipExisting(entities);
  }
}
