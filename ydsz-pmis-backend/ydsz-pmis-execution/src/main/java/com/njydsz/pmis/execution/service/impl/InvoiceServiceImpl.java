package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.InvoiceApprovalDTO;
import com.njydsz.pmis.execution.dto.InvoiceCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.enums.InvoiceBasis;
import com.njydsz.pmis.execution.enums.InvoiceStatus;
import com.njydsz.pmis.execution.enums.InvoiceType;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.service.InvoiceService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceMapper invoiceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InvoiceCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getInvoiceCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "发票编号不能为空");
        }
        if (dto.getContractId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "合同 ID 不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "发票金额必须为正数");
        }
        if (InvoiceType.fromCode(dto.getInvoiceType()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "发票类型非法: " + dto.getInvoiceType());
        }
        if (InvoiceBasis.fromCode(dto.getInvoiceBasis()) == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "开票依据非法: " + dto.getInvoiceBasis());
        }
        if (invoiceMapper.selectByCode(dto.getInvoiceCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "发票编号已存在: " + dto.getInvoiceCode());
        }
        if (StringUtils.hasText(dto.getInvoiceNo())
                && invoiceMapper.selectByInvoiceNo(dto.getInvoiceNo()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY, "发票号已存在: " + dto.getInvoiceNo());
        }
        if ("RED_REVERSE".equalsIgnoreCase(dto.getInvoiceType())) {
            if (dto.getReversedById() == null) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "红冲发票必须指定被红冲的发票 ID");
            }
            InvoiceDO src = invoiceMapper.selectById(dto.getReversedById());
            if (src == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "被红冲的发票不存在");
            }
            if (!InvoiceStatus.ISSUED.getCode().equals(src.getStatus())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "仅已开具(ISSUED)的发票可被红冲");
            }
            if (dto.getAmount().compareTo(src.getAmount()) > 0) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "红冲金额不能大于原发票金额");
            }
        } else {
            // 正常开票：强制校验依据附件
            if ("MILESTONE".equalsIgnoreCase(dto.getInvoiceBasis())
                    || "FINAL".equalsIgnoreCase(dto.getInvoiceBasis())) {
                if (!StringUtils.hasText(dto.getAcceptanceProofId())) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "里程碑/终验开票必须上传验收报告附件(acceptanceProofId)");
                }
            }
            if ("OUTSOURCING".equalsIgnoreCase(dto.getInvoiceBasis())) {
                if (!StringUtils.hasText(dto.getOutsourcingProofId())) {
                    throw new BizException(BizErrorCode.BAD_REQUEST,
                            "人力外包开票必须上传客户确认人天单(outsourcingProofId)");
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
        if (inv.getTenantId() == null) inv.setTenantId(1L);
        if (inv.getProviderTraceId() == null) inv.setProviderTraceId("");
        invoiceMapper.insert(inv);
        log.info("[Invoice] 创建发票: code={} type={} basis={} amount={}",
                inv.getInvoiceCode(), inv.getInvoiceType(), inv.getInvoiceBasis(), inv.getAmount());
        return inv.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long operatorId) {
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.SUBMITTED, null, operatorId);
        if (inv.getAppliedBy() == null) {
            inv.setAppliedBy(operatorId);
            invoiceMapper.updateById(inv);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "审批人不能为空");
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
    public void reject(Long id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "审批人不能为空");
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
    public void issue(Long id, InvoiceApprovalDTO dto) {
        if (dto == null || dto.getOperatorId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "开票人不能为空");
        }
        InvoiceDO inv = getById(id);
        if (StringUtils.hasText(dto.getInvoiceNo())) {
            if (invoiceMapper.selectByInvoiceNo(dto.getInvoiceNo()) != null
                    && !dto.getInvoiceNo().equals(inv.getInvoiceNo())) {
                throw new BizException(BizErrorCode.DUPLICATE_KEY,
                        "发票号已被使用: " + dto.getInvoiceNo());
            }
            inv.setInvoiceNo(dto.getInvoiceNo());
        }
        if (!StringUtils.hasText(inv.getInvoiceNo())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "开具时必须录入发票号(invoiceNo)");
        }
        transit(inv, InvoiceStatus.ISSUED, dto.getComment(), dto.getOperatorId());
        inv.setIssuedBy(dto.getOperatorId());
        inv.setIssuedAt(LocalDateTime.now());
        invoiceMapper.updateById(inv);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void redReverse(Long id, Long operatorId, String comment) {
        if (operatorId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "操作人不能为空");
        }
        InvoiceDO inv = getById(id);
        if (!"NORMAL".equalsIgnoreCase(inv.getInvoiceType())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅 NORMAL 发票可被红冲");
        }
        transit(inv, InvoiceStatus.RED_REVERSED, comment, operatorId);
        // 同时把被红冲的原发票置为 RED_REVERSED
        InvoiceDO origin = invoiceMapper.selectById(id);
        if (origin != null) {
            transit(origin, InvoiceStatus.RED_REVERSED, comment, operatorId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long operatorId, String comment) {
        if (operatorId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "操作人不能为空");
        }
        InvoiceDO inv = getById(id);
        transit(inv, InvoiceStatus.CANCELLED, comment, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InvoiceDO inv = getById(id);
        if (!InvoiceStatus.DRAFT.getCode().equals(inv.getStatus())
                && !InvoiceStatus.REJECTED.getCode().equals(inv.getStatus())
                && !InvoiceStatus.CANCELLED.getCode().equals(inv.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "仅 DRAFT/REJECTED/CANCELLED 状态可删除");
        }
        invoiceMapper.deleteById(id);
    }

    @Override
    public InvoiceDO getById(Long id) {
        InvoiceDO inv = invoiceMapper.selectById(id);
        if (inv == null) throw new BizException(BizErrorCode.NOT_FOUND, "发票不存在");
        return inv;
    }

    @Override
    public Page<InvoiceDO> page(int page, int size, String keyword, String status,
                                Long contractId, Long initiationId, Long customerId,
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
        w.orderByDesc(InvoiceDO::getInvoiceDate, InvoiceDO::getId);
        return invoiceMapper.selectPage(p, w);
    }

    @Override
    public List<InvoiceDO> listByContract(Long contractId) {
        if (contractId == null) return List.of();
        return invoiceMapper.selectByContract(contractId);
    }

    @Override
    public List<InvoiceDO> listByInitiation(Long initiationId) {
        if (initiationId == null) return List.of();
        return invoiceMapper.selectByInitiation(initiationId);
    }

    @Override
    public BigDecimal sumInvoicedByContract(Long contractId) {
        if (contractId == null) return BigDecimal.ZERO;
        BigDecimal v = invoiceMapper.sumInvoicedByContract(contractId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<Map<String, Object>> aggregateByStatus(Long contractId) {
        if (contractId == null) return List.of();
        return invoiceMapper.aggregateByStatus(contractId);
    }

    @Override
    public List<Map<String, Object>> sumByMonth(Long initiationId) {
        if (initiationId == null) return List.of();
        return invoiceMapper.sumByMonth(initiationId);
    }

    /**
     * 校验状态机迁移并持久化
     */
    private void transit(InvoiceDO inv, InvoiceStatus target, String comment, Long operatorId) {
        InvoiceStatus from = InvoiceStatus.fromCode(inv.getStatus());
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "当前状态非法: " + inv.getStatus());
        }
        if (!from.canTransitTo(target)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "发票状态不允许从 " + from.getDesc() + " 迁移到 " + target.getDesc());
        }
        invoiceMapper.updateStatus(inv.getId(), target.getCode(), operatorId, null);
        inv.setStatus(target.getCode());
        log.info("[Invoice] 状态迁移: id={} {} -> {}", inv.getId(), from.getCode(), target.getCode());
    }
}
