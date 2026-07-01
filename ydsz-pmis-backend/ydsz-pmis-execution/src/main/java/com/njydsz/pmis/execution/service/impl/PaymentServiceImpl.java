package com.njydsz.pmis.execution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.execution.dto.PaymentAllocationDTO;
import com.njydsz.pmis.execution.dto.PaymentCreateDTO;
import com.njydsz.pmis.execution.entity.InvoiceDO;
import com.njydsz.pmis.execution.entity.PaymentDO;
import com.njydsz.pmis.execution.enums.InvoiceStatus;
import com.njydsz.pmis.execution.enums.PaymentStatus;
import com.njydsz.pmis.execution.mapper.InvoiceMapper;
import com.njydsz.pmis.execution.mapper.PaymentMapper;
import com.njydsz.pmis.execution.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final InvoiceMapper invoiceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long record(PaymentCreateDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (!StringUtils.hasText(dto.getPaymentCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "回款编号不能为空");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "回款金额必须为正数");
        }
        if (dto.getPaymentDate() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "到账日期不能为空");
        }
        if (paymentMapper.selectByCode(dto.getPaymentCode()) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "回款编号已存在: " + dto.getPaymentCode());
        }
        PaymentDO p = new PaymentDO();
        BeanUtils.copyProperties(dto, p);
        if (p.getStatus() == null) p.setStatus(PaymentStatus.PENDING.getCode());
        if (p.getCurrency() == null) p.setCurrency("CNY");
        if (p.getPaymentMethod() == null) p.setPaymentMethod("BANK_TRANSFER");
        if (p.getTenantId() == null) p.setTenantId(1L);
        if (p.getProviderTraceId() == null) p.setProviderTraceId("");

        BigDecimal allocated = p.getAllocatedAmount() == null ? BigDecimal.ZERO : p.getAllocatedAmount();
        if (allocated.signum() > 0) {
            if (allocated.compareTo(p.getAmount()) > 0) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "已分配金额不能超过回款金额");
            }
            p.setUnallocatedAmount(p.getAmount().subtract(allocated));
        } else {
            p.setUnallocatedAmount(p.getAmount());
            p.setAllocatedAmount(BigDecimal.ZERO);
        }
        paymentMapper.insert(p);
        log.info("[Payment] 录入回款: code={} amount={}", p.getPaymentCode(), p.getAmount());
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Long operatorId) {
        PaymentDO p = getById(id);
        transit(p, PaymentStatus.CONFIRMED, operatorId);
        p.setConfirmedBy(operatorId);
        p.setConfirmedAt(LocalDateTime.now());
        paymentMapper.updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long operatorId, String reason) {
        PaymentDO p = getById(id);
        if (p.getAllocatedAmount() != null && p.getAllocatedAmount().signum() > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已核销的回款不能取消");
        }
        transit(p, PaymentStatus.CANCELLED, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PaymentDO p = getById(id);
        if (PaymentStatus.ALLOCATED.getCode().equals(p.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "已核销回款不能删除");
        }
        paymentMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocate(PaymentAllocationDTO dto) {
        if (dto == null) throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "核销金额必须为正数");
        }
        PaymentDO p = getById(dto.getPaymentId());
        if (!PaymentStatus.CONFIRMED.getCode().equals(p.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅 CONFIRMED 状态可核销");
        }
        InvoiceDO inv = invoiceMapper.selectById(dto.getInvoiceId());
        if (inv == null) throw new BizException(BizErrorCode.NOT_FOUND, "发票不存在");
        if (!InvoiceStatus.ISSUED.getCode().equals(inv.getStatus())
                && !InvoiceStatus.APPROVED.getCode().equals(inv.getStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅 APPROVED/ISSUED 发票可被核销");
        }
        BigDecimal remain = p.getUnallocatedAmount() == null
                ? p.getAmount().subtract(p.getAllocatedAmount() == null ? BigDecimal.ZERO : p.getAllocatedAmount())
                : p.getUnallocatedAmount();
        if (dto.getAmount().compareTo(remain) > 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "核销金额(" + dto.getAmount() + ")大于未分配金额(" + remain + ")");
        }
        String existing = p.getInvoiceAllocation();
        String updated = (existing == null || existing.isBlank())
                ? String.valueOf(dto.getInvoiceId())
                : existing + "," + dto.getInvoiceId();
        BigDecimal newAllocated = p.getAllocatedAmount().add(dto.getAmount());
        BigDecimal newUnalloc = p.getAmount().subtract(newAllocated);
        paymentMapper.updateAllocation(p.getId(), updated, newAllocated, newUnalloc);
        p.setInvoiceAllocation(updated);
        p.setAllocatedAmount(newAllocated);
        p.setUnallocatedAmount(newUnalloc);

        if (newUnalloc.signum() == 0) {
            transit(p, PaymentStatus.ALLOCATED, dto.getOperatorId());
        }
        log.info("[Payment] 核销: paymentId={} invoiceId={} amount={}",
                p.getId(), dto.getInvoiceId(), dto.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoAllocate(Long customerId, Long operatorId) {
        if (customerId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "客户 ID 不能为空");
        }
        List<PaymentDO> pool = paymentMapper.selectUnallocated(customerId);
        if (pool == null || pool.isEmpty()) return 0;
        // 取出客户所有 ISSUED/APPROVED 发票，按开票日期升序
        List<InvoiceDO> invoices = invoiceMapper.selectByCustomer(customerId);
        if (invoices == null || invoices.isEmpty()) return 0;
        invoices = invoices.stream()
                .filter(i -> InvoiceStatus.ISSUED.getCode().equals(i.getStatus())
                        || InvoiceStatus.APPROVED.getCode().equals(i.getStatus()))
                .filter(i -> "NORMAL".equalsIgnoreCase(i.getInvoiceType()))
                .sorted((a, b) -> a.getInvoiceDate().compareTo(b.getInvoiceDate()))
                .toList();
        if (invoices.isEmpty()) return 0;

        // 跟踪每张发票已核销金额（基于现有 payment.invoice_allocation）
        Map<Long, BigDecimal> invoiceAllocated = new HashMap<>();
        for (InvoiceDO inv : invoices) {
            invoiceAllocated.put(inv.getId(), BigDecimal.ZERO);
        }
        List<PaymentDO> allPayments = paymentMapper.selectByCustomer(customerId);
        for (PaymentDO pay : allPayments) {
            if (pay.getInvoiceAllocation() == null) continue;
            String[] ids = pay.getInvoiceAllocation().split(",");
            // 简化：等额分配到每张已分配的发票
            int cnt = ids.length;
            if (cnt == 0) continue;
            BigDecimal each = pay.getAllocatedAmount() == null
                    ? BigDecimal.ZERO
                    : pay.getAllocatedAmount().divide(new BigDecimal(cnt), 2, RoundingMode.HALF_UP);
            for (String idStr : ids) {
                try {
                    Long iid = Long.parseLong(idStr.trim());
                    invoiceAllocated.merge(iid, each, BigDecimal::add);
                } catch (NumberFormatException ignore) { }
            }
        }

        int count = 0;
        for (PaymentDO p : pool) {
            BigDecimal remain = p.getUnallocatedAmount();
            for (InvoiceDO inv : invoices) {
                if (remain.signum() <= 0) break;
                BigDecimal already = invoiceAllocated.getOrDefault(inv.getId(), BigDecimal.ZERO);
                BigDecimal invoiceRemain = inv.getAmount().subtract(already);
                if (invoiceRemain.signum() <= 0) continue;
                BigDecimal take = remain.min(invoiceRemain);
                PaymentAllocationDTO all = new PaymentAllocationDTO();
                all.setPaymentId(p.getId());
                all.setInvoiceId(inv.getId());
                all.setAmount(take);
                all.setOperatorId(operatorId);
                allocate(all);
                invoiceAllocated.merge(inv.getId(), take, BigDecimal::add);
                remain = remain.subtract(take);
                count++;
            }
        }
        return count;
    }

    @Override
    public List<Map<String, Object>> forecastCashFlow(Long initiationId, int months) {
        if (initiationId == null) return List.of();
        if (months <= 0) months = 3;
        if (months > 12) months = 12;
        // 1) 历史按月回款均值
        List<Map<String, Object>> history = paymentMapper.aggregateByMonth(initiationId);
        BigDecimal avg = BigDecimal.ZERO;
        if (history != null && !history.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> h : history) {
                Object amt = h.get("amount");
                if (amt != null) total = total.add(new BigDecimal(amt.toString()));
            }
            avg = total.divide(new BigDecimal(history.size()), 2, RoundingMode.HALF_UP);
        }
        // 2) 应收余额
        List<InvoiceDO> invoices = invoiceMapper.selectByInitiation(initiationId);
        BigDecimal receivable = BigDecimal.ZERO;
        if (invoices != null) {
            for (InvoiceDO inv : invoices) {
                if (InvoiceStatus.ISSUED.getCode().equals(inv.getStatus())
                        && "NORMAL".equalsIgnoreCase(inv.getInvoiceType())) {
                    receivable = receivable.add(inv.getAmount());
                }
            }
        }
        BigDecimal already = paymentMapper.sumReceivedByContract(null);
        if (already == null) already = BigDecimal.ZERO;

        // 3) 简单线性预测：未来 N 个月每月 = min(均值, 应收余额剩余/N)
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate base = LocalDate.now().withDayOfMonth(1);
        BigDecimal perMonth = history != null && !history.isEmpty()
                ? avg
                : receivable.divide(new BigDecimal(months), 2, RoundingMode.HALF_UP);
        for (int i = 1; i <= months; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("month", base.plusMonths(i).toString().substring(0, 7));
            m.put("forecastAmount", perMonth);
            result.add(m);
        }
        return result;
    }

    @Override
    public PaymentDO getById(Long id) {
        PaymentDO p = paymentMapper.selectById(id);
        if (p == null) throw new BizException(BizErrorCode.NOT_FOUND, "回款记录不存在");
        return p;
    }

    @Override
    public Page<PaymentDO> page(int page, int size, String keyword, String status,
                                Long contractId, Long customerId, Long initiationId) {
        Page<PaymentDO> p = new Page<>(page, size);
        LambdaQueryWrapper<PaymentDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(PaymentDO::getPaymentCode, keyword)
                    .or().like(PaymentDO::getPaymentNo, keyword)
                    .or().like(PaymentDO::getBankReference, keyword));
        }
        if (StringUtils.hasText(status)) w.eq(PaymentDO::getStatus, status);
        if (contractId != null) w.eq(PaymentDO::getContractId, contractId);
        if (customerId != null) w.eq(PaymentDO::getCustomerId, customerId);
        if (initiationId != null) w.eq(PaymentDO::getInitiationId, initiationId);
        w.orderByDesc(PaymentDO::getPaymentDate, PaymentDO::getId);
        return paymentMapper.selectPage(p, w);
    }

    @Override
    public BigDecimal sumReceivedByContract(Long contractId) {
        if (contractId == null) return BigDecimal.ZERO;
        BigDecimal v = paymentMapper.sumReceivedByContract(contractId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Override
    public List<Map<String, Object>> aggregateByMonth(Long initiationId) {
        if (initiationId == null) return List.of();
        return paymentMapper.aggregateByMonth(initiationId);
    }

    @Override
    public List<Map<String, Object>> aggregateByCustomer() {
        return paymentMapper.aggregateByCustomer();
    }

    private void transit(PaymentDO p, PaymentStatus target, Long operatorId) {
        PaymentStatus from = PaymentStatus.fromCode(p.getStatus());
        if (from == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "当前状态非法: " + p.getStatus());
        }
        if (!from.canTransitTo(target)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "回款状态不允许从 " + from.getDesc() + " 迁移到 " + target.getDesc());
        }
        paymentMapper.updateStatus(p.getId(), target.getCode(), operatorId);
        p.setStatus(target.getCode());
        log.info("[Payment] 状态迁移: id={} {} -> {}", p.getId(), from.getCode(), target.getCode());
    }
}
