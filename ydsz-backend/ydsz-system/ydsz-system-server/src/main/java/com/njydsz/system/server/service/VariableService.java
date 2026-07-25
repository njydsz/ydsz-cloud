package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.entity.VariableDO;

/**
 * Variable service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface VariableService {

    VariableDO getById(String id);
    List<VariableDO> list();
    String save(VariableDO entity);
    boolean updateById(VariableDO entity);
    boolean removeById(String id);
}
