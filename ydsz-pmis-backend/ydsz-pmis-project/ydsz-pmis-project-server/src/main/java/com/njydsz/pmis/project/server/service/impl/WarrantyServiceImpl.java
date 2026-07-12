package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.domain.dto.WarrantyCreateDTO;
import com.njydsz.pmis.project.domain.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.project.server.engine.AfterSalesCodeGen;
import com.njydsz.pmis.project.domain.entity.WarrantyDO;
import com.njydsz.pmis.project.domain.enums.WarrantyStatus;
import com.njydsz.pmis.project.infra.mapper.WarrantyMapper;
import com.njydsz.pmis.project.server.service.WarrantyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 质保期服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarrantyServiceImpl implements WarrantyService {

    /** 质保期 Mapper */
    private final WarrantyMapper warrantyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(WarrantyCreateDTO dto) {
        validate(dto);
        // 同一项目不允许存在多个 ACTIVE 质保期
        List<WarrantyDO> active = warrantyMapper.selectByInitiation(dto.getInitiationId());
        if (active != null) {
            for (WarrantyDO w : active) {
                WarrantyStatus s = WarrantyStatus.fromCode(w.getStatus());
                if (s != null && !s.isTerminal()) {
                    throw new BizException(StandardResultCode.BAD_REQUEST,
                            "error.execution.msg_a3d34659", w.getWarrantyCode());
                }
            }
        }
        WarrantyDO w = new WarrantyDO();
        BeanUtils.copyProperties(dto, w);
        // 默认值
        if (!StringUtils.hasText(w.getWarrantyCode())) {
            w.setWarrantyCode(AfterSalesCodeGen.warrantyCode(LocalDate.now()));
        }
        if (w.getStartDate() == null) w.setStartDate(LocalDate.now());
        if (w.getDurationMonths() == null) w.setDurationMonths(12);
        if (w.getDurationMonths() <= 0 || w.getDurationMonths() > 120) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_75b5c555");
        }
        w.setEndDate(w.getStartDate().plusMonths(w.getDurationMonths()));
        if (w.getNoticeDays() == null) w.setNoticeDays(30);
        if (w.getNoticeDays() < 0 || w.getNoticeDays() > 180) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_f4127654");
        }
        w.setStatus(WarrantyStatus.ACTIVE.getCode());
        if (w.getTenantId() == null) w.setTenantId(TenantContext.getTenantId());
        warrantyMapper.insert(w);
        log.info("[Warranty] 创建质保期: code={} project={} endDate={}",
                w.getWarrantyCode(), w.getInitiationId(), w.getEndDate());
        return w.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(WarrantyTerminateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_40437174");
        }
        WarrantyDO w = warrantyMapper.selectById(dto.getId());
        if (w == null) throw new BizException(StandardResultCode.NOT_FOUND, "error.execution.msg_6457af8b");
        WarrantyStatus st = WarrantyStatus.fromCode(w.getStatus());
        if (st == null || st.isTerminal()) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_b9835ff3", w.getStatus());
        }
        if (!st.canTransitTo(WarrantyStatus.TERMINATED)) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_5b3f83db", st.getDesc());
        }
        warrantyMapper.markStatus(dto.getId(), WarrantyStatus.TERMINATED.getCode(), dto.getReason());
        log.info("[Warranty] 终止质保期: id={} reason={}", dto.getId(), dto.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiring(LocalDate today, int noticeDays) {
        if (today == null) today = LocalDate.now();
        LocalDate until = today.plusDays(Math.max(0, noticeDays));
        List<WarrantyDO> list = warrantyMapper.selectExpiringBefore(until);
        int count = 0;
        for (WarrantyDO w : list) {
            WarrantyStatus st = WarrantyStatus.fromCode(w.getStatus());
            if (st == WarrantyStatus.ACTIVE) {
                warrantyMapper.markStatus(w.getId(), WarrantyStatus.EXPIRING_SOON.getCode(), null);
                count++;
            }
        }
        if (count > 0) {
            log.info("[Warranty] 扫描即将到期: today={} until={} 标记 {} 条", today, until, count);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanOverdue(LocalDate today) {
        if (today == null) today = LocalDate.now();
        List<WarrantyDO> list = warrantyMapper.selectOverdue(today);
        int count = 0;
        for (WarrantyDO w : list) {
            warrantyMapper.markStatus(w.getId(), WarrantyStatus.EXPIRED.getCode(), null);
            count++;
        }
        if (count > 0) {
            log.info("[Warranty] 扫描已过期: today={} 标记 {} 条", today, count);
        }
        return count;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarrantyDO> listExpiring(LocalDate until) {
        return warrantyMapper.selectExpiringBefore(until);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WarrantyDO> page(int page, int size, String status, String initiationId, String keyword) {
        Page<WarrantyDO> p = new Page<>(page, size);
        LambdaQueryWrapper<WarrantyDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) w.eq(WarrantyDO::getStatus, status);
        if (initiationId != null) w.eq(WarrantyDO::getInitiationId, initiationId);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(WarrantyDO::getWarrantyCode, keyword)
                    .or().like(WarrantyDO::getContactName, keyword));
        }
        w.orderByDesc(WarrantyDO::getCreatedAt);
        return warrantyMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public WarrantyDO getById(String id) {
        WarrantyDO w = warrantyMapper.selectById(id);
        if (w == null) throw new BizException(StandardResultCode.NOT_FOUND, "error.execution.msg_6457af8b");
        return w;
    }

    private void validate(WarrantyCreateDTO dto) {
        if (dto == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
    }
}
