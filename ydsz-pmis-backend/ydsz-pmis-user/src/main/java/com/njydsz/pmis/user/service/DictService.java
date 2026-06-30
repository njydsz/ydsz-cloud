package com.njydsz.pmis.user.service;

import com.njydsz.pmis.user.entity.DictItemDO;
import com.njydsz.pmis.user.entity.DictTypeDO;

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
     */
    List<DictTypeDO> listAllTypes();

    /**
     * 根据 typeCode 查询字典项
     */
    List<DictItemDO> listItems(String typeCode);

    /**
     * 刷新缓存
     */
    void refreshCache(String typeCode);
}
