package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.ApprovalDTO;
import com.njydsz.pmis.execution.dto.PurchaseCreateDTO;
import com.njydsz.pmis.execution.entity.PurchaseDO;
import com.njydsz.pmis.execution.enums.ApprovalStatus;
import com.njydsz.pmis.execution.mapper.PurchaseMapper;
import com.njydsz.pmis.execution.service.PurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final PurchaseMapper purchaseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getPurchaseCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "采购单号不能为空");
        }
        if (!StringUtils.hasText(dto.getItemName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "物品名称不能为空");
        }
        if (dto.getInitiationId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "项目 ID 不能为空");
        }
        if (dto.getApplicantId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "申请人 ID 不能为空");
        }
        if (purchaseMapper.selectByCode(dto.getPurchaseCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "采购单号已存在: " + dto.getPurchaseCode());
        }
        PurchaseDO p = new PurchaseDO();
        BeanUtils.copyProperties(dto, p);
        // 自动计算金额
        if (p.getAmount() == null && p.getQuantity() != null && p.getUnitPrice() != null) {
            p.setAmount(p.getQuantity().multiply(p.getUnitPrice()));
        }
        if (p.getQuantity() == null) p.setQuantity(java.math.BigDecimal.ONE);
        if (!StringUtils.hasText(p.getStatus())) p.setStatus(ApprovalStatus.DRAFT.getCode());
        if (p.getTenantId() == null) p.setTenantId(1L);
        if (p.getProviderTraceId() == null) p.setProviderTraceId("");

        purchaseMapper.insert(p);
        log.info("[Purchase] 创建采购单: code={} item={} amount={}",
                p.getPurchaseCode(), p.getItemName(), p.getAmount());
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ApprovalDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        PurchaseDO p = getById(dto.getId());
        ApprovalStatus from = ApprovalStatus.fromCode(p.getStatus());
        ApprovalStatus to = ApprovalStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知状态: " + dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "采购状态不允许迁移: " + (from == null ? "未知" : from.getDesc()) + " → " + to.getDesc());
        }
        purchaseMapper.updateStatus(dto.getId(), to.getCode(),
                dto.getApproverId(), dto.getApproverName());
        log.info("[Purchase] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseDO p = getById(id);
        ApprovalStatus s = ApprovalStatus.fromCode(p.getStatus());
        if (s == ApprovalStatus.APPROVED || s == ApprovalStatus.PAID) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已批准/已支付采购单不能删除");
        }
        purchaseMapper.deleteById(id);
    }

    @Override
    public PurchaseDO getById(Long id) {
        PurchaseDO p = purchaseMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "采购单不存在");
        return p;
    }

    @Override
    public Page<PurchaseDO> page(int page, int size, String keyword, String status, Long initiationId) {
        Page<PurchaseDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PurchaseDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PurchaseDO::getPurchaseCode, keyword)
                    .or().like(PurchaseDO::getItemName, keyword)
                    .or().like(PurchaseDO::getVendor, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(PurchaseDO::getStatus, status);
        if (initiationId != null) w.eq(PurchaseDO::getInitiationId, initiationId);
        w.orderByDesc(PurchaseDO::getPurchaseDate);
        return purchaseMapper.selectPage(p, w);
    }
}
