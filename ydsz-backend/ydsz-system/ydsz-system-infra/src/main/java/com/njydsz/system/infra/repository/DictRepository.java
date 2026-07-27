package com.njydsz.system.infra.repository;

import org.springframework.stereotype.Repository;

import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.infra.mapper.DictTypeMapper;

import lombok.RequiredArgsConstructor;

/**
 * 字典类型仓储。
 *
 * <p>封装 DictTypeMapper，提供字典类型数据访问能力。
 *
 * @author ydsz-team
 */
@Repository
@RequiredArgsConstructor
public class DictRepository {

    private final DictTypeMapper dictTypeMapper;

    /**
     * 获取原生 Mapper。
     *
     * @return 字典类型 Mapper
     */
    public DictTypeMapper getDictTypeMapper() {
        return dictTypeMapper;
    }
}
