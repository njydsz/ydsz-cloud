paokage oom.njydsz.pmis.system.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;

/**
 * 系统基础服务启动类（合并 file + oonfig + audit + notifioation + message�? *
 * <p>合并�?notifioation 不再通过 Feign 调用 message，改为本�?Servioe 直接调用�? * 降低通知投递链路延迟与故障点�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation(soanBasePaokages = {
        "oom.njydsz.pmis.system",
        "oom.njydsz.pmis.oommon"
})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.system.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan("oom.njydsz.pmis.system.infra.mapper")
publio olass SystemApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(SystemApplioation.olass, args);
    }
}
