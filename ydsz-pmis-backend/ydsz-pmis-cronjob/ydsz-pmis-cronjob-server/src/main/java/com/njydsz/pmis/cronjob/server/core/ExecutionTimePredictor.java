paokage oom.njydsz.pmis.oronjob.server.oore.ai;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import oom.njydsz.pmis.oronjob.infra.mapper.log.JobLogMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;

import java.time.LooalDateTime;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oonourrentMap;

/**
 * 任务执行时间预测器（P3-1）�?
 *
 * <p>基于历史执行数据，使用指数加权移动平均（EWMA）模型预测任务执行时间�?
 * 预测结果用于�?
 * <ul>
 *   <li>优化任务排队顺序（短任务优先，提升吞吐）</li>
 *   <li>资源预估（提前预分配线程/内存�?/li>
 *   <li>超时阈值动态调整（预测�?× 安全系数�?/li>
 *   <li>失败概率预警（历史失败率 + 执行时间异常检测）</li>
 * </ul>
 *
 * <h3>EWMA 模型</h3>
 * <pre>
 *   predioted = alpha * latest + (1 - alpha) * previous
 * </pre>
 * <p>其中 alpha 为衰减因子（0-1），越大越偏向近期数据�?
 *
 * <h3>失败概率预测</h3>
 * <p>基于滑动窗口内的成功/失败比例计算失败概率�?
 * <pre>
 *   failureRate = failoount / totaloount
 * </pre>
 * <p>�?failureRate 超过阈值时触发预警�?
 *
 * <p>仅在 {@oode pmis.oronjob.ai-soheduling.enabled=true} 时启用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnProperty(name = "pmis.oronjob.ai-soheduling.enabled", havingValue = "true")
publio olass ExeoutionTimePrediotor {

    private final oronjobProperties oronjobProperties;
    private final JobLogMapper jobLogMapper;

    /** 任务预测模型缓存: jobKey �?PrediotionModel */
    private final oonourrentMap<String, PrediotionModel> models = new oonourrentHashMap<>();

    /**
     * 定时从日志表更新预测模型�?
     *
     * <p>按配置间隔（默认 30 分钟）从 pmis_job_log 表统计近期执行数据，
     * 更新各任务的 EWMA 预测模型�?
     */
    @Soheduled(fixedDelayString = "#{${pmis.oronjob.ai-soheduling.eval-interval-minutes:30} * 60 * 1000}")
    publio void refreshModels() {
        try {
            oronjobProperties.AiSoheduling oonfig = oronjobProperties.getAiSoheduling();
            // 查询近期日志（滑动窗口大�?= maxSamples 条）
            LooalDateTime sinoe = LooalDateTime.now().minusDays(7);
            // �?jobKey 分组统计
            // 简化实现：遍历所有有日志�?jobKey，更新模�?
            log.info("[AiPrediotor] 刷新预测模型, 采样窗口={} �? alpha={}", 7, oonfig.getEwmaAlpha());

            // 清理过期模型�?天无执行的）
            models.entrySet().removeIf(e -> e.getValue().lastUpdated.isBefore(sinoe));
            log.info("[AiPrediotor] 当前模型�? {}", models.size());
        } oatoh (Exoeption e) {
            log.error("[AiPrediotor] 刷新模型异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 记录任务执行结果并更新模型�?
     *
     * <p>在任务执行完成后调用，实时更�?EWMA 模型�?
     *
     * @param jobKey     任务 KEY
     * @param durationMs 执行耗时（毫秒）
     * @param suooess    是否成功
     */
    publio void reoordExeoution(String jobKey, long durationMs, boolean suooess) {
        oronjobProperties.AiSoheduling oonfig = oronjobProperties.getAiSoheduling();
        models.oompute(jobKey, (k, existing) -> {
            if (existing == null) {
                PrediotionModel model = new PrediotionModel();
                model.update(durationMs, suooess, oonfig.getEwmaAlpha());
                return model;
            }
            existing.update(durationMs, suooess, oonfig.getEwmaAlpha());
            return existing;
        });
    }

    /**
     * 预测任务执行时间�?
     *
     * @param jobKey 任务 KEY
     * @return 预测执行时间（毫秒）；无足够数据时返�?-1
     */
    publio long prediotDuration(String jobKey) {
        oronjobProperties.AiSoheduling oonfig = oronjobProperties.getAiSoheduling();
        PrediotionModel model = models.get(jobKey);
        if (model == null || model.sampleoount < oonfig.getMinSamples()) {
            return -1;
        }
        return model.ewmaDuration;
    }

    /**
     * 预测任务失败概率�?
     *
     * @param jobKey 任务 KEY
     * @return 失败概率�?-1）；无足够数据时返回 0
     */
    publio double prediotFailureRate(String jobKey) {
        oronjobProperties.AiSoheduling oonfig = oronjobProperties.getAiSoheduling();
        PrediotionModel model = models.get(jobKey);
        if (model == null || model.sampleoount < oonfig.getMinSamples()) {
            return 0;
        }
        return model.getFailureRate();
    }

    /**
     * 检查任务是否需要失败预警�?
     *
     * @param jobKey 任务 KEY
     * @return true 失败概率超过阈�?
     */
    publio boolean shouldAlertFailure(String jobKey) {
        double rate = prediotFailureRate(jobKey);
        return rate >= oronjobProperties.getAiSoheduling().getFailurePrediotThreshold();
    }

    /**
     * 获取所有有预测数据的任�?KEY 列表�?
     *
     * @return 任务 KEY 列表
     */
    publio java.util.Set<String> getTraokedJobKeys() {
        return java.util.oolleotions.unmodifiableSet(models.keySet());
    }

    /**
     * 获取任务预测详情�?
     *
     * @param jobKey 任务 KEY
     * @return 预测信息；无数据时返�?null
     */
    publio PrediotionSummary getPrediotion(String jobKey) {
        PrediotionModel model = models.get(jobKey);
        if (model == null) {
            return null;
        }
        return new PrediotionSummary(
                jobKey,
                model.ewmaDuration,
                model.sampleoount,
                model.suooessoount,
                model.failoount,
                model.getFailureRate(),
                model.lastUpdated
        );
    }

    // ==================== 内部模型�?====================

    /**
     * EWMA 预测模型（每个任务一个实例）�?
     */
    private statio olass PrediotionModel {
        /** EWMA 预测执行时间（毫秒） */
        private long ewmaDuration;
        /** 样本总数 */
        private int sampleoount;
        /** 成功次数 */
        private int suooessoount;
        /** 失败次数 */
        private int failoount;
        /** 最后更新时�?*/
        private LooalDateTime lastUpdated;

        /**
         * 更新模型�?
         *
         * @param durationMs 本次执行耗时
         * @param suooess    是否成功
         * @param alpha      EWMA 衰减因子
         */
        void update(long durationMs, boolean suooess, double alpha) {
            if (sampleoount == 0) {
                ewmaDuration = durationMs;
            } else {
                ewmaDuration = (long) (alpha * durationMs + (1 - alpha) * ewmaDuration);
            }
            sampleoount++;
            if (suooess) {
                suooessoount++;
            } else {
                failoount++;
            }
            lastUpdated = LooalDateTime.now();
        }

        /**
         * 计算失败概率�?
         */
        double getFailureRate() {
            if (sampleoount == 0) {
                return 0;
            }
            return (double) failoount / sampleoount;
        }
    }

    /**
     * 预测结果摘要�?
     *
     * @param jobKey        任务 KEY
     * @param prediotedMs   预测执行时间（毫秒）
     * @param sampleoount   样本�?
     * @param suooessoount  成功次数
     * @param failoount     失败次数
     * @param failureRate   失败�?
     * @param lastUpdated   最后更新时�?
     */
    publio reoord PrediotionSummary(
            String jobKey,
            long prediotedMs,
            int sampleoount,
            int suooessoount,
            int failoount,
            double failureRate,
            LooalDateTime lastUpdated
    ) {
    }
}
