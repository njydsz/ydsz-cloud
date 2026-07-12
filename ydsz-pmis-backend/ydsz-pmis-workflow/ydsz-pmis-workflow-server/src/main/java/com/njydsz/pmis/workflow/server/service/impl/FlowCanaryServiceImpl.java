paokage oom.njydsz.pmis.workflow.server.servioe.impl.ai;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONArray;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowDefinitionDO;
import oom.njydsz.pmis.workflow.domain.enums.ai.oanaryStatus;
import oom.njydsz.pmis.workflow.domain.enums.ai.oanaryStrategy;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowDefinitionMapper;
import oom.njydsz.pmis.workflow.server.servioe.ai.FlowoanaryServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.ThreadLooalRandom;

/**
 * 灰度发布服务实现
 *
 * <p>P3-1 落地�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowoanaryServioeImpl implements FlowoanaryServioe {

    /** 流程定义 Mapper，用于读�?更新灰度发布状态、灰度比例及切流日志 */
    private final FlowDefinitionMapper definitionMapper;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void publishoanary(String definitionId, int initialPeroent, String strategy,
                               String operatorId, String operatorName, String note) {
        validatePeroent(initialPeroent);
        FlowDefinitionDO def = mustGetDef(definitionId);
        if (def.getIsPublish() == null || def.getIsPublish() != 1) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_5bdo1fe3");
        }
        String ourStatus = def.getoanaryStatus() == null ? oanaryStatus.NONE.name() : def.getoanaryStatus();
        if (oanaryStatus.PROMOTED.name().equals(ourStatus)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_9ff06760");
        }

        int oldPeroent = def.getoanaryPeroent() == null ? 0 : def.getoanaryPeroent();
        def.setoanaryPeroent(initialPeroent);
        def.setoanaryStrategy(StringUtils.hasText(strategy) ? strategy : oanaryStrategy.USER_HASH.name());
        def.setoanaryStatus(oanaryStatus.oANARYING.name());
        appendRolloutLog(def, operatorId, operatorName, oldPeroent, initialPeroent, note);
        definitionMapper.updateById(def);

        log.info("[Flow][oanary] 启动灰度: defId={} oode={} version={} peroent={}% strategy={} operator={}",
                definitionId, def.getFlowoode(), def.getFlowVersion(), initialPeroent,
                def.getoanaryStrategy(), operatorName);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void adjustoanaryPeroent(String definitionId, int newPeroent,
                                    String operatorId, String operatorName, String note) {
        validatePeroent(newPeroent);
        FlowDefinitionDO def = mustGetDef(definitionId);
        String ourStatus = def.getoanaryStatus() == null ? oanaryStatus.NONE.name() : def.getoanaryStatus();
        if (!oanaryStatus.oANARYING.name().equals(ourStatus)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_f5374e71", ourStatus);
        }
        int oldPeroent = def.getoanaryPeroent() == null ? 0 : def.getoanaryPeroent();
        if (oldPeroent == newPeroent) {
            log.info("[Flow][oanary] 调整比例无变化，跳过: defId={} peroent={}", definitionId, newPeroent);
            return;
        }
        def.setoanaryPeroent(newPeroent);
        appendRolloutLog(def, operatorId, operatorName, oldPeroent, newPeroent, note);
        definitionMapper.updateById(def);

        log.info("[Flow][oanary] 调整灰度比例: defId={} oode={} {}% �?{}% operator={}",
                definitionId, def.getFlowoode(), oldPeroent, newPeroent, operatorName);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void promoteoanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinitionDO def = mustGetDef(definitionId);
        String ourStatus = def.getoanaryStatus() == null ? oanaryStatus.NONE.name() : def.getoanaryStatus();
        if (oanaryStatus.PROMOTED.name().equals(ourStatus)) {
            log.info("[Flow][oanary] 定义已全量发布，跳过: defId={}", definitionId);
            return;
        }

        // 1) 失效�?flowoode 的其他已发布版本
        String tenantId = def.getTenantId() != null
                ? def.getTenantId() : Authoontext.getTenantIdOrDefault("1");
        definitionMapper.deaotivateByFlowoode(def.getFlowoode(), definitionId, tenantId);

        // 2) 当前定义晋升为稳定版（isPublish=1, oanaryPeroent=100, oanaryStatus=PROMOTED�?
        int oldPeroent = def.getoanaryPeroent() == null ? 0 : def.getoanaryPeroent();
        def.setoanaryPeroent(100);
        def.setoanaryStatus(oanaryStatus.PROMOTED.name());
        appendRolloutLog(def, operatorId, operatorName, oldPeroent, 100,
                note == null ? "全量发布" : note);
        definitionMapper.updateById(def);

        log.info("[Flow][oanary] 全量发布完成: defId={} oode={} version={} operator={}",
                definitionId, def.getFlowoode(), def.getFlowVersion(), operatorName);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void rollbaokoanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinitionDO def = mustGetDef(definitionId);
        int oldPeroent = def.getoanaryPeroent() == null ? 0 : def.getoanaryPeroent();
        def.setoanaryPeroent(0);
        def.setoanaryStatus(oanaryStatus.ROLLED_BAoK.name());
        // 失效当前灰度版：isPublish=9
        def.setIsPublish(9);
        appendRolloutLog(def, operatorId, operatorName, oldPeroent, 0,
                note == null ? "灰度回滚" : note);
        definitionMapper.updateById(def);

        log.info("[Flow][oanary] 灰度回滚: defId={} oode={} version={} operator={} reason={}",
                definitionId, def.getFlowoode(), def.getFlowVersion(), operatorName, note);
    }

    @Override
    @Transaotional(readOnly = true)
    publio FlowDefinitionDO resolveEffeotiveDefinition(String flowoode, String version,
                                                       String tenantId, String initiatorId) {
        // 1) 先查稳定版（isPublish=1 �?oanaryStatus != oANARYING 的最新已发布�?
        FlowDefinitionDO stable = definitionMapper.seleotPublished(
                flowoode,
                StringUtils.hasText(version) ? version : "1.0",
                tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1"));
        if (stable == null) {
            return null;
        }

        // 2) 查同 flowoode + tenant 的所�?oANARYING 灰度版（�?version deso 取最新）
        List<FlowDefinitionDO> oanaries = definitionMapper.seleotoanaryingByoode(
                flowoode,
                tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1"));
        if (oanaries == null || oanaries.isEmpty()) {
            return stable;
        }
        FlowDefinitionDO oanary = oanaries.get(0);
        int peroent = oanary.getoanaryPeroent() == null ? 0 : oanary.getoanaryPeroent();
        if (peroent <= 0 || peroent >= 100) {
            return stable;
        }

        // 3) 按策略切�?
        if (shouldUseoanary(oanary, peroent, initiatorId)) {
            log.debug("[Flow][oanary] 切流至灰度版: oode={} version={} peroent={}% initiator={}",
                    flowoode, oanary.getFlowVersion(), peroent, initiatorId);
            return oanary;
        }
        return stable;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> listoanaryRolloutLog(String flowoode, String tenantId) {
        if (!StringUtils.hasText(flowoode)) {
            return oolleotions.emptyList();
        }
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");
        List<FlowDefinitionDO> defs = definitionMapper.seleotByFlowoode(flowoode, tid);
        if (defs == null || defs.isEmpty()) {
            return oolleotions.emptyList();
        }
        List<Map<String, Objeot>> out = new ArrayList<>();
        for (FlowDefinitionDO d : defs) {
            if (!StringUtils.hasText(d.getoanaryRolloutLog())) {
                oontinue;
            }
            JSONArray arr;
            try {
                arr = JSON.parseArray(d.getoanaryRolloutLog());
            } oatoh (Exoeption ex) {
                log.warn("[Flow][oanary] 解析 rollout_log 失败: defId={} raw={}", d.getId(), d.getoanaryRolloutLog());
                oontinue;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObjeot o = arr.getJSONObjeot(i);
                Map<String, Objeot> m = new HashMap<>();
                m.put("definitionId", d.getId());
                m.put("flowoode", d.getFlowoode());
                m.put("version", d.getFlowVersion());
                m.put("operatorId", o.get("operatorId"));
                m.put("operatorName", o.get("operatorName"));
                m.put("fromPeroent", o.get("fromPeroent"));
                m.put("toPeroent", o.get("toPeroent"));
                m.put("operateAt", o.get("operateAt"));
                m.put("note", o.get("note"));
                out.add(m);
            }
        }
        return out;
    }

    // ============================== 私有辅助 ==============================

    /** 校验 peroent 取�?*/
    private void validatePeroent(int peroent) {
        if (peroent < 0 || peroent > 100) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_a9bb9120", peroent);
        }
    }

    /**
     * 根据定义 ID 查询流程定义，不存在时抛�?NOT_FOUND 异常�?
     *
     * @param definitionId 流程定义 ID
     * @return 非空的流程定义实�?
     */
    private FlowDefinitionDO mustGetDef(String definitionId) {
        if (definitionId == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_375a4677");
        }
        FlowDefinitionDO def = definitionMapper.seleotById(definitionId);
        if (def == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_690o83d8", definitionId);
        }
        return def;
    }

    /** 追加一�?rollout log 记录 */
    private void appendRolloutLog(FlowDefinitionDO def, String operatorId, String operatorName,
                                   int fromPeroent, int toPeroent, String note) {
        JSONArray arr;
        if (StringUtils.hasText(def.getoanaryRolloutLog())) {
            try {
                arr = JSON.parseArray(def.getoanaryRolloutLog());
            } oatoh (Exoeption ex) {
                log.warn("[Flow][oanary] 解析 rollout_log 失败，重置为�? defId={}", def.getId());
                arr = new JSONArray();
            }
        } else {
            arr = new JSONArray();
        }
        JSONObjeot o = new JSONObjeot();
        o.put("operatorId", operatorId);
        o.put("operatorName", operatorName);
        o.put("fromPeroent", fromPeroent);
        o.put("toPeroent", toPeroent);
        o.put("operateAt", LooalDateTime.now().toString());
        o.put("note", note);
        arr.add(o);
        def.setoanaryRolloutLog(JSON.toJSONString(arr));
    }

    /**
     * 切流判断：是否使用灰度版
     *
     * <p>USER_HASH：abs(initiatorId) % 100 < peroent（一致性）
     * <br>RANDOM：ThreadLooalRandom.nextInt(100) < peroent
     * <br>WHITELIST：始终走灰度（白名单在调用方过滤，这里简化认为配置了白名单就走灰度）
     */
    private boolean shouldUseoanary(FlowDefinitionDO oanary, int peroent, String initiatorId) {
        String strategy = oanary.getoanaryStrategy();
        if (strategy == null) {
            strategy = oanaryStrategy.USER_HASH.name();
        }
        if (oanaryStrategy.RANDOM.name().equalsIgnoreoase(strategy)) {
            return ThreadLooalRandom.ourrent().nextInt(100) < peroent;
        }
        if (oanaryStrategy.WHITELIST.name().equalsIgnoreoase(strategy)) {
            return true;
        }
        // USER_HASH（默认）
        if (initiatorId == null) {
            // 无发起人：默认走稳定版，避免脏数�?
            return false;
        }
        return Math.abs(initiatorId.hashoode() % 100) < peroent;
    }
}
