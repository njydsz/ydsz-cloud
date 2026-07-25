package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.system.domain.entity.DictVersionDO;
import com.njydsz.system.infra.mapper.DictVersionMapper;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典版本 Service 实现。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictVersionServiceImpl implements DictVersionService {

    private final DictVersionMapper mapper;

    @Override
    public List<DictVersionDO> listByTypeCode(String typeCode) {
        return mapper.listByTypeCode(typeCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createVersion(String typeCode, String version, String changeLog) {
        DictVersionDO entity = new DictVersionDO();
        entity.setTypeCode(typeCode);
        entity.setVersion(version);
        entity.setChangeLog(changeLog);
        entity.setEffectiveDate(LocalDateTime.now());
        mapper.insert(entity);
        return entity.getId();
    }
}
