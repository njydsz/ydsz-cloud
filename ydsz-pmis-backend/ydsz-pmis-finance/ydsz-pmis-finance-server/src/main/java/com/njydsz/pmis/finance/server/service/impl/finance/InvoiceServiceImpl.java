package com.njydsz.pmis.finance.server.service.impl.finance;

import com.njydsz.pmis.common.security.TenantContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.finance.domain.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.finance.domain.dto.InvoiceCreateDTO;
import com.njydsz.pmis.finance.domain.entity.InvoiceDO;
import com.njydsz.pmis.finance.domain.enums.InvoiceBasis;
import com.njydsz.pmis.finance.domain.enums.InvoiceStatus;
import com.njydsz.pmis.finance.domain.enums.InvoiceType;
import com.njydsz.pmis.finance.infra.mapper.InvoiceMapper;
import com.njydsz.pmis.finance.server.service.finance.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 发票服务实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    /** 发票 Mapper */
    private final InvoiceMapper invoiceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(InvoiceCreateDTO dto) {
        if (dto == null) throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_d9712a58");
        if (!StringUtils.hasText(dto.getInvoiceCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_0bf89391");
        }
        if (dto.getContractId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_af96cf73");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_abaef3a6");
        }
        if (InvoiceType.fromCode(dto.getInvoiceType()) == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_e77a5692", dto.getInvoiceType());
        }
        if (InvoiceBasis.fromCode(dto.getInvoiceBasis()) == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_a5324fa7", dto.getInvoiceBasis());
        }
        if (invoiceMapper.selectByCode(dto.getInvoiceCode()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_9c944632", dto.getInvoiceCode());
        }
        if (StringUtils.hasText(dto.getInvoiceNo())
                && invoiceMapper.selectByInvoiceNo(dto.getInvoiceNo()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY, "error.execution.msg_bef09851", dto.getInvoiceNo());
        }
        if ("RED_REVERSE".equalsIgnoreCase(dto.getInvoiceType())) {
            if (dto.getReversedById() == null) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_571d513d");
            }
            InvoiceDO src = invoiceMapper.selectById(dto.getReversedById());
            if (src == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_12b7e014");
            }
            if (!InvoiceStatus.ISSUED.getCode().equals(src.getStatus())) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_25f7c916");
            }
            if (dto.getAmount().compareTo(src.getAmount()) > 0) {
                throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_2d897570");
            }
        } else {
            // 正常开票：强制校验依据附件
            if ("MILESTONE".equalsIgnoreCase(dto.getInvoiceBasis())
                    || "FINAL".equalsIgnoreCase(dto.getInvoiceBasis())) {
                if (!StringUtils.hasText(dto.getAcceptanceProofId())) {
                    throw new SysException(StandardResultCode.BAD_REQUEST,
                            "error.execution.msg_ec948d12");
                }
            }
            if ("OUTSOURCING".equalsIgnoreCase(dto.getInvoiceBasis())) {
                if (!StringUtils.hasText(dto.getOutsourcingProofId())) {
                    throw new SysException(StandardResultCode.BAD_REQUEST,
                            "error.execution.msg_a89c0a16");
                }
            }
        }
        InvoiceDO inv = new InvoiceDO();
        BeanUtils.copyProperties(dto, inv);
        if (inv.getTaxRate() == null) inv.setTaxRate(new BigDecimal("0.06"));
        // 计算税额与不含税金额
        if (inv.getTaxAmount() == null || inv.getNetAmount() == null) {
            BigDecimal rate = inv.getTaxRate() == null ? BigDecimal.ZERO : inv.getTaxRate();
            BigDecimal amount = inv.getAmount();
            if (rate.signum() > 0) {
                // 价税分离：不含税 = 金额 / (1 + 税率)
                BigDecimal net = amount.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
                inv.setNetAmount(net);
                inv.setTaxAmount(amount.subtract(net));
            } else {
                inv.setNetAmount(amount);
                inv.setTaxAmount(BigDecimal.ZERO);
            }
        }
        if (inv.getStatus() == null) inv.setStatus(InvoiceStatus.DRAFT.getCode());
        if (inv.getCurrency() == null) inv.setCurrency("CNY");
        if (inv.getTenantId() == null) inv.setTenantId(TenantContext.getTenantId());
        if (inv.getProviderTraceId() == null) inv.setProviderTraceId("");
        invoiceMapper.insert(inv);
        log.info("[Invoice] 创建发票: code={} type={} basis={} amount={}",
                inv.getInvoiceCode(), inv.getInvoiceType(), inv.getInvoiceBasis(), inv.getAmount());
        return inv.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(String id, String operatorId) {
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.SUBMITTED, null, operatorId);
        if (inv.getAppliedBy() == null) {
            inv.setAppliedBy(operatorId);
            invoiceMapper.updateById(inv);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_52fbfb11");
        }
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.APPROVED, dto.getComment(), dto.getOperatorId());
        inv.setApprovedBy(dto.getOperatorId());
        inv.setApprovedAt(LocalDateTime.now());
        inv.setApprovalComment(dto.getComment());
        invoiceMapper.updateById(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_52fbfb11");
        }
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.REJECTED, dto.getComment(), dto.getOperatorId());
        inv.setApprovedBy(dto.getOperatorId());
        inv.setApprovedAt(LocalDateTime.now());
        inv.setApprovalComment(dto.getComment());
        invoiceMapper.updateById(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(String id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_69724bea");
        }
        InvoiceDO inv = getById(id);
        if (StringUtils.hasText(dto.getInvoiceNo())) {
            if (invoiceMapper.selectByInvoiceNo(dto.getInvoiceNo()) != null
                    && !dto.getInvoiceNo().equals(inv.getInvoiceNo())) {
                throw new SysException(StandardResultCode.DUPLICATE_KEY,
                        "error.execution.msg_67174829", dto.getInvoiceNo());
            }
            inv.setInvoiceNo(dto.getInvoiceNo());
        }
        if (!StringUtils.hasText(inv.getInvoiceNo())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_3ba9d565");
        }
        transit(inv, InvoiceStatus.ISSUED, dto.getComment(), dto.getOperatorId());
        inv.setIssuedBy(dto.getOperatorId());
        inv.setIssuedAt(LocalDateTime.now());
        invoiceMapper.updateById(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void redReverse(String id, String operatorId, String comment) {
        if (operatorId == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_2f7e744f");
        }
        InvoiceDO inv = getById(id);
        if (!"NORMAL".equalsIgnoreCase(inv.getInvoiceType())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_8f692e44");
        }
        transit(inv, InvoiceStatus.RED_REVERSED, comment, operatorId);
        // 同时把被红冲的原发票（蓝字发票）置为 RED_REVERSED
        if (inv.getReversedById() != null) {
            InvoiceDO origin = invoiceMapper.selectById(inv.getReversedById());
            if (origin != null) {
                transit(origin, InvoiceStatus.RED_REVERSED, comment, operatorId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, String operatorId, String comment) {
        if (operatorId == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.execution.msg_2f7e744f");
        }
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.CANCELLED, comment, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        InvoiceDO inv = getById(id);
        if (!InvoiceStatus.DRAFT.getCode().equals(inv.getStatus())
                && !InvoiceStatus.REJECTED.getCode().equals(inv.getStatus())
                && !InvoiceStatus.CANCELLED.getCode().equals(inv.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_dd7be833");
        }
        invoiceMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDO getById(String id) {
        InvoiceDO inv = invoiceMapper.selectById(id);
        if (inv == null) throw new SysException(StandardResultCode.NOT_FOUND, "error.execution.msg_1b0f0829");
        return inv;
    }

    @Override
    @DataScope(userColumn = "applied_by")
    @Transactional(readOnly = true)
    public Page<InvoiceDO> page(int page, int size, String keyword, String status,
                                String contractId, String initiationId, String customerId,
                                String invoiceType) {
        Page<InvoiceDO> p = new Page<>(page, size);
        LambdaQueryWrapper<InvoiceDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(InvoiceDO::getInvoiceCode, keyword)
                    .or().like(InvoiceDO::getInvoiceNo, keyword)
                    .or().like(InvoiceDO::getTitle, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(InvoiceDO::getStatus, status);
        if (StringUtils.hasText(invoiceType)) w.eq(InvoiceDO::getInvoiceType, invoiceType);
        if (contractId != null) w.eq(InvoiceDO::getContractId, contractId);
        if (initiationId != null) w.eq(InvoiceDO::getInitiationId, initiationId);
        if (customerId != null) w.eq(InvoiceDO::getCustomerId, customerId);
        // 数据权限 SQL 注入
        String ds = DataScopeHelper.buildSqlFragment("", "", "dept_id", "applied_by");
        if (!ds.isEmpty()) w.apply(ds);
        w.orderByDesc(InvoiceDO::getInvoiceDate, InvoiceDO::getId);
        return invoiceMapper.selectPage(p, w);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDO> listByContract(String contractId) {
        if (contractId == null) return List.of();
        return invoiceMapper.selectByContract(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDO> listByInitiation(String initiationId) {
        if (initiationId == null) return List.of();
        return invoiceMapper.selectByInitiation(initiationId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumInvoicedByContract(String contractId) {
        if (contractId == null) return BigDecimal.ZERO;
        BigDecimal v = invoiceMapper.sumInvoicedByContract(contractId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aggregateByStatus(String contractId) {
        if (contractId == null) return List.of();
        return invoiceMapper.aggregateByStatus(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> sumByMonth(String initiationId) {
        if (initiationId == null) return List.of();
        return invoiceMapper.sumByMonth(initiationId);
    }

    /**
     * 校验状态机迁移并持久化
     */
    private void transit(InvoiceDO inv, InvoiceStatus target, String comment, String operatorId) {
        InvoiceStatus from = InvoiceStatus.fromCode(inv.getStatus());
        if (from == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_2e33226a", inv.getStatus());
        }
        if (!from.canTransitTo(target)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.execution.msg_80c713df", from.getDesc(), target.getDesc());
        }
        invoiceMapper.updateStatus(inv.getId(), target.getCode(), operatorId, null);
        inv.setStatus(target.getCode());
        log.info("[Invoice] 状态迁移: id={} {} -> {}", inv.getId(), from.getCode(), target.getCode());
    }
}
