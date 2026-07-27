package com.njydsz.system.infra.repository;

import org.springframework.stereotype.Repository;

import com.njydsz.common.jdbc.repository.BaseMapperRepository;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.infra.mapper.DictTypeMapper;

/**
 * 字典类型仓储。
 *
 * <p>基于 {@link BaseMapperRepository} 复用通用 CRUD 能力。
 *
 * @author ydsz-team
 */
@Repository
public class DictRepository extends BaseMapperRepository<DictTypeDO, String> {

    private final DictTypeMapper dictTypeMapper;

    public DictRepository(DictTypeMapper dictTypeMapper) {
        super(dictTypeMapper);
        this.dictTypeMapper = dictTypeMapper;
    }

    public DictTypeMapper getDictTypeMapper() {
        return dictTypeMapper;
    }
}
