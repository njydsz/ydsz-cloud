package com.njydsz.pmis.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.project.engine.DecisionTableEvaluator;
import com.njydsz.pmis.project.entity.DecisionTableDO;
import com.njydsz.pmis.project.mapper.DecisionTableMapper;
import com.njydsz.pmis.project.service.DecisionTableEvalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表评估服务实现
 *
 * <p>按 tableCode 查询启用的决策表（取最新版本），委托 {@link DecisionTableEvaluator} 完成评估。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DecisionTableEvalServiceImpl implements DecisionTableEvalService {

    private final DecisionTableMapper decisionTableMapper;
    private final DecisionTableEvaluator decisionTableEvaluator;

    @Override
    public List<Map<String, Object>> evaluate(String tableCode, Map<String, Object> facts) {
        return evaluate(tableCode, facts, null);
    }

    @Override
    public List<Map<String, Object>> evaluate(String tableCode, Map<String, Object> facts, String tenantId) {
        if (tableCode == null || tableCode.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "决策表编码不能为空");
        }
        DecisionTableDO table = loadTable(tableCode, tenantId);
        log.info("[DMN] 评估决策表: tableCode={}, tenantId={}, factsKeys={}",
                tableCode, tenantId, facts == null ? "[]" : facts.keySet());
        return decisionTableEvaluator.evaluate(table, facts);
    }

    /**
     * 加载启用的决策表（按版本号倒序取最新一条）
     *
     * @param tableCode 决策表编码
     * @param tenantId  租户 ID（当前实体未启用租户隔离，仅记录日志）
     * @return 决策表实体
     * @throws BizException 决策表不存在或未启用
     */
    private DecisionTableDO loadTable(String tableCode, String tenantId) {
        LambdaQueryWrapper<DecisionTableDO> wrapper = new LambdaQueryWrapper<DecisionTableDO>()
                .eq(DecisionTableDO::getTableCode, tableCode)
                .eq(DecisionTableDO::getEnabled, Boolean.TRUE)
                .orderByDesc(DecisionTableDO::getVersion)
                .last("LIMIT 1");
        // tenantId 当前实体未直接持有，预留扩展（多租户场景可按需扩展查询条件）
        if (tenantId != null) {
            log.debug("[DMN] 租户 ID 已传入但决策表未启用租户隔离，忽略: tenantId={}", tenantId);
        }
        List<DecisionTableDO> tables = decisionTableMapper.selectList(wrapper);
        if (tables == null || tables.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "决策表不存在或未启用: " + tableCode);
        }
        return tables.get(0);
    }
}
