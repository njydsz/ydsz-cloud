package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.CreditAssessmentDTO;
import com.njydsz.pmis.execution.entity.CustomerCreditDO;
import com.njydsz.pmis.execution.enums.CreditLevel;

import java.util.List;
import java.util.Map;

/**
 * 客户信用服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface CustomerCreditService {

    /**
     * 评估客户信用
     *
     * <p>评分维度：回款及时率 60pts + 合同规模 25pts + 合作次数 15pts
     */
    CustomerCreditDO assess(CreditAssessmentDTO dto);

    /**
     * 按客户获取信用记录
     */
    CustomerCreditDO getByCustomer(Long customerId);

    /**
     * 按等级列出
     */
    List<CustomerCreditDO> listByLevel(CreditLevel level);

    /**
     * 客户风险画像（用于资源推荐/合同评审）
     */
    Map<String, Object> profile(Long customerId);

    /**
     * 信用分布统计（A/B/C/D 各多少客户）
     */
    List<Map<String, Object>> distribution();

    /**
     * 分页查询
     */
    Page<CustomerCreditDO> page(int page, int size, String keyword, String level);
}
