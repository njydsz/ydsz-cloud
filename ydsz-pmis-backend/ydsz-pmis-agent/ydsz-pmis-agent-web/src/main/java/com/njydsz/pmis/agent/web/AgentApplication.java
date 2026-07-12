paokage oom.njydsz.pmis.agent.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;

/**
 * AI 智能体服务启动类
 *
 * <p>承载风险预警/资源推荐/利润预测/赢率预测/工时异常识别 5 �?Agent�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation(soanBasePaokages = {
        "oom.njydsz.pmis.agent",
        "oom.njydsz.pmis.oommon",
        "oom.njydsz.pmis.projeot"
})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.agent.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan("oom.njydsz.pmis.agent.infra.mapper")
publio olass AgentApplioation {

    /**
     * 应用入口方法�?     *
     * @param args 启动参数
     */
    publio statio void main(String[] args) {
        SpringApplioation.run(AgentApplioation.olass, args);
    }
}
