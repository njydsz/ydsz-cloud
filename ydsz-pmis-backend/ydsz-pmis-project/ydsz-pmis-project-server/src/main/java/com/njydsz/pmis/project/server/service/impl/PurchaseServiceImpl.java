package com.njydsz.pmis.project.server.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.project.domain.dto.ApprovalDTO;
import com.njydsz.pmis.project.domain.dto.PurchaseCreateDTO;
import com.njydsz.pmis.project.server.engine.BudgetGuard;
import com.njydsz.pmis.project.domain.entity.PurchaseDO;
import com.njydsz.pmis.project.domain.enums.ApprovalStatus;
import com.njydsz.pmis.project.infra.mapper.PurchaseMapper;
import com.njydsz.pmis.project.server.service.PurchaseService;
import com.njydsz.pmis.common.security.DataScopeHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 采购成本服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    /** 采购成本 Mapper */
    private final PurchaseMapper purchaseMapper;
    /** 预算守卫（采购超预算校验） */
    private final BudgetGuard budgetGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(PurchaseCreateDTO dto) {
        if (dto == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getPurchaseCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_5e907df2");
        }
        if (!StringUtils.hasText(dto.getItemName())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_f93c80f1");
        }
        if (dto.getInitiationId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_576c2b5e");
        }
        if (dto.getApplicantId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_98bc5a1a");
        }
        if (purchaseMapper.selectByCode(dto.getPurchaseCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_126ca992", dto.getPurchaseCode());
        }
        PurchaseDO p = new PurchaseDO();
        BeanUtils.copyProperties(dto, p);
        // 自动计算金额
        if (p.getAmount() == null && p.getQuantity() != null && p.getUnitPrice() != null) {
            p.setAmount(p.getQuantity().multiply(p.getUnitPrice()));
        }
        if (p.getQuantity() == null) p.setQuantity(BigDecimal.ONE);
        if (!StringUtils.hasText(p.getStatus())) p.setStatus(ApprovalStatus.DRAFT.getCode());
        if (p.getTenantId() == null) p.setTenantId(TenantContext.getTenantId());
        if (p.getProviderTraceId() == null) p.setProviderTraceId("");

        // 预算强管控：本次新增 + 项目已发生 ≤ 立项预算
        if (p.getAmount() != null && p.getAmount().signum() > 0) {
            budgetGuard.check(p.getInitiationId(), p.getAmount(), "PURCHASE");
        }

        purchaseMapper.insert(p);
        log.info("[Purchase] 创建采购单: code={} item={} amount={}",
                p.getPurchaseCode(), p.getItemName(), p.getAmount());
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ApprovalDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        }
        PurchaseDO p = getById(dto.getId());
        ApprovalStatus from = ApprovalStatus.fromCode(p.getStatus());
        ApprovalStatus to = ApprovalStatus.fromCode(dto.getTargetStatus());
        if (to == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_7bc741c6", dto.getTargetStatus());
        }
        if (from == null || !from.canTransitTo(to)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_8d2ee457", (from == null ? "未知" : from.getDesc()), to.getDesc());
        }
        purchaseMapper.updateStatus(dto.getId(), to.getCode(),
                dto.getApproverId(), dto.getApproverName());
        log.info("[Purchase] 状态迁移: id={} {} -> {}", dto.getId(), from.getCode(), to.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        PurchaseDO p = getById(id);
        ApprovalStatus s = ApprovalStatus.fromCode(p.getStatus());
        if (s == ApprovalStatus.APPROVED || s == ApprovalStatus.PAID) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_306554e9");
        }
        purchaseMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseDO getById(String id) {
        PurchaseDO p = purchaseMapper.selectById(id);
        if (p == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_df942bcd");
        return p;
    }

    @Override
    @DataScope(userColumn = "created_by")
    @Transactional(readOnly = true)
    public Page<PurchaseDO> page(int page, int size, String keyword, String status, String initiationId) {
        Page<PurchaseDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PurchaseDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PurchaseDO::getPurchaseCode, keyword)
                    .or().like(PurchaseDO::getItemName, keyword)
                    .or().like(PurchaseDO::getVendor, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(PurchaseDO::getStatus, status);
        if (initiationId != null) w.eq(PurchaseDO::getInitiationId, initiationId);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "created_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(PurchaseDO::getPurchaseDate);
        return purchaseMapper.selectPage(p, w);
    }
}
