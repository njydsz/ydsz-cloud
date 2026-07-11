package com.njydsz.pmis.workflow.server.service.impl.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import com.njydsz.pmis.workflow.domain.enums.ai.CanaryStatus;
import com.njydsz.pmis.workflow.domain.enums.ai.CanaryStrategy;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.server.service.ai.FlowCanaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度发布服务实现
 *
 * <p>P3-1 落地。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCanaryServiceImpl implements FlowCanaryService {

    /** 流程定义 Mapper，用于读取/更新灰度发布状态、灰度比例及切流日志 */
    private final FlowDefinitionMapper definitionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCanary(String definitionId, int initialPercent, String strategy,
                               String operatorId, String operatorName, String note) {
        validatePercent(initialPercent);
        FlowDefinitionDO def = mustGetDef(definitionId);
        if (def.getIsPublish() == null || def.getIsPublish() != 1) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_5bdc1fe3");
        }
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (CanaryStatus.PROMOTED.name().equals(curStatus)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_9ff06760");
        }

        int oldPercent = def.getCanaryPercent() == null ? 0 : def.getCanaryPercent();
        def.setCanaryPercent(initialPercent);
        def.setCanaryStrategy(StringUtils.hasText(strategy) ? strategy : CanaryStrategy.USER_HASH.name());
        def.setCanaryStatus(CanaryStatus.CANARYING.name());
        appendRolloutLog(def, operatorId, operatorName, oldPercent, initialPercent, note);
        definitionMapper.updateById(def);

        log.info("[Flow][Canary] 启动灰度: defId={} code={} version={} percent={}% strategy={} operator={}",
                definitionId, def.getFlowCode(), def.getFlowVersion(), initialPercent,
                def.getCanaryStrategy(), operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustCanaryPercent(String definitionId, int newPercent,
                                    String operatorId, String operatorName, String note) {
        validatePercent(newPercent);
        FlowDefinitionDO def = mustGetDef(definitionId);
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (!CanaryStatus.CANARYING.name().equals(curStatus)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_f5374e71", curStatus);
        }
        int oldPercent = def.getCanaryPercent() == null ? 0 : def.getCanaryPercent();
        if (oldPercent == newPercent) {
            log.info("[Flow][Canary] 调整比例无变化，跳过: defId={} percent={}", definitionId, newPercent);
            return;
        }
        def.setCanaryPercent(newPercent);
        appendRolloutLog(def, operatorId, operatorName, oldPercent, newPercent, note);
        definitionMapper.updateById(def);

        log.info("[Flow][Canary] 调整灰度比例: defId={} code={} {}% → {}% operator={}",
                definitionId, def.getFlowCode(), oldPercent, newPercent, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promoteCanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinitionDO def = mustGetDef(definitionId);
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (CanaryStatus.PROMOTED.name().equals(curStatus)) {
            log.info("[Flow][Canary] 定义已全量发布，跳过: defId={}", definitionId);
            return;
        }

        // 1) 失效同 flowCode 的其他已发布版本
        String tenantId = def.getTenantId() != null
                ? def.getTenantId() : SecurityContext.getTenantIdOrDefault("1");
        definitionMapper.deactivateByFlowCode(def.getFlowCode(), definitionId, tenantId);

        // 2) 当前定义晋升为稳定版（isPublish=1, canaryPercent=100, canaryStatus=PROMOTED）
        int oldPercent = def.getCanaryPercent() == null ? 0 : def.getCanaryPercent();
        def.setCanaryPercent(100);
        def.setCanaryStatus(CanaryStatus.PROMOTED.name());
        appendRolloutLog(def, operatorId, operatorName, oldPercent, 100,
                note == null ? "全量发布" : note);
        definitionMapper.updateById(def);

        log.info("[Flow][Canary] 全量发布完成: defId={} code={} version={} operator={}",
                definitionId, def.getFlowCode(), def.getFlowVersion(), operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinitionDO def = mustGetDef(definitionId);
        int oldPercent = def.getCanaryPercent() == null ? 0 : def.getCanaryPercent();
        def.setCanaryPercent(0);
        def.setCanaryStatus(CanaryStatus.ROLLED_BACK.name());
        // 失效当前灰度版：isPublish=9
        def.setIsPublish(9);
        appendRolloutLog(def, operatorId, operatorName, oldPercent, 0,
                note == null ? "灰度回滚" : note);
        definitionMapper.updateById(def);

        log.info("[Flow][Canary] 灰度回滚: defId={} code={} version={} operator={} reason={}",
                definitionId, def.getFlowCode(), def.getFlowVersion(), operatorName, note);
    }

    @Override
    @Transactional(readOnly = true)
    public FlowDefinitionDO resolveEffectiveDefinition(String flowCode, String version,
                                                       String tenantId, String initiatorId) {
        // 1) 先查稳定版（isPublish=1 且 canaryStatus != CANARYING 的最新已发布）
        FlowDefinitionDO stable = definitionMapper.selectPublished(
                flowCode,
                StringUtils.hasText(version) ? version : "1.0",
                tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1"));
        if (stable == null) {
            return null;
        }

        // 2) 查同 flowCode + tenant 的所有 CANARYING 灰度版（按 version desc 取最新）
        List<FlowDefinitionDO> canaries = definitionMapper.selectCanaryingByCode(
                flowCode,
                tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1"));
        if (canaries == null || canaries.isEmpty()) {
            return stable;
        }
        FlowDefinitionDO canary = canaries.get(0);
        int percent = canary.getCanaryPercent() == null ? 0 : canary.getCanaryPercent();
        if (percent <= 0 || percent >= 100) {
            return stable;
        }

        // 3) 按策略切流
        if (shouldUseCanary(canary, percent, initiatorId)) {
            log.debug("[Flow][Canary] 切流至灰度版: code={} version={} percent={}% initiator={}",
                    flowCode, canary.getFlowVersion(), percent, initiatorId);
            return canary;
        }
        return stable;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCanaryRolloutLog(String flowCode, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            return Collections.emptyList();
        }
        String tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault("1");
        List<FlowDefinitionDO> defs = definitionMapper.selectByFlowCode(flowCode, tid);
        if (defs == null || defs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowDefinitionDO d : defs) {
            if (!StringUtils.hasText(d.getCanaryRolloutLog())) {
                continue;
            }
            JSONArray arr;
            try {
                arr = JSON.parseArray(d.getCanaryRolloutLog());
            } catch (Exception ex) {
                log.warn("[Flow][Canary] 解析 rollout_log 失败: defId={} raw={}", d.getId(), d.getCanaryRolloutLog());
                continue;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, Object> m = new HashMap<>();
                m.put("definitionId", d.getId());
                m.put("flowCode", d.getFlowCode());
                m.put("version", d.getFlowVersion());
                m.put("operatorId", o.get("operatorId"));
                m.put("operatorName", o.get("operatorName"));
                m.put("fromPercent", o.get("fromPercent"));
                m.put("toPercent", o.get("toPercent"));
                m.put("operateAt", o.get("operateAt"));
                m.put("note", o.get("note"));
                out.add(m);
            }
        }
        return out;
    }

    // ============================== 私有辅助 ==============================

    /** 校验 percent 取值 */
    private void validatePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.workflow.msg_a9bb9120", percent);
        }
    }

    /**
     * 根据定义 ID 查询流程定义，不存在时抛出 NOT_FOUND 异常。
     *
     * @param definitionId 流程定义 ID
     * @return 非空的流程定义实体
     */
    private FlowDefinitionDO mustGetDef(String definitionId) {
        if (definitionId == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_375a4677");
        }
        FlowDefinitionDO def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_690c83d8", definitionId);
        }
        return def;
    }

    /** 追加一条 rollout log 记录 */
    private void appendRolloutLog(FlowDefinitionDO def, String operatorId, String operatorName,
                                   int fromPercent, int toPercent, String note) {
        JSONArray arr;
        if (StringUtils.hasText(def.getCanaryRolloutLog())) {
            try {
                arr = JSON.parseArray(def.getCanaryRolloutLog());
            } catch (Exception ex) {
                log.warn("[Flow][Canary] 解析 rollout_log 失败，重置为空: defId={}", def.getId());
                arr = new JSONArray();
            }
        } else {
            arr = new JSONArray();
        }
        JSONObject o = new JSONObject();
        o.put("operatorId", operatorId);
        o.put("operatorName", operatorName);
        o.put("fromPercent", fromPercent);
        o.put("toPercent", toPercent);
        o.put("operateAt", LocalDateTime.now().toString());
        o.put("note", note);
        arr.add(o);
        def.setCanaryRolloutLog(JSON.toJSONString(arr));
    }

    /**
     * 切流判断：是否使用灰度版
     *
     * <p>USER_HASH：abs(initiatorId) % 100 < percent（一致性）
     * <br>RANDOM：ThreadLocalRandom.nextInt(100) < percent
     * <br>WHITELIST：始终走灰度（白名单在调用方过滤，这里简化认为配置了白名单就走灰度）
     */
    private boolean shouldUseCanary(FlowDefinitionDO canary, int percent, String initiatorId) {
        String strategy = canary.getCanaryStrategy();
        if (strategy == null) {
            strategy = CanaryStrategy.USER_HASH.name();
        }
        if (CanaryStrategy.RANDOM.name().equalsIgnoreCase(strategy)) {
            return ThreadLocalRandom.current().nextInt(100) < percent;
        }
        if (CanaryStrategy.WHITELIST.name().equalsIgnoreCase(strategy)) {
            return true;
        }
        // USER_HASH（默认）
        if (initiatorId == null) {
            // 无发起人：默认走稳定版，避免脏数据
            return false;
        }
        return Math.abs(initiatorId.hashCode() % 100) < percent;
    }
}
