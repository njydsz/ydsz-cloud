package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.entity.DictTypeDO;

/**
 * Dict service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DictService {

    DictTypeDO getById(String id);
    List<DictTypeDO> list();
    String save(DictTypeDO entity);
    boolean updateById(DictTypeDO entity);
    boolean removeById(String id);
}
