package com.njydsz.pmis.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.user.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.user.entity.ResourcePoolDO;
import com.njydsz.pmis.user.enums.PoolType;
import com.njydsz.pmis.user.mapper.ResourcePoolMapper;
import com.njydsz.pmis.user.service.ResourcePoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePoolServiceImpl implements ResourcePoolService {

    private final ResourcePoolMapper poolMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ResourcePoolCreateDTO dto) {
        validate(dto);
        if (poolMapper.selectByCode(dto.getPoolCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "池编号已存在: " + dto.getPoolCode());
        }
        if (PoolType.fromCode(dto.getPoolType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "无效池类型: " + dto.getPoolType());
        }
        ResourcePoolDO p = new ResourcePoolDO();
        BeanUtils.copyProperties(dto, p);
        if (!StringUtils.hasText(p.getStatus())) p.setStatus("ACTIVE");
        if (p.getHeadcount() == null) p.setHeadcount(0);
        if (p.getBillableTarget() == null) p.setBillableTarget(0);
        if (p.getTenantId() == null) p.setTenantId(1L);
        if (p.getProviderTraceId() == null) p.setProviderTraceId("");
        poolMapper.insert(p);
        log.info("[ResourcePool] 创建资源池: code={} type={}", p.getPoolCode(), p.getPoolType());
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ResourcePoolCreateDTO dto) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        ResourcePoolDO p = poolMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "资源池不存在");
        if (dto.getPoolName() != null) p.setPoolName(dto.getPoolName());
        if (dto.getDepartmentId() != null) p.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) p.setDepartmentName(dto.getDepartmentName());
        if (dto.getLevelRange() != null) p.setLevelRange(dto.getLevelRange());
        if (dto.getHeadcount() != null) p.setHeadcount(dto.getHeadcount());
        if (dto.getBillableTarget() != null) p.setBillableTarget(dto.getBillableTarget());
        if (dto.getDescription() != null) p.setDescription(dto.getDescription());
        if (dto.getStatus() != null) p.setStatus(dto.getStatus());
        poolMapper.updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        poolMapper.deleteById(id);
    }

    @Override
    public ResourcePoolDO getById(Long id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "ID 不能为空");
        ResourcePoolDO p = poolMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "资源池不存在");
        return p;
    }

    @Override
    public List<ResourcePoolDO> listByType(String poolType) {
        if (!StringUtils.hasText(poolType)) return List.of();
        return poolMapper.selectByType(poolType);
    }

    @Override
    public List<ResourcePoolDO> listByDept(Long departmentId) {
        if (departmentId == null) return List.of();
        return poolMapper.selectByDept(departmentId);
    }

    @Override
    public Page<ResourcePoolDO> page(int page, int size, String poolType, String status) {
        Page<ResourcePoolDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ResourcePoolDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(poolType)) w.eq(ResourcePoolDO::getPoolType, poolType);
        if (StringUtils.hasText(status)) w.eq(ResourcePoolDO::getStatus, status);
        w.orderByDesc(ResourcePoolDO::getCreatedAt);
        return poolMapper.selectPage(p, w);
    }

    private void validate(ResourcePoolCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getPoolCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "池编号不能为空");
        }
        if (!StringUtils.hasText(dto.getPoolName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "池名称不能为空");
        }
        if (!StringUtils.hasText(dto.getPoolType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "池类型不能为空");
        }
    }
}
