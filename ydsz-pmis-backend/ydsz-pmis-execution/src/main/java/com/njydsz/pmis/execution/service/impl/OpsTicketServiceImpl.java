package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.OpsTicketAssignDTO;
import com.njydsz.pmis.execution.dto.OpsTicketCreateDTO;
import com.njydsz.pmis.execution.dto.OpsTicketStatusDTO;
import com.njydsz.pmis.execution.engine.AfterSalesCodeGen;
import com.njydsz.pmis.execution.engine.SlaCalculator;
import com.njydsz.pmis.execution.entity.OpsTicketDO;
import com.njydsz.pmis.execution.enums.OpsTicketPriority;
import com.njydsz.pmis.execution.enums.OpsTicketStatus;
import com.njydsz.pmis.execution.mapper.OpsTicketMapper;
import com.njydsz.pmis.execution.service.OpsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Long create(OpsTicketCreateDTO dto) {
        validate(dto);
        OpsTicketPriority priority = OpsTicketPriority.fromCode(dto.getPriority());
        if (priority == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "优先级非法: " + dto.getPriority());
        }
        if (!ALLOWED_CATEGORIES.contains(dto.getCategory() == null ? "OTHER" : dto.getCategory())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "工单类型非法: " + dto.getCategory());
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
        if (t.getTenantId() == null) t.setTenantId(1L);
        ticketMapper.insert(t);
        log.info("[OpsTicket] 创建工单: code={} priority={} responseDueAt={} resolveDueAt={}",
                t.getTicketCode(), t.getPriority(), t.getResponseDueAt(), t.getResolveDueAt());
        return t.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(OpsTicketAssignDTO dto) {
        if (dto == null || dto.getId() == null || dto.getAssigneeId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "派单人不能为空");
        }
        OpsTicketDO t = ticketMapper.selectById(dto.getId());
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "工单不存在");
        OpsTicketStatus st = OpsTicketStatus.fromCode(t.getStatus());
        if (st == null) throw new BizException(BizErrorCode.BAD_REQUEST, "工单状态非法");
        if (!st.canTransitTo(OpsTicketStatus.ASSIGNED)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "当前状态不允许派单: " + st.getDesc());
        }
        ticketMapper.updateAssignee(dto.getId(), dto.getAssigneeId(), dto.getAssigneeName(),
                OpsTicketStatus.ASSIGNED.getCode(), LocalDateTime.now());
        log.info("[OpsTicket] 派单: id={} -> {} ({})", dto.getId(), dto.getAssigneeId(), dto.getAssigneeName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(OpsTicketStatusDTO dto) {
        if (dto == null || dto.getId() == null || !StringUtils.hasText(dto.getTargetStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "参数不能为空");
        }
        OpsTicketDO t = ticketMapper.selectById(dto.getId());
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "工单不存在");
        OpsTicketStatus from = OpsTicketStatus.fromCode(t.getStatus());
        OpsTicketStatus to = OpsTicketStatus.fromCode(dto.getTargetStatus());
        if (from == null) throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态非法");
        if (to == null) throw new BizException(BizErrorCode.BAD_REQUEST, "目标状态非法: " + dto.getTargetStatus());
        if (!from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "状态不允许迁移: " + from.getDesc() + " → " + to.getDesc());
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
        if (t == null) throw new BizException(BizErrorCode.NOT_FOUND, "工单不存在");
        OpsTicketStatus from = OpsTicketStatus.fromCode(t.getStatus());
        if (from != OpsTicketStatus.RESOLVED) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅已解决的工单可关闭评价");
        }
        if (dto.getCustomerScore() == null || dto.getCustomerScore() < 1 || dto.getCustomerScore() > 5) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请给出 1-5 星评价");
        }
        changeStatus(dto);
    }

    @Override
    public Page<OpsTicketDO> page(int page, int size, String status, String priority,
                                   Long initiationId, Long assigneeId, String keyword) {
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
    public List<Map<String, Object>> slaSummary() {
        return ticketMapper.aggregateSlaBreach();
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long initiationId) {
        return ticketMapper.aggregateByStatus(initiationId);
    }

    private void validate(OpsTicketCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "工单标题不能为空");
        }
    }
}
