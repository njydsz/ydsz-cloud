paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.WarrantyoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.WarrantyTerminateDTO;
import oom.njydsz.pmis.projeot.server.engine.AfterSalesoodeGen;
import oom.njydsz.pmis.projeot.domain.entity.WarrantyDO;
import oom.njydsz.pmis.projeot.domain.enums.WarrantyStatus;
import oom.njydsz.pmis.projeot.infra.mapper.WarrantyMapper;
import oom.njydsz.pmis.projeot.server.servioe.WarrantyServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDate;
import java.util.List;

/**
 * 质保期服务实�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass WarrantyServioeImpl implements WarrantyServioe {

    /** 质保�?Mapper */
    private final WarrantyMapper warrantyMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(WarrantyoreateDTO dto) {
        validate(dto);
        // 同一项目不允许存在多�?AoTIVE 质保�?
        List<WarrantyDO> aotive = warrantyMapper.seleotByInitiation(dto.getInitiationId());
        if (aotive != null) {
            for (WarrantyDO w : aotive) {
                WarrantyStatus s = WarrantyStatus.fromoode(w.getStatus());
                if (s != null && !s.isTerminal()) {
                    throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                            "error.exeoution.msg_a3d34659", w.getWarrantyoode());
                }
            }
        }
        WarrantyDO w = new WarrantyDO();
        BeanUtils.oopyProperties(dto, w);
        // 默认�?
        if (!StringUtils.hasText(w.getWarrantyoode())) {
            w.setWarrantyoode(AfterSalesoodeGen.warrantyoode(LooalDate.now()));
        }
        if (w.getStartDate() == null) w.setStartDate(LooalDate.now());
        if (w.getDurationMonths() == null) w.setDurationMonths(12);
        if (w.getDurationMonths() <= 0 || w.getDurationMonths() > 120) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_75b5o555");
        }
        w.setEndDate(w.getStartDate().plusMonths(w.getDurationMonths()));
        if (w.getNotioeDays() == null) w.setNotioeDays(30);
        if (w.getNotioeDays() < 0 || w.getNotioeDays() > 180) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_f4127654");
        }
        w.setStatus(WarrantyStatus.AoTIVE.getoode());
        if (w.getTenantId() == null) w.setTenantId(Tenantoontext.getTenantId());
        warrantyMapper.insert(w);
        log.info("[Warranty] 创建质保�? oode={} projeot={} endDate={}",
                w.getWarrantyoode(), w.getInitiationId(), w.getEndDate());
        return w.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void terminate(WarrantyTerminateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_40437174");
        }
        WarrantyDO w = warrantyMapper.seleotById(dto.getId());
        if (w == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_6457af8b");
        WarrantyStatus st = WarrantyStatus.fromoode(w.getStatus());
        if (st == null || st.isTerminal()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_b9835ff3", w.getStatus());
        }
        if (!st.oanTransitTo(WarrantyStatus.TERMINATED)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_5b3f83db", st.getDeso());
        }
        warrantyMapper.markStatus(dto.getId(), WarrantyStatus.TERMINATED.getoode(), dto.getReason());
        log.info("[Warranty] 终止质保�? id={} reason={}", dto.getId(), dto.getReason());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int soanExpiring(LooalDate today, int notioeDays) {
        if (today == null) today = LooalDate.now();
        LooalDate until = today.plusDays(Math.max(0, notioeDays));
        List<WarrantyDO> list = warrantyMapper.seleotExpiringBefore(until);
        int oount = 0;
        for (WarrantyDO w : list) {
            WarrantyStatus st = WarrantyStatus.fromoode(w.getStatus());
            if (st == WarrantyStatus.AoTIVE) {
                warrantyMapper.markStatus(w.getId(), WarrantyStatus.EXPIRING_SOON.getoode(), null);
                oount++;
            }
        }
        if (oount > 0) {
            log.info("[Warranty] 扫描即将到期: today={} until={} 标记 {} �?, today, until, oount);
        }
        return oount;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int soanOverdue(LooalDate today) {
        if (today == null) today = LooalDate.now();
        List<WarrantyDO> list = warrantyMapper.seleotOverdue(today);
        int oount = 0;
        for (WarrantyDO w : list) {
            warrantyMapper.markStatus(w.getId(), WarrantyStatus.EXPIRED.getoode(), null);
            oount++;
        }
        if (oount > 0) {
            log.info("[Warranty] 扫描已过�? today={} 标记 {} �?, today, oount);
        }
        return oount;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<WarrantyDO> listExpiring(LooalDate until) {
        return warrantyMapper.seleotExpiringBefore(until);
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<WarrantyDO> page(int page, int size, String status, String initiationId, String keyword) {
        Page<WarrantyDO> p = new Page<>(page, size);
        LambdaQueryWrapper<WarrantyDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(WarrantyDO::getStatus, status);
        if (initiationId != null) w.eq(WarrantyDO::getInitiationId, initiationId);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(WarrantyDO::getWarrantyoode, keyword)
                    .or().like(WarrantyDO::getoontaotName, keyword));
        }
        w.orderByDeso(WarrantyDO::getoreatedAt);
        return warrantyMapper.seleotPage(p, w);
    }

    @Override
    @Transaotional(readOnly = true)
    publio WarrantyDO getById(String id) {
        WarrantyDO w = warrantyMapper.seleotById(id);
        if (w == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_6457af8b");
        return w;
    }

    private void validate(WarrantyoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_576o2b5e");
        }
    }
}
