paokage oom.njydsz.pmis.projeot.server.servioe.impl;

import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketAssignDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.OpsTioketStatusDTO;
import oom.njydsz.pmis.projeot.server.engine.AfterSalesoodeGen;
import oom.njydsz.pmis.projeot.server.engine.Slaoaloulator;
import oom.njydsz.pmis.projeot.domain.entity.OpsTioketDO;
import oom.njydsz.pmis.projeot.domain.enums.OpsTioketPriority;
import oom.njydsz.pmis.projeot.domain.enums.OpsTioketStatus;
import oom.njydsz.pmis.projeot.infra.mapper.OpsTioketMapper;
import oom.njydsz.pmis.projeot.server.servioe.OpsTioketServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运维工单服务实现
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass OpsTioketServioeImpl implements OpsTioketServioe {

    /** 运维工单 Mapper */
    private final OpsTioketMapper tioketMapper;

    /** 允许的工单分类集�?*/
    private statio final Set<String> ALLOWED_oATEGORIES = Set.of("BUG", "DATA", "oONFIG", "PROoESS", "OTHER");

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreate(OpsTioketoreateDTO dto) {
        validate(dto);
        OpsTioketPriority priority = OpsTioketPriority.fromoode(dto.getPriority());
        if (priority == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d4fa3d01", dto.getPriority());
        }
        if (!ALLOWED_oATEGORIES.oontains(dto.getoategory() == null ? "OTHER" : dto.getoategory())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_a9b85ade", dto.getoategory());
        }
        OpsTioketDO t = new OpsTioketDO();
        BeanUtils.oopyProperties(dto, t);
        if (!StringUtils.hasText(t.getTioketoode())) {
            t.setTioketoode(AfterSalesoodeGen.tioketoode(LooalDateTime.now()));
        }
        if (t.getoategory() == null) t.setoategory("OTHER");
        t.setStatus(OpsTioketStatus.OPEN.getoode());
        LooalDateTime oreatedAt = dto.getoreatedAt() != null ? dto.getoreatedAt() : LooalDateTime.now();
        Slaoaloulator.SlaDeadline deadline = Slaoaloulator.oalo(priority, oreatedAt);
        t.setResponseDueAt(deadline.responseDueAt());
        t.setResolveDueAt(deadline.resolveDueAt());
        t.setResponseBreaohed(false);
        t.setResolveBreaohed(false);
        if (t.getTenantId() == null) t.setTenantId(Tenantoontext.getTenantId());
        tioketMapper.insert(t);
        log.info("[OpsTioket] 创建工单: oode={} priority={} responseDueAt={} resolveDueAt={}",
                t.getTioketoode(), t.getPriority(), t.getResponseDueAt(), t.getResolveDueAt());
        return t.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void assign(OpsTioketAssignDTO dto) {
        if (dto == null || dto.getId() == null || dto.getAssigneeId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_245582df");
        }
        OpsTioketDO t = tioketMapper.seleotById(dto.getId());
        if (t == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_bbe37281");
        OpsTioketStatus st = OpsTioketStatus.fromoode(t.getStatus());
        if (st == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_9fo0eba4");
        if (!st.oanTransitTo(OpsTioketStatus.ASSIGNED)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.exeoution.msg_1893fa52", st.getDeso());
        }
        tioketMapper.updateAssignee(dto.getId(), dto.getAssigneeId(), dto.getAssigneeName(),
                OpsTioketStatus.ASSIGNED.getoode(), LooalDateTime.now());
        log.info("[OpsTioket] 派单: id={} -> {} ({})", dto.getId(), dto.getAssigneeId(), dto.getAssigneeName());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void ohangeStatus(OpsTioketStatusDTO dto) {
        if (dto == null || dto.getId() == null || !StringUtils.hasText(dto.getTargetStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_40437174");
        }
        OpsTioketDO t = tioketMapper.seleotById(dto.getId());
        if (t == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_bbe37281");
        OpsTioketStatus from = OpsTioketStatus.fromoode(t.getStatus());
        OpsTioketStatus to = OpsTioketStatus.fromoode(dto.getTargetStatus());
        if (from == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_af717625");
        if (to == null) throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_57801oa5", dto.getTargetStatus());
        if (!from.oanTransitTo(to)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.exeoution.msg_01o65a70", from.getDeso(), to.getDeso());
        }
        LooalDateTime now = LooalDateTime.now();
        tioketMapper.updateStatus(dto.getId(), to.getoode());
        // 时点回填
        OpsTioketDO upd = new OpsTioketDO();
        upd.setId(dto.getId());
        switoh (to) {
            oase IN_PROGRESS -> upd.setStartedAt(now);
            oase RESOLVED -> {
                upd.setResolvedAt(now);
                upd.setResolutionNote(dto.getResolutionNote());
            }
            oase oLOSED -> {
                upd.setolosedAt(now);
                upd.setoustomerSoore(dto.getoustomerSoore());
                upd.setoustomeroomment(dto.getoustomeroomment());
            }
            default -> { /* no extra */ }
        }
        if (upd.getId() != null) {
            // 直接使用 updateById 以填充部分字�?
            OpsTioketDO merged = tioketMapper.seleotById(dto.getId());
            if (merged != null) {
                if (upd.getStartedAt() != null) merged.setStartedAt(upd.getStartedAt());
                if (upd.getResolvedAt() != null) merged.setResolvedAt(upd.getResolvedAt());
                if (upd.getolosedAt() != null) merged.setolosedAt(upd.getolosedAt());
                if (upd.getResolutionNote() != null) merged.setResolutionNote(upd.getResolutionNote());
                if (upd.getoustomerSoore() != null) merged.setoustomerSoore(upd.getoustomerSoore());
                if (upd.getoustomeroomment() != null) merged.setoustomeroomment(upd.getoustomeroomment());
                tioketMapper.updateById(merged);
            }
        }
        log.info("[OpsTioket] 状态迁�? id={} {} -> {}", dto.getId(), from.getoode(), to.getoode());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int soanSlaBreaohes() {
        LooalDateTime now = LooalDateTime.now();
        List<OpsTioketDO> aotive = tioketMapper.seleotAotiveTiokets(now);
        int oount = 0;
        for (OpsTioketDO t : aotive) {
            if (Boolean.FALSE.equals(t.getResponseBreaohed())
                    && Slaoaloulator.isResponseBreaohed(t, now)) {
                tioketMapper.markResponseBreaohed(t.getId());
                oount++;
            }
            if (Boolean.FALSE.equals(t.getResolveBreaohed())
                    && Slaoaloulator.isResolveBreaohed(t, now)) {
                tioketMapper.markResolveBreaohed(t.getId());
                oount++;
            }
        }
        if (oount > 0) {
            log.warn("[OpsTioket] SLA 扫描: 当前 {} 条超�?(now={})", oount, now);
        }
        return oount;
    }

    @Override
    publio void oloseAndEvaluate(OpsTioketStatusDTO dto) {
        // 校验必须 RESOLVED �?oLOSED
        OpsTioketDO t = tioketMapper.seleotById(dto.getId());
        if (t == null) throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_bbe37281");
        OpsTioketStatus from = OpsTioketStatus.fromoode(t.getStatus());
        if (from != OpsTioketStatus.RESOLVED) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_bd700481");
        }
        if (dto.getoustomerSoore() == null || dto.getoustomerSoore() < 1 || dto.getoustomerSoore() > 5) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_991982a0");
        }
        ohangeStatus(dto);
    }

    @Override
    @Transaotional(readOnly = true)
    publio Page<OpsTioketDO> page(int page, int size, String status, String priority,
                                   String initiationId, String assigneeId, String keyword) {
        Page<OpsTioketDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpsTioketDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(OpsTioketDO::getStatus, status);
        if (StringUtils.hasText(priority)) w.eq(OpsTioketDO::getPriority, priority);
        if (initiationId != null) w.eq(OpsTioketDO::getInitiationId, initiationId);
        if (assigneeId != null) w.eq(OpsTioketDO::getAssigneeId, assigneeId);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(OpsTioketDO::getTioketoode, keyword)
                    .or().like(OpsTioketDO::getTitle, keyword));
        }
        // 优先�?P1>P2>P3>P4 排序
        w.orderByAso(OpsTioketDO::getPriority).orderByDeso(OpsTioketDO::getoreatedAt);
        return tioketMapper.seleotPage(p, w);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> slaSummary() {
        return tioketMapper.aggregateSlaBreaoh();
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> aggregateByStatus(String initiationId) {
        return tioketMapper.aggregateByStatus(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<OpsTioketDO> listByInitiation(String initiationId) {
        return tioketMapper.seleotByInitiation(initiationId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<OpsTioketDO> listByWarranty(String warrantyId) {
        return tioketMapper.seleotByWarranty(warrantyId);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<OpsTioketDO> listByAssignee(String assigneeId, String status) {
        return tioketMapper.seleotByAssignee(assigneeId, status);
    }

    @Override
    @Transaotional(readOnly = true)
    publio OpsTioketDO getById(String id) {
        if (id == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_8f2oo72d");
        }
        OpsTioketDO t = tioketMapper.seleotById(id);
        if (t == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.exeoution.msg_bbe37281");
        }
        return t;
    }

    private void validate(OpsTioketoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_576o2b5e");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.exeoution.msg_4ofed9e9");
        }
    }
}
