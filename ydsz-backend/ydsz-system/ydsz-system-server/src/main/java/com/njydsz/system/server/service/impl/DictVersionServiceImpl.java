package com.njydsz.system.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.system.domain.entity.DictVersion;
import com.njydsz.system.domain.vo.DictVersionVO;
import com.njydsz.system.infra.mapper.DictVersionMapper;
import com.njydsz.system.server.service.DictVersionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.system.domain.converter.SystemConverter;

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
    public List<DictVersionVO> listByTypeCode(String typeCode) {
        return mapper.listByTypeCode(typeCode).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createVersion(String typeCode, String version, String changeLog, String snapshotJson) {
        DictVersion entity = new DictVersion();
        entity.setTypeCode(typeCode);
        entity.setVersion(version);
        entity.setChangeLog(changeLog);
        entity.setSnapshotJson(snapshotJson);
        entity.setEffectiveDate(LocalDateTime.now());
        mapper.insert(entity);
        return entity.getId();
}
