package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.WarrantyCreateDTO;
import com.njydsz.pmis.execution.dto.WarrantyTerminateDTO;
import com.njydsz.pmis.execution.engine.AfterSalesCodeGen;
import com.njydsz.pmis.execution.entity.WarrantyDO;
import com.njydsz.pmis.execution.enums.WarrantyStatus;
import com.njydsz.pmis.execution.mapper.WarrantyMapper;
import com.njydsz.pmis.execution.service.WarrantyService;
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

    private final WarrantyMapper warrantyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WarrantyCreateDTO dto) {
        validate(dto);
        // 同一项目不允许存在多个 ACTIVE 质保期
        List<WarrantyDO> active = warrantyMapper.selectByInitiation(dto.getInitiationId());
        if (active != null) {
            for (WarrantyDO w : active) {
                WarrantyStatus s = WarrantyStatus.fromCode(w.getStatus());
                if (s != null && !s.isTerminal()) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "项目已存在未结清的质保期: " + w.getWarrantyCode());
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "质保期月数必须在 1-120 之间");
        }
        w.setEndDate(w.getStartDate().plusMonths(w.getDurationMonths()));
        if (w.getNoticeDays() == null) w.setNoticeDays(30);
        if (w.getNoticeDays() < 0 || w.getNoticeDays() > 180) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "提醒天数必须在 0-180 之间");
        }
        w.setStatus(WarrantyStatus.ACTIVE.getCode());
        if (w.getTenantId() == null) w.setTenantId(1L);
        warrantyMapper.insert(w);
        log.info("[Warranty] 创建质保期: code={} project={} endDate={}",
                w.getWarrantyCode(), w.getInitiationId(), w.getEndDate());
        return w.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(WarrantyTerminateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "参数不能为空");
        }
        WarrantyDO w = warrantyMapper.selectById(dto.getId());
        if (w == null) throw new BizException(BizErrorCode.NOT_FOUND, "质保期不存在");
        WarrantyStatus st = WarrantyStatus.fromCode(w.getStatus());
        if (st == null || st.isTerminal()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "质保期已处于终态: " + w.getStatus());
        }
        if (!st.canTransitTo(WarrantyStatus.TERMINATED)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "当前状态不允许终止: " + st.getDesc());
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
    public List<WarrantyDO> listExpiring(LocalDate until) {
        return warrantyMapper.selectExpiringBefore(until);
    }

    @Override
    public Page<WarrantyDO> page(int page, int size, String status, Long initiationId, String keyword) {
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
    public WarrantyDO getById(Long id) {
        WarrantyDO w = warrantyMapper.selectById(id);
        if (w == null) throw new BizException(BizErrorCode.NOT_FOUND, "质保期不存在");
        return w;
    }

    private void validate(WarrantyCreateDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
    }
}
