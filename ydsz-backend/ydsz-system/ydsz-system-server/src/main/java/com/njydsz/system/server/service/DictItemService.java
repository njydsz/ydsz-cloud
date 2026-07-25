package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.vo.DictItemVO;

/**
 * 字典项 Service。
 *
 * <p>提供字典项 CRUD、按类型+编码查询、按类型查询列表、树形查询、分页查询等能力，
 * 集成 Redis 缓存、Micrometer 指标、缓存穿透防护和字典版本快照。
 *
 * @author ydsz-team
 */
public interface DictItemService {

    /**
     * 按 ID 查询字典项。
     *
     * @param id 主键 ID
     * @return 字典项 VO
     */
    DictItemVO getById(String id);

    /**
     * 按类型编码和字典项编码查询启用的字典项（走缓存）。
     *
     * @param typeCode 字典类型编码
     * @param itemCode 字典项编码
     * @return 字典项 VO
     */
    DictItemVO getByTypeAndCode(String typeCode, String itemCode);

    /**
     * 按类型编码查询所有启用的字典项（走缓存）。
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表
     */
    List<DictItemVO> listEnabledByTypeCode(String typeCode);

    /**
     * 按父级 ID 查询子字典项列表（树形字典）。
     *
     * @param parentId 父级字典项 ID
     * @return 子字典项列表
     */
    List<DictItemVO> listChildren(String parentId);

    /**
     * 分页查询字典项。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 分页结果（VO）
     */
    IPage<DictItemVO> page(int pageNum, int pageSize);

    /**
     * 查询全部字典项（仅内部使用）。
     *
     * @return 字典项列表（VO）
     */
    List<DictItemVO> list();

    /**
     * 创建字典项（自动记录版本快照）。
     *
     * @param dto 字典项 DTO
     * @return 主键 ID
     */
    String save(DictItemDTO dto);

    /**
     * 更新字典项（自动记录版本快照）。
     *
     * @param dto 字典项 DTO
     * @return 是否成功
     */
    boolean updateById(DictItemDTO dto);

    /**
     * 删除字典项（自动记录版本快照）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
