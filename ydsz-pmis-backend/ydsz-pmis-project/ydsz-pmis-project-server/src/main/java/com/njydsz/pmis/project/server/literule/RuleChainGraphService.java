package com.njydsz.pmis.project.server.literule;

import java.time.LocalDateTime;

import com.njydsz.pmis.common.util.json.JsonUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.literule.domain.entity.RuleChainGraphDO;
import com.njydsz.pmis.literule.infra.mapper.RuleChainGraphMapper;
import com.njydsz.pmis.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.pmis.literule.server.spi.RuleChainGraphProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则链画布 Service（P0-1）
 *
 * <p>提供画布的 CRUD 与持久化能力，画布内容以 JSON 形式存储到 pmis_rule_chain_graph 表。
 *
 * <p>实现 {@link RuleChainGraphProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleChainGraphService implements RuleChainGraphProvider {

    private final RuleChainGraphMapper ruleChainGraphMapper;

    /**
     * 查询指定规则的画布
     *
     * @param ruleCode 规则编码
     * @return 画布；不存在返回 null
     */
    public RuleChainGraph getByRuleCode(String ruleCode) {
        if (ruleCode == null) return null;
        RuleChainGraphDO DO = ruleChainGraphMapper.selectByRuleCode(ruleCode);
        return DO == null ? null : toGraph(DO);
    }

    /**
     * 保存或更新画布
     *
     * <p>已存在画布则版本号 +1 并更新内容；不存在则新建。
     *
     * @param ruleCode 规则编码
     * @param graph    画布
     * @param operator 操作人
     * @return 保存后的画布
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleChainGraph save(String ruleCode, RuleChainGraph graph, String operator) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        if (graph == null) {
            throw new IllegalArgumentException("graph 不能为空");
        }
        graph.setRuleCode(ruleCode);
        RuleChainGraphDO existing = ruleChainGraphMapper.selectByRuleCode(ruleCode);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            RuleChainGraphDO DO = new RuleChainGraphDO();
            DO.setRuleCode(ruleCode);
            DO.setName(graph.getName() != null ? graph.getName() : ruleCode);
            DO.setDescription(graph.getDescription());
            DO.setScenario(graph.getScenario());
            DO.setTenantId(graph.getTenantId() != null ? graph.getTenantId() : "1");
            DO.setGraphVersion(1);
            DO.setStatus(graph.getStatus() != null ? graph.getStatus() : "DRAFT");
            DO.setContentJson(JsonUtils.toJson(graph));
            DO.setCreatedBy(operator);
            DO.setCreatedAt(now);
            DO.setUpdatedBy(operator);
            DO.setUpdatedAt(now);
            ruleChainGraphMapper.insert(DO);
            log.info("[RuleChainGraph] 画布新建: ruleCode={}, operator={}", ruleCode, operator);
            return toGraph(DO);
        }
        existing.setName(graph.getName() != null ? graph.getName() : existing.getName());
        existing.setDescription(graph.getDescription());
        existing.setScenario(graph.getScenario());
        existing.setStatus(graph.getStatus() != null ? graph.getStatus() : existing.getStatus());
        existing.setContentJson(JsonUtils.toJson(graph));
        existing.setGraphVersion((existing.getGraphVersion() == null ? 1 : existing.getGraphVersion()) + 1);
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(now);
        ruleChainGraphMapper.updateById(existing);
        log.info("[RuleChainGraph] 画布更新: ruleCode={}, version={}, operator={}",
                ruleCode, existing.getGraphVersion(), operator);
        return toGraph(existing);
    }

    /**
     * 删除画布
     *
     * @param ruleCode 规则编码
     * @return true=有删除，false=无记录
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String ruleCode) {
        if (ruleCode == null) return false;
        int rows = ruleChainGraphMapper.deleteByRuleCode(ruleCode);
        if (rows > 0) {
            log.info("[RuleChainGraph] 画布删除: ruleCode={}", ruleCode);
        }
        return rows > 0;
    }

    /**
     * DO → API Graph
     */
    private RuleChainGraph toGraph(RuleChainGraphDO DO) {
        try {
            return JsonUtils.parseMap(DO.getContentJson(), RuleChainGraph.class);
        } catch (Exception e) {
            log.warn("[RuleChainGraph] 画布 JSON 解析失败: ruleCode={}, err={}",
                    DO.getRuleCode(), e.getMessage());
            return null;
        }
    }
}
