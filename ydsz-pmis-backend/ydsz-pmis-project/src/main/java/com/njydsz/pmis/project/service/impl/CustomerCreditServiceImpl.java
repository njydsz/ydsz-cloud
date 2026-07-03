package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.dto.CreditAssessmentDTO;
import com.njydsz.pmis.literule.calc.CreditScoreEvaluator;
import com.njydsz.pmis.project.entity.CustomerCreditDO;
import com.njydsz.pmis.project.entity.InvoiceDO;
import com.njydsz.pmis.project.entity.PaymentDO;
import com.njydsz.pmis.project.enums.CreditLevel;
import com.njydsz.pmis.project.enums.InvoiceStatus;
import com.njydsz.pmis.project.enums.PaymentStatus;
import com.njydsz.pmis.project.mapper.CustomerCreditMapper;
import com.njydsz.pmis.project.mapper.InvoiceMapper;
import com.njydsz.pmis.project.mapper.PaymentMapper;
import com.njydsz.pmis.project.service.CustomerCreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户信用服务实现
 *
 * <p>负责客户信用评估、信用档案查询、风险等级映射与信用分布统计。
 * 信用等级映射：A(90-100)/B(75-89)/C(60-74)/D(0-59)，新客户默认 30 基础分。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerCreditServiceImpl implements CustomerCreditService {

    private final CustomerCreditMapper creditMapper;
    private final InvoiceMapper invoiceMapper;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreditDO assess(CreditAssessmentDTO dto) {
        if (dto == null || dto.getCustomerId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "客户 ID 不能为空");
        }
        // 1) 累计合同/开票/回款金额
        List<InvoiceDO> invoices = invoiceMapper.selectByCustomer(dto.getCustomerId());
        List<PaymentDO> payments = paymentMapper.selectByCustomer(dto.getCustomerId());

        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        if (invoices != null) {
            for (InvoiceDO inv : invoices) {
                if (InvoiceStatus.ISSUED.getCode().equals(inv.getStatus())
                        && "NORMAL".equalsIgnoreCase(inv.getInvoiceType())) {
                    totalInvoiced = totalInvoiced.add(inv.getAmount() == null ? BigDecimal.ZERO : inv.getAmount());
                }
            }
        }
        if (payments != null) {
            for (PaymentDO p : payments) {
                if (PaymentStatus.CONFIRMED.getCode().equals(p.getStatus())
                        || PaymentStatus.ALLOCATED.getCode().equals(p.getStatus())) {
                    totalReceived = totalReceived.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
                }
            }
        }
        BigDecimal totalContract = totalInvoiced; // 简化：以开票为口径

        // 2) 及时率：amount == allocated 的比例
        int totalCount = 0;
        int onTimeCount = 0;
        int overdueCount = 0;
        if (payments != null) {
            for (PaymentDO p : payments) {
                if (PaymentStatus.CANCELLED.getCode().equals(p.getStatus())) continue;
                totalCount++;
                BigDecimal a = p.getAllocatedAmount() == null ? BigDecimal.ZERO : p.getAllocatedAmount();
                if (a.compareTo(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount()) >= 0) {
                    onTimeCount++;
                } else {
                    overdueCount++;
                }
            }
        }
        BigDecimal onTimeRate = totalCount == 0
                ? BigDecimal.ONE
                : new BigDecimal(onTimeCount).divide(new BigDecimal(totalCount), 4, RoundingMode.HALF_UP);

        int score = CreditScoreEvaluator.score(onTimeRate, totalContract, totalCount, overdueCount);
        CreditLevel level = CreditLevel.fromScore(score);

        // 3) 写入或更新
        CustomerCreditDO credit = creditMapper.selectByCustomerId(dto.getCustomerId());
        if (credit == null) {
            credit = new CustomerCreditDO();
            credit.setCustomerId(dto.getCustomerId());
            credit.setCustomerName(dto.getCustomerName());
            credit.setTenantId(1L);
            credit.setProviderTraceId("");
            creditMapper.insert(credit);
        }
        credit.setCreditLevel(level.getCode());
        credit.setCreditScore(score);
        credit.setTotalContractAmount(totalContract);
        credit.setTotalInvoicedAmount(totalInvoiced);
        credit.setTotalReceivedAmount(totalReceived);
        credit.setOnTimeRate(onTimeRate);
        credit.setContractCount(totalCount);
        credit.setOverdueCount(overdueCount);
        credit.setLastEvaluationAt(LocalDateTime.now());
        credit.setEvaluator(dto.getEvaluator() == null ? "SYSTEM" : dto.getEvaluator());
        if (StringUtils.hasText(dto.getCustomerName())) credit.setCustomerName(dto.getCustomerName());
        creditMapper.updateById(credit);

        log.info("[Credit] 评估客户: customerId={} score={} level={} onTimeRate={}",
                dto.getCustomerId(), score, level.getCode(), onTimeRate);
        return credit;
    }

    @Override
    public CustomerCreditDO getByCustomer(Long customerId) {
        if (customerId == null) return null;
        return creditMapper.selectByCustomerId(customerId);
    }

    @Override
    public List<CustomerCreditDO> listByLevel(CreditLevel level) {
        if (level == null) return List.of();
        return creditMapper.selectByLevel(level.getCode());
    }

    @Override
    public Map<String, Object> profile(Long customerId) {
        if (customerId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "客户 ID 不能为空");
        }
        CustomerCreditDO credit = getByCustomer(customerId);
        Map<String, Object> p = new HashMap<>();
        p.put("credit", credit);
        if (credit == null) {
            p.put("riskLevel", "UNKNOWN");
            return p;
        }
        String risk = switch (CreditLevel.fromCode(credit.getCreditLevel())) {
            case A -> "LOW";
            case B -> "LOW";
            case C -> "MEDIUM";
            case D -> "HIGH";
        };
        p.put("riskLevel", risk);
        return p;
    }

    @Override
    public List<Map<String, Object>> distribution() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CreditLevel l : CreditLevel.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("level", l.getCode());
            m.put("desc", l.getDesc());
            m.put("count", creditMapper.selectByLevel(l.getCode()).size());
            result.add(m);
        }
        return result;
    }

    @Override
    public Page<CustomerCreditDO> page(int page, int size, String keyword, String level) {
        Page<CustomerCreditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<CustomerCreditDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(CustomerCreditDO::getCustomerName, keyword));
        }
        if (StringUtils.hasText(level)) w.eq(CustomerCreditDO::getCreditLevel, level);
        w.orderByDesc(CustomerCreditDO::getCreditScore);
        return creditMapper.selectPage(p, w);
    }
}
