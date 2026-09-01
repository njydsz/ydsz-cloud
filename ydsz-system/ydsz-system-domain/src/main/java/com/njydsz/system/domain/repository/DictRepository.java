package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictItemPageQuery;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictItemVO;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典仓储接口（domain 层契约）。
 *
 * <p>定义字典类型与字典项的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link DictTypeVO} / {@link DictItemVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link DictPageQuery} / {@link DictItemPageQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link DictTypeDTO} / {@link DictItemDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface DictRepository {

  // ============================== 字典类型 ==============================

  /**
   * 分页查询字典类型。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<DictTypeVO>> findTypePage(DictPageQuery query);

  /**
   * 根据主键查询字典类型。
   *
   * @param id 字典类型主键
   * @return 字典类型 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<DictTypeVO> findTypeById(String id);

  /**
   * 查询全部字典类型（不区分状态）。
   *
   * @return 全部字典类型 VO 列表
   */
  List<DictTypeVO> findAllTypes();

  /**
   * 校验字典类型编码是否已存在（排除指定 ID）。
   *
   * @param typeCode 字典类型编码
   * @param excludeId 排除的主键 ID（更新场景排除自身）
   * @return 已存在返回 {@code true}
   */
  boolean existsTypeCode(String typeCode, String excludeId);

  /**
   * 插入字典类型。
   *
   * @param dto 字典类型 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insertType(DictTypeDTO dto);

  /**
   * 更新字典类型。
   *
   * @param dto 字典类型 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateTypeById(DictTypeDTO dto);

  /**
   * 逻辑删除字典类型。
   *
   * @param id 字典类型 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteTypeById(String id);

  /**
   * 统计指定类型编码下的字典项数量。
   *
   * @param typeCode 字典类型编码
   * @return 字典项数量
   */
  long countItemsByTypeCode(String typeCode);

  // ============================== 字典项 ==============================

  /**
   * 根据主键查询字典项。
   *
   * @param id 字典项主键
   * @return 字典项 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<DictItemVO> findItemById(String id);

  /**
   * 按类型编码和字典项编码查询启用的字典项。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 字典项 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<DictItemVO> findItemByTypeAndCode(String typeCode, String itemCode);

  /**
   * 按类型编码查询所有启用的字典项（按排序号升序）。
   *
   * @param typeCode 字典类型编码
   * @return 启用状态的字典项 VO 列表
   */
  List<DictItemVO> findItemsEnabledByTypeCode(String typeCode);

  /**
   * 查询指定父节点下的所有子字典项。
   *
   * @param parentId 父字典项 ID
   * @return 子字典项 VO 列表（按 sortOrder 升序）
   */
  List<DictItemVO> findItemsByParentId(String parentId);

  /**
   * 按类型编码查询所有字典项（含全部状态，用于树形构建）。
   *
   * @param typeCode 字典类型编码
   * @return 字典项 VO 列表（按 sortOrder 升序）
   */
  List<DictItemVO> findItemsByTypeCode(String typeCode);

  /**
   * 分页查询字典项。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<DictItemVO>> findItemPage(DictItemPageQuery query);

  /**
   * 查询全部字典项（不区分状态）。
   *
   * @return 全部字典项 VO 列表
   */
  List<DictItemVO> findAllItems();

  /**
   * 查询待导出字典项（未删除记录，按类型/排序）。
   *
   * @param typeCode 字典类型编码（为空导出全部）
   * @return 字典项 VO 列表
   */
  List<DictItemVO> findItemsForExport(String typeCode);

  /**
   * 按类型编码集合查询字典项（用于批量唯一性校验）。
   *
   * @param typeCodes 字典类型编码集合
   * @return 字典项 VO 列表（含 type_code, item_code）
   */
  List<DictItemVO> findItemsByTypeCodes(Set<String> typeCodes);

  /**
   * 校验 (typeCode, itemCode) 组合是否已存在。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 已存在返回 {@code true}
   */
  boolean existsItemByTypeAndCode(String typeCode, String itemCode);

  /**
   * 插入字典项。
   *
   * @param dto 字典项 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insertItem(DictItemDTO dto);

  /**
   * 更新字典项。
   *
   * @param dto 字典项 DTO
   * @return 更新成功返回 {@code true}
   */
  boolean updateItemById(DictItemDTO dto);

  /**
   * 逻辑删除字典项。
   *
   * @param id 字典项 ID
   * @return 删除成功返回 {@code true}
   */
  boolean deleteItemById(String id);

  /**
   * 批量删除插入字典项（一次 SQL 批量写入）。
   *
   * @param items 字典项 DTO 列表
   * @return 插入成功返回 {@code true}
   */
  boolean insertItemsBatch(List<DictItemDTO> items);

  /**
   * 物理删除指定类型编码下的所有字典项（含逻辑删除标记的记录）。
   *
   * @param typeCode 字典类型编码
   * @return 删除的记录数
   */
  int physicalDeleteByTypeCode(String typeCode);

  /**
   * 查询全部启用且未删除的字典项（用于缓存预热）。
   *
   * @return 启用且未删除的字典项 VO 列表
   */
  List<DictItemVO> findEnabledItems();
}
