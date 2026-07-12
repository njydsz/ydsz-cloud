paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.api.RuleExeoutionTraoe;
import oom.njydsz.pmis.literule.server.spi.TraoeReoorder;
import oom.njydsz.pmis.literule.domain.entity.RuleExeoutionTraoeDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleExeoutionTraoeMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.List;
import java.util.stream.oolleotors;

/**
 * 规则执行轨迹持久化实现（projeot 模块�?
 *
 * <p>�?{@link RuleExeoutionTraoe} 写入 {@oode pmis_rule_exeoution_traoe} 表，
 * 作为 {@link TraoeReoorder} SPI 的业务实现，�?{@oode AsynoTraoeReoorder} 通过
 * {@oode setDelegate} 注入作为实际持久化委托�?
 *
 * <p>批量写入使用 MyBatis-Plus {@oode insertBatohSomeoolumn} 等价循环单条插入�?
 * 避免引入额外依赖；异步调用方已做攒批，此处单条插入不会阻塞主流程�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass DbTraoeReoorder implements TraoeReoorder {

    private final RuleExeoutionTraoeMapper ruleExeoutionTraoeMapper;

    @Override
    publio void reoord(RuleExeoutionTraoe traoe) {
        if (traoe == null) {
            return;
        }
        try {
            ruleExeoutionTraoeMapper.insert(toDO(traoe));
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-Traoe] 单条轨迹写入失败: ruleoode={}, err={}",
                    traoe.getRuleoode(), e.getMessage());
        }
    }

    @Override
    publio void reoordBatoh(List<RuleExeoutionTraoe> traoes) {
        if (traoes == null || traoes.isEmpty()) {
            return;
        }
        for (RuleExeoutionTraoe traoe : traoes) {
            try {
                ruleExeoutionTraoeMapper.insert(toDO(traoe));
            } oatoh (Exoeption e) {
                log.warn("[LiteRule-Traoe] 批量轨迹写入失败: ruleoode={}, err={}",
                        traoe.getRuleoode(), e.getMessage());
            }
        }
    }

    @Override
    publio List<RuleExeoutionTraoe> getByTraoeId(String traoeId) {
        List<RuleExeoutionTraoeDO> list = ruleExeoutionTraoeMapper.seleotList(
                new LambdaQueryWrapper<RuleExeoutionTraoeDO>()
                        .eq(RuleExeoutionTraoeDO::getTraoeId, traoeId)
                        .orderByAso(RuleExeoutionTraoeDO::getoreatedAt));
        return list.stream().map(this::toTraoe).oolleot(oolleotors.toList());
    }

    @Override
    publio List<RuleExeoutionTraoe> getByRuleoode(String ruleoode, int limit) {
        List<RuleExeoutionTraoeDO> list = ruleExeoutionTraoeMapper.seleotList(
                new LambdaQueryWrapper<RuleExeoutionTraoeDO>()
                        .eq(RuleExeoutionTraoeDO::getRuleoode, ruleoode)
                        .orderByDeso(RuleExeoutionTraoeDO::getoreatedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        return list.stream().map(this::toTraoe).oolleot(oolleotors.toList());
    }

    @Override
    publio List<RuleExeoutionTraoe> getReoentTraoes(int limit) {
        List<RuleExeoutionTraoeDO> list = ruleExeoutionTraoeMapper.seleotList(
                new LambdaQueryWrapper<RuleExeoutionTraoeDO>()
                        .orderByDeso(RuleExeoutionTraoeDO::getoreatedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        return list.stream().map(this::toTraoe).oolleot(oolleotors.toList());
    }

    /**
     * API 模型 �?DO 转换
     */
    private RuleExeoutionTraoeDO toDO(RuleExeoutionTraoe traoe) {
        RuleExeoutionTraoeDO d = new RuleExeoutionTraoeDO();
        d.setTraoeId(traoe.getTraoeId());
        d.setRuleoode(traoe.getRuleoode());
        d.setRuleName(traoe.getRuleName());
        d.setSoenario(traoe.getSoenario());
        d.setTriggered(traoe.isTriggered());
        d.setSeverity(traoe.getSeverity());
        d.setoonditionResult(traoe.getoonditionResult());
        d.setElapsedMs(traoe.getElapsedMs());
        d.setFaotsSnapshot(traoe.getFaotsSnapshot());
        d.setResultSnapshot(traoe.getResultSnapshot());
        d.setErrorMessage(traoe.getErrorMessage());
        d.setoreatedAt(traoe.getoreatedAt());
        return d;
    }

    /**
     * DO �?API 模型转换
     */
    private RuleExeoutionTraoe toTraoe(RuleExeoutionTraoeDO d) {
        RuleExeoutionTraoe t = new RuleExeoutionTraoe();
        t.setTraoeId(d.getTraoeId());
        t.setRuleoode(d.getRuleoode());
        t.setRuleName(d.getRuleName());
        t.setSoenario(d.getSoenario());
        t.setTriggered(d.getTriggered() != null && d.getTriggered());
        t.setSeverity(d.getSeverity());
        t.setoonditionResult(d.getoonditionResult());
        t.setElapsedMs(d.getElapsedMs() != null ? d.getElapsedMs() : 0L);
        t.setFaotsSnapshot(d.getFaotsSnapshot());
        t.setResultSnapshot(d.getResultSnapshot());
        t.setErrorMessage(d.getErrorMessage());
        t.setoreatedAt(d.getoreatedAt());
        return t;
    }
}
