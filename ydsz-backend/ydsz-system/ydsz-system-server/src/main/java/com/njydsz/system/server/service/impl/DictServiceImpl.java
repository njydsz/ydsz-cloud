package com.njydsz.system.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.entity.DictTypeDO;
import com.njydsz.system.domain.vo.DictTypeVO;
import com.njydsz.system.infra.mapper.DictTypeMapper;
import com.njydsz.system.server.service.DictService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 字典类型 Service 实现。
 *
 * @author ydsz-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictTypeMapper mapper;

    @Override
    public DictTypeVO getById(String id) {
        DictTypeDO entity = mapper.selectById(id);
        return toVO(entity);
    }

    @Override
    public IPage<DictTypeDO> page(int pageNum, int pageSize) {
        return mapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    @Override
    public List<DictTypeDO> list() {
        return mapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(DictTypeDTO dto) {
        DictTypeDO entity = toEntity(dto);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(DictTypeDTO dto) {
        DictTypeDO entity = toEntity(dto);
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }

    private DictTypeVO toVO(DictTypeDO entity) {
        if (entity == null) {
            return null;
        }
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setTypeCode(entity.getTypeCode());
        vo.setTypeName(entity.getTypeName());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    private DictTypeDO toEntity(DictTypeDTO dto) {
        DictTypeDO entity = new DictTypeDO();
        entity.setId(dto.getId());
        entity.setTypeCode(dto.getTypeCode());
        entity.setTypeName(dto.getTypeName());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
