package com.njydsz.pmis.userinfo.service;

import com.njydsz.pmis.userinfo.entity.DictItemDO;
import com.njydsz.pmis.userinfo.entity.DictTypeDO;

import java.util.List;

/**
 * 字典服务（带 Redis 缓存）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface DictService {

    /**
     * 查询所有字典类型
     *
     * @return 字典类型列表
     */
    List<DictTypeDO> listAllTypes();

    /**
     * 根据 typeCode 查询字典项
     *
     * @param typeCode 字典类型编码
     * @return 字典项列表
     */
    List<DictItemDO> listItems(String typeCode);

    /**
     * 刷新缓存（P2-6: 返回最新字典项，由 @CachePut 写入缓存）
     *
     * @param typeCode 字典类型编码
     * @return 最新字典项列表
     */
    List<DictItemDO> refreshCache(String typeCode);
}
