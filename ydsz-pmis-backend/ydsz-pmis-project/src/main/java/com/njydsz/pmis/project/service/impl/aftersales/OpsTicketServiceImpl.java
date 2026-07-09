package com.njydsz.pmis.project.service.impl.aftersales;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.aftersales.OpsTicketAssignDTO;
import com.njydsz.pmis.project.dto.aftersales.OpsTicketCreateDTO;
import com.njydsz.pmis.project.dto.aftersales.OpsTicketStatusDTO;
import com.njydsz.pmis.project.engine.AfterSalesCodeGen;
import com.njydsz.pmis.project.engine.SlaCalculator;
import com.njydsz.pmis.project.entity.aftersales.OpsTicketDO;
import com.njydsz.pmis.project.enums.aftersales.OpsTicketPriority;
import com.njydsz.pmis.project.enums.aftersales.OpsTicketStatus;
import com.njydsz.pmis.project.mapper.aftersales.OpsTicketMapper;
import com.njydsz.pmis.project.service.aftersales.OpsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 运维工单服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsTicketServiceImpl implements OpsTicketService {

    private final OpsTicketMapper ticketMapper;

    private static final Set<String> ALLOWED_CATEGORIES = Set.of("BUG", "DATA", "CONFIG", "PROCESS", "OTHER");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(OpsTicketCreateDTO dto) {
        validate(dto);
        OpsTicketPriority priority = OpsTicketPriority.fromCode(dto.getPriority());
        if (priority == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d4fa3d01", dto.getPriority());
        }
        if (!ALLOWED_CATEGORIES.contains(dto.getCategory() == null ? "OTHER" : dto.getCategory())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_a9b85ade", dto.getCategory());
        }
        OpsTicketDO t = new OpsTicketDO();
        BeanUtils.copyProperties(dto, t);
        if (!StringUtils.hasText(t.getTicketCode())) {
            t.setTicketCode(AfterSalesCodeGen.ticketCode(LocalDateTime.now()));
        }
        if (t.getCategory() == null) t.setCategory("OTHER");
        t.setStatus(OpsTicketStatus.OPEN.getCode());
        LocalDateTime createdAt = dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now();
        SlaCalculator.SlaDeadline deadline = SlaCalculator.calc(priority, createdAt);
        t.setResponseDueAt(deadline.responseDueAt());
        t.setResolveDueAt(deadline.resolveDueAt());
        t.setResponseBreached(false);
        t.setResolveBreached(false);
        if (t.getTenantId() == null) t.setTenantId(TenantContext.getTenantId());
        ticketMapper.insert(t);
        log.info("[OpsTicket] 创建工单: code={} priority={} responseDueAt={} resolveDueAt={}",
                t.getTicketCode(), t.getPriority(), t.getResponseDueAt(), t.getResolveDueAt());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(OpsTicketAssignDTO dto) {
        if (dto == null || dto.getId() == null || dto.getAssigneeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_245582df");
        }
        OpsTicketDO t = ticketMapper.selectById(dto.getId());
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_bbe37281");
        OpsTicketStatus st = OpsTicketStatus.fromCode(t.getStatus());
        if (st == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_9fc0eba4");
        if (!st.canTransitTo(OpsTicketStatus.ASSIGNED)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_1893fa52", st.getDesc());
        }
        ticketMapper.updateAssignee(dto.getId(), dto.getAssigneeId(), dto.getAssigneeName(),
                OpsTicketStatus.ASSIGNED.getCode(), LocalDateTime.now());
        log.info("[OpsTicket] 派单: id={} -> {} ({})", dto.getId(), dto.getAssigneeId(), dto.getAssigneeName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(OpsTicketStatusDTO dto) {
        if (dto == null || dto.getId() == null || !StringUtils.hasText(dto.getTargetStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_40437174");
        }
        OpsTicketDO t = ticketMapper.selectById(dto.getId());
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_bbe37281");
        OpsTicketStatus from = OpsTicketStatus.fromCode(t.getStatus());
        OpsTicketStatus to = OpsTicketStatus.fromCode(dto.getTargetStatus());
        if (from == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_af717625");
        if (to == null) throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_57801ca5", dto.getTargetStatus());
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.execution.msg_01c65a70", from.getDesc(), to.getDesc());
        }
        LocalDateTime now = LocalDateTime.now();
        ticketMapper.updateStatus(dto.getId(), to.getCode());
        // 时点回填
        OpsTicketDO upd = new OpsTicketDO();
        upd.setId(dto.getId());
        switch (to) {
            case IN_PROGRESS -> upd.setStartedAt(now);
            case RESOLVED -> {
                upd.setResolvedAt(now);
                upd.setResolutionNote(dto.getResolutionNote());
            }
            case CLOSED -> {
                upd.setClosedAt(now);
                upd.setCustomerScore(dto.getCustomerScore());
                upd.setCustomerComment(dto.getCustomerComment());
            }
            default -> { /* no extra */ }
        }
        if (upd.getId() != null) {
            // 直接使用 updateById 以填充部分字段
            OpsTicketDO merged = ticketMapper.selectById(dto.getId());
            if (merged != null) {
                if (upd.getStartedAt() != null) merged.setStartedAt(upd.getStartedAt());
                if (upd.getResolvedAt() != null) merged.setResolvedAt(upd.getResolvedAt());
                if (upd.getClosedAt() != null) merged.setClosedAt(upd.getClosedAt());
                if (upd.getResolutionNote() != null) merged.setResolutionNote(upd.getResolutionNote());
                if (upd.getCustomerScore() != null) merged.setCustomerScore(upd.getCustomerScore());
                if (upd.getCustomerComment() != null) merged.setCustomerComment(upd.getCustomerComment());
                ticketMapper.updateById(merged);
            }
        }
        log.info("[OpsTicket] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();
        List<OpsTicketDO> active = ticketMapper.selectActiveTickets(now);
        int count = 0;
        for (OpsTicketDO t : active) {
            if (Boolean.FALSE.equals(t.getResponseBreached())
                    && SlaCalculator.isResponseBreached(t, now)) {
                ticketMapper.markResponseBreached(t.getId());
                count++;
            }
            if (Boolean.FALSE.equals(t.getResolveBreached())
                    && SlaCalculator.isResolveBreached(t, now)) {
                ticketMapper.markResolveBreached(t.getId());
                count++;
            }
        }
        if (count > 0) {
            log.warn("[OpsTicket] SLA 扫描: 当前 {} 条超时 (now={})", count, now);
        }
        return count;
    }

    @Override
    public void closeAndEvaluate(OpsTicketStatusDTO dto) {
        // 校验必须 RESOLVED → CLOSED
        OpsTicketDO t = ticketMapper.selectById(dto.getId());
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_bbe37281");
        OpsTicketStatus from = OpsTicketStatus.fromCode(t.getStatus());
        if (from != OpsTicketStatus.RESOLVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_bd700481");
        }
        if (dto.getCustomerScore() == null || dto.getCustomerScore() < 1 || dto.getCustomerScore() > 5) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_991982a0");
        }
        changeStatus(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OpsTicketDO> page(int page, int size, String status, String priority,
                                   String initiationId, String assigneeId, String keyword) {
        Page<OpsTicketDO> p = new Page<>(page, size);
        LambdaQueryWrapper<OpsTicketDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(OpsTicketDO::getStatus, status);
        if (StringUtils.hasText(priority)) w.eq(OpsTicketDO::getPriority, priority);
        if (initiationId != null) w.eq(OpsTicketDO::getInitiationId, initiationId);
        if (assigneeId != null) w.eq(OpsTicketDO::getAssigneeId, assigneeId);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(OpsTicketDO::getTicketCode, keyword)
                    .or().like(OpsTicketDO::getTitle, keyword));
        }
        // 优先级 P1>P2>P3>P4 排序
        w.orderByAsc(OpsTicketDO::getPriority).orderByDesc(OpsTicketDO::getCreatedAt);
        return ticketMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> slaSummary() {
        return ticketMapper.aggregateSlaBreach();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStatus(String initiationId) {
        return ticketMapper.aggregateByStatus(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpsTicketDO> listByInitiation(String initiationId) {
        return ticketMapper.selectByInitiation(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpsTicketDO> listByWarranty(String warrantyId) {
        return ticketMapper.selectByWarranty(warrantyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpsTicketDO> listByAssignee(String assigneeId, String status) {
        return ticketMapper.selectByAssignee(assigneeId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public OpsTicketDO getById(String id) {
        if (id == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_8f2cc72d");
        }
        OpsTicketDO t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.execution.msg_bbe37281");
        }
        return t;
    }

    private void validate(OpsTicketCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.execution.msg_4cfed9e9");
        }
    }
}
