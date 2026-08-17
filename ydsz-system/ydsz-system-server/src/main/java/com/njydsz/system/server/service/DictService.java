package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.domain.query.DictPageQuery;

/**
 * 字典类型 Service 接口
 *
 * <p>提供字典类型（{@code ydsz_dict_type}）的 CRUD 与全量查询能力。 与 {@link DictItemService} 协同：先创建「类型」，再为其挂载「字典项」。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>删除类型前需先清空其下字典项（{@code removeById} 不级联）
 *   <li>{@link #listAll()} 走本地 Caffeine 缓存（5min TTL），避免下拉框渲染触发 DB
 *   <li>所有写操作通过 {@code @CacheEvict} 主动失效 Redis 字典缓存
 *   <li>租户隔离由 MyBatis 拦截器自动注入，本类方法签名无需 {@code tenantId}
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see DictItemService 字典项 Service
 * @see com.njydsz.system.domain.entity.DictType 字典类型实体
 */
public interface DictService {

  /**
   * 分页查询字典类型
   *
   * <p>支持按 {@code typeCode} 精确匹配 / {@code typeName} 模糊匹配 / {@code status} 过滤， 租户过滤由 MyBatis
   * 拦截器自动注入。
   *
   * @param query 分页查询参数
   * @return 分页结果（{@link com.njydsz.common.core.response.PageResponse}）
   */
  PageResponse<List<DictTypeVO>> page(DictPageQuery query);

  /**
   * 按 ID 查询字典类型
   *
   * @param id 主键 ID
   * @return 字典类型 VO；不存在时返回 {@code null}（调用方需判空）
   */
  DictTypeVO getById(String id);

  /**
   * 创建字典类型
   *
   * <p>写入前校验 {@code (tenantId, typeCode)} 唯一性；冲突时抛 {@code
   * com.njydsz.common.exception.BizException}。
   *
   * @param dto 字典类型 DTO
   * @return 新建字典类型主键 ID
   * @throws com.njydsz.common.exception.BizException 当 {@code typeCode} 已存在时抛出
   */
  String save(DictTypeVO vo);

  /**
   * 更新字典类型
   *
   * <p>仅更新非空字段；{@code typeCode} 一般不允许变更（与字典项强绑定）。
   *
   * @param dto 字典类型 DTO（{@code id} 必填）
   * @return 是否成功
   */
  boolean updateById(DictTypeVO vo);

  /**
   * 删除字典类型
   *
   * <p>删除前<b>不级联</b>删除其下字典项；调用方需先通过 {@code DictItemService} 提供的批量删除能力清空字典项。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);

  /**
   * 查询全部字典类型（不区分状态）
   *
   * <p>用于「字典类型选择器」下拉框数据源；走本地 Caffeine 缓存（5min TTL）。
   *
   * @return 字典类型列表（按 {@code sortOrder} 升序）
   */
  List<DictTypeVO> listAll();
}
