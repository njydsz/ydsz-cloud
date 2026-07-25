package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.DictItemDTO;
import com.njydsz.system.domain.entity.DictItemDO;
import com.njydsz.system.domain.vo.DictItemVO;

/**
 * 字典项 Service。
 *
 * <p>提供字典项 CRUD、按类型+编码查询、按类型查询列表、分页查询等能力，
 * 集成 Redis 缓存和 Micrometer 指标。
 *
 * @author ydsz-team
 */
public interface DictItemService {

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

    IPage<DictItemDO> page(int pageNum, int pageSize);

    List<DictItemDO> list();

    String save(DictItemDTO dto);

    boolean updateById(DictItemDTO dto);

    boolean removeById(String id);
}
