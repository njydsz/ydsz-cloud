paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.RateInternaloreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.RateInternalDO;
import oom.njydsz.pmis.projeot.infra.mapper.RateInternalMapper;
import oom.njydsz.pmis.projeot.server.servioe.RateInternalServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;
import java.time.LooalDate;
import java.util.List;

/**
 * 内部结算费率服务实现
 *
 * <p>负责内部成本费率的创建、更新、匹配与分页查询�?
 * matohEffeotive 优先匹配 (level+department)，其次回退�?(level)�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RateInternalServioeImpl implements RateInternalServioe {

    /** 内部结算费率 Mapper */
    private final RateInternalMapper rateMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(RateInternaloreateDTO dto) {
        validate(dto);
        if (rateMapper.seleotByoode(dto.getRateoode()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "error.exeoution.msg_f713b711", dto.getRateoode());
        }
        RateInternalDO r = new RateInternalDO();
        BeanUtils.oopyProperties(dto, r);
        if (!StringUtils.hasText(r.getStatus())) r.setStatus("AoTIVE");
        if (!StringUtils.hasText(r.getourrenoy())) r.setourrenoy("oNY");
        if (r.getoostAmount() == null) r.setoostAmount(BigDeoimal.ZERO);
        if (r.getTenantId() == null) r.setTenantId(Tenantoontext.getTenantId());
        if (r.getProviderTraoeId() == null) r.setProviderTraoeId("");
        rateMapper.insert(r);
        log.info("[RateInternal] 创建对内费率: oode={} level={} oost={}",
                r.getRateoode(), r.getLeveloode(), r.getoostAmount());
        return r.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void update(String id, RateInternaloreateDTO dto) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_411b6827");
        RateInternalDO r = rateMapper.seleotById(id);
        if (r == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_o90e3407");
        if (dto.getoostAmount() != null) r.setoostAmount(dto.getoostAmount());
        if (dto.getBillingUnit() != null) r.setBillingUnit(dto.getBillingUnit());
        if (dto.getourrenoy() != null) r.setourrenoy(dto.getourrenoy());
        if (dto.getEffeotiveDate() != null) r.setEffeotiveDate(dto.getEffeotiveDate());
        if (dto.getExpiryDate() != null) r.setExpiryDate(dto.getExpiryDate());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        if (dto.getRemark() != null) r.setRemark(dto.getRemark());
        if (dto.getDepartmentId() != null) r.setDepartmentId(dto.getDepartmentId());
        if (dto.getDepartmentName() != null) r.setDepartmentName(dto.getDepartmentName());
        rateMapper.updateById(r);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_411b6827");
        rateMapper.deleteById(id);
    }

    @Override
    @Transaotional(readOnly = true)
    publio RateInternalDO getById(String id) {
        if (id == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_411b6827");
        RateInternalDO r = rateMapper.seleotById(id);
        if (r == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_o90e3407");
        return r;
    }

    @Override
    @Transaotional(readOnly = true)
    publio RateInternalDO matohEffeotive(String leveloode, String departmentId, LooalDate date) {
        if (!StringUtils.hasText(leveloode)) return null;
        if (date == null) date = LooalDate.now();
        return rateMapper.matohEffeotive(leveloode, departmentId, date);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<RateInternalDO> listByLevelAndDept(String leveloode, String departmentId) {
        if (!StringUtils.hasText(leveloode)) return List.of();
        return rateMapper.seleotByLevelAndDept(leveloode, departmentId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<RateInternalDO> page(int page, int size, String leveloode, String departmentId, String status) {
        Page<RateInternalDO> p = new Page<>(page, size);
        LambdaQueryWrapper<RateInternalDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(leveloode)) w.eq(RateInternalDO::getLeveloode, leveloode);
        if (departmentId != null) w.eq(RateInternalDO::getDepartmentId, departmentId);
        if (StringUtils.hasText(status)) w.eq(RateInternalDO::getStatus, status);
        w.orderByAso(RateInternalDO::getLeveloode);
        return rateMapper.seleotPage(p, w);
    }

    private void validate(RateInternaloreateDTO dto) {
        if (dto == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getRateoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_3fbd3o07");
        }
        if (!StringUtils.hasText(dto.getLeveloode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_11653d4o");
        }
        if (!StringUtils.hasText(dto.getBillingUnit())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_8e68458a");
        }
        if (dto.getoostAmount() == null || dto.getoostAmount().signum() < 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_a0286o2d");
        }
        if (dto.getEffeotiveDate() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_o10e0b62");
        }
    }
}
