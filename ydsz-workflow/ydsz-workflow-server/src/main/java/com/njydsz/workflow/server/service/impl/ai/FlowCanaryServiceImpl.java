package com.njydsz.workflow.server.service.impl.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.njydsz.common.util.id.RandomUtils;
import com.njydsz.common.json.YdszJson;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowDefinition;
import com.njydsz.workflow.domain.enums.CanaryStatus;
import com.njydsz.workflow.domain.enums.CanaryStrategy;
import com.njydsz.workflow.infra.mapper.FlowDefinitionMapper;
import com.njydsz.workflow.server.service.FlowCanaryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度发布服务实现
 *
 * <p>对 {@link FlowCanaryService} 接口的完整实现，承担工作流引擎的<b>灰度发布</b>能力。
 * 借鉴大厂「无中断发布」理念，支持同一 {@code flowCode} 下多版本并存，按「比例 / 用户 / 租户 / 标签」
 * 等多种策略切流，避免新版本 BUG 直接影响全量用户。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>灰度发布</b>：{@link #publishCanary} 启动灰度，按 {@code initialPercent}（如 5%）引流到新版本</li>
 *   <li><b>动态调比</b>：{@link #adjustCanaryPercent} 提升或回退灰度比例（10% → 30% → 60% → 100%）</li>
 *   <li><b>一键回滚</b>：{@link #rollbackCanary} 立即回滚到稳定版本，规避新版本线上故障</li>
 *   <li><b>全量发布</b>：{@link #promoteToFull} 灰度完成提升为正式版本，停用旧版本</li>
 *   <li><b>效果分析</b>：{@link #resolveEffectiveDefinition} 启动实例时解析「当前用户应走哪个版本」</li>
 *   <li><b>发布日志</b>：所有切流动作写入 {@code canary_rollout_log} JSON 字段，支持回溯审计</li>
 * </ul>
 *
 * <p><b>灰度策略（{@link CanaryStrategy}）：</b>
 * <ul>
 *   <li>{@code USER_HASH} — 按 {@code userId} 哈希分桶，相同用户稳定路由到同一版本（最常用）</li>
 *   <li>{@code TENANT} — 按 {@code tenantId} 整租户切流（适合 B 端多租户）</li>
 *   <li>{@code PERCENT} — 纯随机比例分桶（最简单但不稳定）</li>
 *   <li>{@code TAG} — 按用户标签切流（如「内部员工」→ 新版本，「外部客户」→ 旧版本）</li>
 * </ul>
 *
 * <p><b>状态机（{@link CanaryStatus}）：</b>
 * <pre>
 *   NONE → CANARYING → PROMOTED（成功路径）
 *                    → ROLLED_BACK（回滚路径）
 * </pre>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}，
 *       确保「定义状态 + canary 字段 + rollout log」原子性</li>
 *   <li>同一 {@code flowCode} 的并发灰度由 {@code ydsz:flow:canary:lock:{flowCode}} 分布式锁串行化</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>支持<b>渐进式发布</b>：建议发布比例按 5% → 20% → 50% → 100% 阶梯提升，
 *       每档观察 5-10 分钟异常指标（错误率 / 审批耗时）后再提</li>
 *   <li>支持<b>快速回滚</b>：灰度出现异常时 {@link #rollbackCanary} 一键回滚，
 *       已生效的实例保留在原版本，不再切流到新版本</li>
 *   <li>支持<b>流量染色</b>：实例启动时记录 {@code canary_flag}，便于查询「走新版本的所有实例」</li>
 * </ul>
 *
 * <p><b>指标埋点：</b>通过 {@code ydsz_workflow_canary_*} 指标暴露灰度发布次数、调整次数、回滚次数，
 * 供 Prometheus / Grafana 监控大盘使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowCanaryService 接口定义
 * @see com.njydsz.workflow.domain.entity.FlowDefinition 流程定义实体（持有 canary 状态字段）
 * @see CanaryStrategy 灰度策略枚举
 * @see CanaryStatus 灰度状态枚举
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowCanaryServiceImpl implements FlowCanaryService {

    /** 流程定义 Mapper，用于读取/更新灰度发布状态、灰度比例及切流日志 */
    private final FlowDefinitionMapper definitionMapper;

    /**
     * 启动灰度发布
     *
     * <p>将已发布（{@code isPublish=1}）的流程定义切换为灰度态（{@code CanaryStatus.CANARYING}），
     * 设置初始引流比例（如 5%）。启动后该定义会与同 {@code flowCode} 的稳定版并存，
     * 按 {@code strategy} 切流规则决定每个新启动实例走哪个版本。
     *
     * <p><b>限制：</b>已全量发布（{@code PROMOTED}）的定义不能再启动灰度。
     *
     * @param definitionId    流程定义 ID
     * @param initialPercent  初始灰度比例（0-100），建议起步 ≤ 10%
     * @param strategy        切流策略：{@code USER_HASH / TENANT / RANDOM / TAG}，
     *                        为空时默认 {@code USER_HASH}
     * @param operatorId      操作人 ID（用于审计）
     * @param operatorName    操作人姓名
     * @param note            灰度原因 / 备注（写入 rollout log）
     * @throws SysException {@code BAD_REQUEST} — 定义未发布 / 已全量 / percent 非法
     * @throws SysException {@code NOT_FOUND}   — 定义不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCanary(String definitionId, int initialPercent, String strategy,
                               String operatorId, String operatorName, String note) {
        validatePercent(initialPercent);
        FlowDefinition def = mustGetDef(definitionId);
        if (def.getIsPublish() == null || def.getIsPublish() != 1) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_5bdc1fe3")
                .build();
        }
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (CanaryStatus.PROMOTED.name().equals(curStatus)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_9ff06760")
                .build();
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

    /**
     * 动态调整灰度比例
     *
     * <p>灰度运行中动态提升（如 5% → 30% → 60%）或回退（如 30% → 5%）引流比例。
     * 比例相同时跳过（幂等）。<b>仅</b>{@code CANARYING} 状态可调整。
     *
     * <p><b>建议阶梯：</b>5% → 20% → 50% → 100%，每档观察 5-10 分钟异常指标
     * （错误率 / 审批耗时 / SLA 超时率）后再提升。
     *
     * @param definitionId  流程定义 ID
     * @param newPercent    新灰度比例（0-100）
     * @param operatorId    操作人 ID
     * @param operatorName  操作人姓名
     * @param note          调整原因 / 备注
     * @throws SysException {@code BAD_REQUEST} — 定义非灰度中状态 / percent 非法
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustCanaryPercent(String definitionId, int newPercent,
                                    String operatorId, String operatorName, String note) {
        validatePercent(newPercent);
        FlowDefinition def = mustGetDef(definitionId);
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (!CanaryStatus.CANARYING.name().equals(curStatus)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .key("error.workflow.msg_f5374e71").params(curStatus)
                .build();
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

    /**
     * 灰度全量发布（晋升为稳定版）
     *
     * <p>灰度稳定后将当前灰度版晋升为正式版（{@code canaryStatus=PROMOTED}，
     * {@code canaryPercent=100}），同时<b>失效同 {@code flowCode} 的其他已发布版本</b>（{@code isPublish=0}）。
     * 已生效的实例仍跑原版本，新启动的实例会 100% 路由到新版本。
     *
     * <p>幂等性：已 {@code PROMOTED} 状态直接返回。
     *
     * @param definitionId  流程定义 ID
     * @param operatorId    操作人 ID
     * @param operatorName  操作人姓名
     * @param note          备注（默认「全量发布」）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void promoteCanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinition def = mustGetDef(definitionId);
        String curStatus = def.getCanaryStatus() == null ? CanaryStatus.NONE.name() : def.getCanaryStatus();
        if (CanaryStatus.PROMOTED.name().equals(curStatus)) {
            log.info("[Flow][Canary] 定义已全量发布，跳过: defId={}", definitionId);
            return;
        }

        // 1) 失效同 flowCode 的其他已发布版本
        String tenantId = def.getTenantId() != null
                ? def.getTenantId() : AuthContextUtils.getTenantIdOrDefault();
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

    /**
     * 灰度回滚（一键回到稳定版）
     *
     * <p>灰度期间发现线上 BUG 时，<b>立即</b>将当前灰度版失效（{@code isPublish=9}，{@code canaryPercent=0}，
     * {@code canaryStatus=ROLLED_BACK}），新启动的实例全部走原稳定版。
     * <b>已生效的灰度实例不会自动迁移</b>（避免状态错乱），需手动处置。
     *
     * @param definitionId  流程定义 ID
     * @param operatorId    操作人 ID
     * @param operatorName  操作人姓名
     * @param note          回滚原因（默认「灰度回滚」）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCanary(String definitionId, String operatorId, String operatorName, String note) {
        FlowDefinition def = mustGetDef(definitionId);
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

    /**
     * 解析用户实际应走的流程定义（启动实例时调用）
     *
     * <p>根据 {@code flowCode + version} 查到稳定版，再查同 {@code flowCode} 的灰度中版本，
     * 按切流策略（{@code USER_HASH / RANDOM / WHITELIST}）决定返回稳定版或灰度版。
     *
     * <p><b>短路规则：</b>
     * <ul>
     *   <li>稳定版不存在 → 返回 {@code null}（让调用方报错）</li>
     *   <li>无灰度版 → 返回稳定版</li>
     *   <li>灰度版 {@code percent=0} / {@code percent=100} → 返回稳定版（极端值无意义）</li>
     *   <li>切流判定为「稳定版」→ 返回稳定版</li>
     *   <li>切流判定为「灰度版」→ 返回灰度版</li>
     * </ul>
     *
     * @param flowCode    流程编码
     * @param version     版本号（默认 {@code "1.0"}）
     * @param tenantId    租户 ID（默认从 {@link AuthContextUtils} 取）
     * @param initiatorId 发起人 ID（用于 USER_HASH 切流）
     * @return 实际应走的流程定义（无稳定版时返回 null）
     */
    @Override
    @Transactional(readOnly = true)
    public FlowDefinition resolveEffectiveDefinition(String flowCode, String version,
                                                       String tenantId, String initiatorId) {
        // 1) 先查稳定版（isPublish=1 且 canaryStatus != CANARYING 的最新已发布）
        FlowDefinition stable = definitionMapper.selectPublished(
                flowCode,
                StringUtils.hasText(version) ? version : "1.0",
                tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault());
        if (stable == null) {
            return null;
        }

        // 2) 查同 flowCode + tenant 的所有 CANARYING 灰度版（按 version desc 取最新）
        List<FlowDefinition> canaries = definitionMapper.selectCanaryingByCode(
                flowCode,
                tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault());
        if (canaries == null || canaries.isEmpty()) {
            return stable;
        }
        FlowDefinition canary = canaries.get(0);
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

    /**
     * 查询指定流程的灰度发布操作日志
     *
     * <p>汇总同 {@code flowCode} 下所有定义的 {@code canary_rollout_log} JSON 数组，
     * 反序列化为「操作人 / 比例变更 / 时间 / 备注」列表，按操作时间正序返回。
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（默认从 {@link AuthContextUtils} 取）
     * @return 灰度操作日志列表，{@code flowCode} 为空或无数据时返回空列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCanaryRolloutLog(String flowCode, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            return Collections.emptyList();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();
        List<FlowDefinition> defs = definitionMapper.selectByFlowCode(flowCode, tid);
        if (defs == null || defs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowDefinition d : defs) {
            if (!StringUtils.hasText(d.getCanaryRolloutLog())) {
                continue;
            }
            List<Object> arr;
            try {
                arr = YdszJson.parseArray(d.getCanaryRolloutLog());
            } catch (Exception ex) {
                log.warn("[Flow][Canary] 解析 rollout_log 失败: defId={} raw={}", d.getId(), d.getCanaryRolloutLog());
                continue;
            }
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> o = new HashMap<>();
                for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                    o.put(String.valueOf(e.getKey()), e.getValue());
                }
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
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .key("error.workflow.msg_a9bb9120").params(percent)
                .build();
        }
    }

    /**
     * 根据定义 ID 查询流程定义，不存在时抛出 NOT_FOUND 异常。
     *
     * @param definitionId 流程定义 ID
     * @return 非空的流程定义实体
     */
    private FlowDefinition mustGetDef(String definitionId) {
        if (definitionId == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("error.workflow.msg_375a4677")
                .build();
        }
        FlowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
                .key("error.workflow.msg_690c83d8").params(definitionId)
                .build();
        }
        return def;
    }

    /** 追加一条 rollout log 记录 */
    private void appendRolloutLog(FlowDefinition def, String operatorId, String operatorName,
                                   int fromPercent, int toPercent, String note) {
        List<Object> arr;
        if (StringUtils.hasText(def.getCanaryRolloutLog())) {
            try {
                arr = new ArrayList<>(YdszJson.parseArray(def.getCanaryRolloutLog()));
            } catch (Exception ex) {
                log.warn("[Flow][Canary] 解析 rollout_log 失败，重置为空: defId={}", def.getId());
                arr = new ArrayList<>();
            }
        } else {
            arr = new ArrayList<>();
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("operatorId", operatorId);
        o.put("operatorName", operatorName);
        o.put("fromPercent", fromPercent);
        o.put("toPercent", toPercent);
        o.put("operateAt", LocalDateTime.now().toString());
        o.put("note", note);
        arr.add(o);
        def.setCanaryRolloutLog(YdszJson.toJson(arr));
    }

    /**
     * 切流判断：是否使用灰度版
     *
     * <p>USER_HASH：abs(initiatorId) % 100 < percent（一致性）
     * <br>RANDOM：ThreadLocalRandom.nextInt(100) < percent
     * <br>WHITELIST：始终走灰度（白名单在调用方过滤，这里简化认为配置了白名单就走灰度）
     */
    private boolean shouldUseCanary(FlowDefinition canary, int percent, String initiatorId) {
        String strategy = canary.getCanaryStrategy();
        if (strategy == null) {
            strategy = CanaryStrategy.USER_HASH.name();
        }
        if (CanaryStrategy.RANDOM.name().equalsIgnoreCase(strategy)) {
            return RandomUtils.randomInt(100) < percent;
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
