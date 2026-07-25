package com.njydsz.system.server.service;

import java.util.List;
import com.njydsz.system.domain.entity.DictItemDO;

/**
 * Dict item service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DictItemService {

    DictItemDO getById(String id);
    List<DictItemDO> list();
    String save(DictItemDO entity);
    boolean updateById(DictItemDO entity);
    boolean removeById(String id);
}
