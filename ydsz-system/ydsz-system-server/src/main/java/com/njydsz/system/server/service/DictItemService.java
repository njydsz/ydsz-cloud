package com.njydsz.system.server.service;
import java.io.InputStream;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.ImportResultVO;



/**
 * 字典项 Service 接口
 *
 * <p>提供字典项（{@code ydsz_sys_dict_item}）的 CRUD、按类型查询、树形查询、分页查询等能力。 集成 Redis 缓存、Micrometer
 * 指标、缓存穿透防护和字典版本快照。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>按类型查询</b>：{@link #getByTypeAndCode} / {@link #listEnabledByTypeCode} — 走 Redis 缓存，
 *       是前端下拉框的核心数据源
 *   <li><b>树形结构</b>：{@link #listChildren} — 支持「省 / 市 / 区县」三级级联
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「字典项管理」列表
 *   <li><b>版本快照</b>：所有写操作触发 {@link DictVersionService#createVersion} 记录变更
 * </ul>
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>Redis 缓存（{@code ydsz:dict:item:{typeCode}}），TTL 30min
 *   <li><b>缓存穿透防护</b>：DB 不存在的 typeCode 缓存「null 哨兵」1min
 *   <li><b>缓存击穿防护</b>：高并发 key 使用 {@code @Cacheable(sync = true)} 单线程回源
 *   <li>写操作通过 {@code @CacheEvict} 主动失效
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see DictService 字典类型 Service
 * @see DictVersionService 字典版本 Service
 * @see com.njydsz.system.infra.entity.DictItem 字典项实体
 */
public interface DictItemService {

  /**
   * 按 ID 查询字典项
   *
   * @param id 主键 ID
   * @return 字典项 VO；不存在返回 {@code null}
   */
  DictItemVO getById(String id);

  /**
   * 按类型编码和字典项编码查询启用的字典项（走缓存）
   *
   * <p>典型用法：业务代码中通过 {@code (typeCode, itemCode)} 反查字典项的展示值。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 字典项 VO；不存在或已禁用返回 {@code null}
   */
  DictItemVO getByTypeAndCode(String typeCode, String itemCode);

  /**
   * 按类型编码查询所有启用的字典项（走缓存）
   *
   * <p>典型用法：前端下拉框 / 单选框数据源。命中 Redis 缓存时延迟 < 1ms。
   *
   * @param typeCode 字典类型编码
   * @return 字典项列表（按 {@code sortOrder} 升序）
   */
  List<DictItemVO> listEnabledByTypeCode(String typeCode);

  /**
   * 按父级 ID 查询子字典项列表（树形字典）
   *
   * <p>用于「省 / 市 / 区县」等树形结构字典的级联查询；{@code parentId=0} 时返回所有根节点。
   *
   * @param parentId 父级字典项 ID
   * @return 子字典项列表
   */
  List<DictItemVO> listChildren(String parentId);

  /**
   * 构建字典项树形结构。
   *
   * <p>将指定类型编码下的所有字典项构建为树形结构，根节点的父级 ID 为 "0"。
   *
   * <p>使用示例：
   *
   * <pre>{@code
   * List<DictItemVO> tree = dictItemService.buildTree("region");
   * // 返回省级列表，每个省级节点包含 cities 子节点，每个市级节点包含 districts 子节点
   * }</pre>
   *
   * @param typeCode 字典类型编码
   * @return 树形结构根节点列表
   */
  List<DictItemVO> buildTree(String typeCode);

  /**
   * 分页查询字典项（支持搜索过滤）
   *
   * <p>管理后台「字典项管理」列表数据源；不走缓存（数据量可控）。
   *
   * @param query 分页查询条件（pageNum / pageSize / typeCode / itemCode / status）
   * @return 分页结果（VO），统一使用 {@link PageResponse}
   */
  PageResponse<List<DictItemVO>> page(DictItemPageQuery query);

  /**
   * 查询全部字典项（仅内部使用）
   *
   * <p>仅供字典版本快照、批量同步等内部场景使用，<b>不对前端暴露</b>。
   *
   * @return 字典项列表（VO）
   */
  List<DictItemVO> list();

  /**
   * 创建字典项（自动记录版本快照）
   *
   * <p>写入前校验 {@code (tenantId, typeCode, itemCode)} 唯一性； 写入成功后异步调用 {@link
   * DictVersionService#createVersion} 记录变更。
   *
   * @param dto 字典项 DTO（命令入参）
   * @return 新建字典项主键 ID
   */
  String save(DictItemDTO dto);

  /**
   * 更新字典项（自动记录版本快照）
   *
   * @param dto 字典项 DTO（命令入参，{@code id} 必填）
   * @return 是否成功
   */
  boolean updateById(DictItemDTO dto);

  /**
   * 删除字典项（自动记录版本快照）
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);

  /**
   * 回滚字典到指定版本
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在
   *   <li>物理删除当前字典项
   *   <li>从目标快照重建字典项
   *   <li>创建新版本记录（标记回滚来源）
   *   <li>失效缓存
   * </ol>
   *
   * @param typeCode 字典类型编码
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID
   * @return 新创建的回滚版本 ID
   */
  String rollbackTo(String typeCode, String targetVersion, String operatorId);

  /**
   * 导出字典项为 Excel 字节数组
   *
   * <p>按字典类型编码导出，使用 ydsz-common-excel 实现。
   *
   * @param typeCode 字典类型编码（为 null 时导出全部字典项）
   * @return Excel 文件字节数组
   */
  byte[] exportDictItems(String typeCode);

  /**
   * 从 Excel 导入字典项
   *
   * <p>使用 ydsz-common-excel 读取 Excel 文件，逐条校验后批量插入。 导入前校验 (typeCode, itemCode) 唯一性，重复时跳过。
   *
   * @param inputStream Excel 文件输入流
   * @return 导入结果（成功数、失败数、跳过数）
   */
  ImportResultVO importDictItems(InputStream inputStream);
}
