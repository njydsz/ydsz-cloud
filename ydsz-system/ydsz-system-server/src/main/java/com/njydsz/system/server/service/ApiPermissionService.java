package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.ApiPermissionDTO;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.vo.ApiPermissionVO;


/**
 * 接口权限 Service 接口
 *
 * <p>提供接口权限（{@code ydsz_sys_api_permission}）的分页查询、启用/禁用、删除和启动扫描注册能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #enable} / {@link #disable} / {@link
 *       #removeById}
 *   <li><b>注册扫描</b>：{@link #scanAndRegister()} — 启动时扫描 {@code @AuthApiPermission} 注解注册
 *   <li><b>批量注册</b>：{@link #batchRegister} — 批量插入跳过已有权限码
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ApiPermissionService {

  /**
   * 扫描并注册接口权限（由启动运行器调用）。
   *
   * @return 新注册的接口数量
   */
  int scanAndRegister();

  /**
   * 分页查询接口权限。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<ApiPermissionVO>> page(ApiPermissionQuery query);

  /**
   * 按 ID 查询接口权限。
   *
   * @param id 主键 ID
   * @return 接口权限 VO；不存在返回 {@code null}
   */
  ApiPermissionVO getById(String id);

  /**
   * 启用接口权限。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean enable(String id);

  /**
   * 禁用接口权限。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean disable(String id);

  /**
   * 逻辑删除接口权限。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);

  /**
   * 列出全部接口权限。
   *
   * @return 接口权限 VO 列表
   */
  List<ApiPermissionVO> listAll();

  /**
   * 批量注册接口权限（跳过已有权限码）。
   *
   * @param dtos 接口权限 DTO 列表
   * @return 实际插入的记录数
   */
  int batchRegister(List<ApiPermissionDTO> dtos);
}
