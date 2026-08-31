package com.njydsz.system.server.service.impl;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.system.domain.dto.TenantDTO;
import com.njydsz.system.domain.enums.SystemExceptionCode;
import com.njydsz.system.domain.query.TenantPageQuery;
import com.njydsz.system.domain.repository.TenantRepository;
import com.njydsz.system.domain.vo.TenantVO;
import com.njydsz.system.server.service.TenantService;




/**
 * 租户 Service 实现
 *
 * <p>对 {@link TenantService} 接口的完整实现，是「多租户管理中心」的核心业务逻辑层。 提供租户的 CRUD、分页查询、唯一性校验等能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「租户管理」列表
 *   <li><b>唯一性校验</b>：{@link #existsByTenantCode} — 创建前检查租户编码唯一性
 * </ul>
 *
 * <p><b>多租户：</b>租户管理属于系统级超级管理员权限， 查询时不注入租户过滤条件，可跨租户查看全部租户。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see TenantService 租户 Service 接口
 * @see com.njydsz.system.infra.entity.Tenant 租户实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

  /** 租户仓储 */
  private final TenantRepository tenantRepository;

  /**
   * 按 ID 查询租户
   *
   * @param id 主键 ID
   * @return 租户 VO；不存在返回 {@code null}
   */
  @Override
  public TenantVO getById(String id) {
    return tenantRepository.findById(id).orElse(null);
  }

  /**
   * 分页查询租户列表
   *
   * <p>支持按租户名称模糊匹配、状态过滤。
   *
   * @param query 分页查询条件（pageNum / pageSize / tenantName / status）
   * @return 分页结果
   */
  @Override
  public PageResponse<List<TenantVO>> page(TenantPageQuery query) {
    return tenantRepository.findByPage(query);
  }

  /**
   * 创建租户
   *
   * <p>写入前校验 {@code tenantCode} 全局唯一性。
   *
   * @param dto 租户 DTO
   * @return 新建租户主键 ID
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(TenantDTO dto) {
    if (existsByTenantCode(dto.getTenantCode())) {
      throw BusinessException.of(SystemExceptionCode.TENANT_CODE_DUPLICATE)
          .data("tenantCode", dto.getTenantCode());
    }
    tenantRepository.insert(dto);
    log.info("创建租户成功: tenantCode={}", dto.getTenantCode());
    return dto.getId();
  }

  /**
   * 更新租户
   *
   * <p>更新时校验 {@code tenantCode} 唯一性（排除自身）。
   *
   * @param dto 租户 DTO（{@code id} 必填）
   * @return 是否成功
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(TenantDTO dto) {
    TenantVO existing = tenantRepository.findById(dto.getId()).orElse(null);
    if (existing != null) {
      // 租户编码未变更时跳过唯一性校验（排除自身）
      if (!dto.getTenantCode().equals(existing.getTenantCode()) && existsByTenantCode(dto.getTenantCode())) {
        throw BusinessException.of(SystemExceptionCode.TENANT_CODE_DUPLICATE)
            .data("tenantCode", dto.getTenantCode());
      }
    }
    return tenantRepository.updateById(dto);
  }

  /**
   * 删除租户（逻辑删除）
   *
   * <p>基于 MyBatis-Plus 逻辑删除（{@code @TableLogic}），不物理删除。
   *
   * <p><b>删除保护（P1-3）：</b>
   *
   * <ul>
   *   <li>内置平台租户（{@code DEFAULT}/{@code MASTER}）禁止删除，避免平台配置失锚
   *   <li>仍处于 {@code ENABLED} 状态的租户禁止删除——必须先停用租户并清理其业务数据（用户/组织等由
   *       上层模块承载，应在编排层完成数据清理），防止产生孤儿数据
   * </ul>
   *
   * @param id 主键 ID
   * @return 是否成功
   * @throws BusinessException 内置租户或活跃租户禁止删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    TenantVO tenant = tenantRepository.findById(id).orElse(null);
    if (tenant == null) {
      return false;
    }
    // 内置平台租户禁止删除
    if (isBuiltinTenant(tenant.getTenantCode())) {
      throw BusinessException.of(SystemExceptionCode.TENANT_BUILTIN)
          .data("tenantCode", tenant.getTenantCode());
    }
    // 活跃租户禁止直接删除（先停用 + 清理业务数据，防止孤儿数据）
    if ("ENABLED".equals(tenant.getStatus())) {
      throw BusinessException.of(SystemExceptionCode.TENANT_LINKED)
          .data("tenantCode", tenant.getTenantCode())
          .data("reason", "租户仍处于启用状态，请先停用租户并清理其业务数据后再删除");
    }
    return tenantRepository.deleteById(id);
  }

  /**
   * 判断是否为内置平台租户（私有）。
   *
   * @param tenantCode 租户编码
   * @return 内置租户返回 {@code true}
   */
  private boolean isBuiltinTenant(String tenantCode) {
    return "DEFAULT".equalsIgnoreCase(tenantCode) || "MASTER".equalsIgnoreCase(tenantCode);
  }

  /**
   * 检查租户编码是否已存在
   *
   * @param tenantCode 租户编码
   * @return 已存在返回 {@code true}，否则返回 {@code false}
   */
  @Override
  public boolean existsByTenantCode(String tenantCode) {
    TenantPageQuery query = new TenantPageQuery();
    query.setTenantCode(tenantCode);
    return tenantRepository.countByCondition(query) > 0;
  }
}
