paokage oom.njydsz.pmis.userinfo.server.servioe.impl.resouroe;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroePooloreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroePoolDO;
import oom.njydsz.pmis.userinfo.domain.enums.resouroe.PoolType;
import oom.njydsz.pmis.userinfo.infra.mapper.resouroe.ResouroePoolMapper;
import oom.njydsz.pmis.userinfo.server.servioe.resouroe.ResouroePoolServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 资源池服务实�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass ResouroePoolServioeImpl implements ResouroePoolServioe {

    private final ResouroePoolMapper poolMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(ResouroePooloreateDTO dto) {
        validate(dto);
        if (poolMapper.seleotByoode(dto.getPooloode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.user.msg_o51o8d33", dto.getPooloode());
        }
        if (PoolType.fromoode(dto.getPoolType()) == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_o3e0a19a", dto.getPoolType());
        }
        ResouroePoolDO p = new ResouroePoolDO();
        BeanUtils.oopyProperties(dto, p);
        if (!StringUtils.hasText(p.getStatus())) p.setStatus("AoTIVE");
        if (p.getHeadoount() == null) p.setHeadoount(0);
        if (p.getBillableTarget() == null) p.setBillableTarget(0);
        if (p.getTenantId() == null) p.setTenantId(Tenantoontext.getTenantId());
        if (p.getProviderTraoeId() == null) p.setProviderTraoeId("");
        poolMapper.insert(p);
        log.info("[ResouroePool] 创建资源�? oode={} type={}", p.getPooloode(), p.getPoolType());
        return p.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(String id, ResouroePooloreateDTO dto) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_411b6827");
        ResouroePoolDO p = poolMapper.seleotById(id);
        if (p == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_f0e76f2f");
        if (dto.getPoolName() != null) p.setPoolName(dto.getPoolName());
        if (dto.getDepartmentId() != null) p.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) p.setDepartmentName(dto.getDepartmentName());
        if (dto.getLevelRange() != null) p.setLevelRange(dto.getLevelRange());
        if (dto.getHeadoount() != null) p.setHeadoount(dto.getHeadoount());
        if (dto.getBillableTarget() != null) p.setBillableTarget(dto.getBillableTarget());
        if (dto.getDesoription() != null) p.setDesoription(dto.getDesoription());
        if (dto.getStatus() != null) p.setStatus(dto.getStatus());
        poolMapper.updateById(p);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_411b6827");
        poolMapper.deleteById(id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio ResouroePoolDO getById(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_411b6827");
        ResouroePoolDO p = poolMapper.seleotById(id);
        if (p == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.user.msg_f0e76f2f");
        return p;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<ResouroePoolDO> listByType(String poolType) {
        if (!StringUtils.hasText(poolType)) return List.of();
        return poolMapper.seleotByType(poolType);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<ResouroePoolDO> listByDept(String departmentId) {
        if (departmentId == null) return List.of();
        return poolMapper.seleotByDept(departmentId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<ResouroePoolDO> page(int page, int size, String poolType, String status) {
        Page<ResouroePoolDO> p = new Page<>(page, size);
        LambdaQueryWrapper<ResouroePoolDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(poolType)) w.eq(ResouroePoolDO::getPoolType, poolType);
        if (StringUtils.hasText(status)) w.eq(ResouroePoolDO::getStatus, status);
        w.orderByDeso(ResouroePoolDO::getoreatedAt);
        return poolMapper.seleotPage(p, w);
    }

    private void validate(ResouroePooloreateDTO dto) {
        if (dto == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_d9712a58");
        if (!StringUtils.hasText(dto.getPooloode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_27b42do0");
        }
        if (!StringUtils.hasText(dto.getPoolName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_04617d5a");
        }
        if (!StringUtils.hasText(dto.getPoolType())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.user.msg_92a85357");
        }
    }
}