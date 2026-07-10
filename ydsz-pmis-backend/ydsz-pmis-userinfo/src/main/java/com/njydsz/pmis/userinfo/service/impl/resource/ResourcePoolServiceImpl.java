package com.njydsz.pmis.userinfo.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.userinfo.dto.resource.ResourcePoolCreateDTO;
import com.njydsz.pmis.userinfo.entity.resource.ResourcePoolDO;
import com.njydsz.pmis.userinfo.enums.PoolType;
import com.njydsz.pmis.userinfo.mapper.resource.ResourcePoolMapper;
import com.njydsz.pmis.userinfo.service.resource.ResourcePoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 资源池服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcePoolServiceImpl implements ResourcePoolService {

    private final ResourcePoolMapper poolMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(ResourcePoolCreateDTO dto) {
        validate(dto);
        if (poolMapper.selectByCode(dto.getPoolCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "error.user.msg_c51c8d33", dto.getPoolCode());
        }
        if (PoolType.fromCode(dto.getPoolType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_c3e0a19a", dto.getPoolType());
        }
        ResourcePoolDO p = new ResourcePoolDO();
        BeanUtils.copyProperties(dto, p);
        if (!StringUtils.hasText(p.getStatus())) p.setStatus("ACTIVE");
        if (p.getHeadcount() == null) p.setHeadcount(0);
        if (p.getBillableTarget() == null) p.setBillableTarget(0);
        if (p.getTenantId() == null) p.setTenantId(TenantContext.getTenantId());
        if (p.getProviderTraceId() == null) p.setProviderTraceId("");
        poolMapper.insert(p);
        log.info("[ResourcePool] 创建资源池: code={} type={}", p.getPoolCode(), p.getPoolType());
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(String id, ResourcePoolCreateDTO dto) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_411b6827");
        ResourcePoolDO p = poolMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_f0e76f2f");
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
    public void delete(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_411b6827");
        poolMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ResourcePoolDO getById(String id) {
        if (id == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_411b6827");
        ResourcePoolDO p = poolMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.user.msg_f0e76f2f");
        return p;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourcePoolDO> listByType(String poolType) {
        if (!StringUtils.hasText(poolType)) return List.of();
        return poolMapper.selectByType(poolType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourcePoolDO> listByDept(String departmentId) {
        if (departmentId == null) return List.of();
        return poolMapper.selectByDept(departmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResourcePoolDO> page(int page, int size, String poolType, String status) {
        Page<ResourcePoolDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ResourcePoolDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(poolType)) w.eq(ResourcePoolDO::getPoolType, poolType);
        if (StringUtils.hasText(status)) w.eq(ResourcePoolDO::getStatus, status);
        w.orderByDesc(ResourcePoolDO::getCreatedAt);
        return poolMapper.selectPage(p, w);
    }

    private void validate(ResourcePoolCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (!StringUtils.hasText(dto.getPoolCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_27b42dc0");
        }
        if (!StringUtils.hasText(dto.getPoolName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_04617d5a");
        }
        if (!StringUtils.hasText(dto.getPoolType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.user.msg_92a85357");
        }
    }
}