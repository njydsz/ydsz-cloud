paokage oom.njydsz.pmis.oronjob.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 定时任务调度服务启动�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@SpringBootApplioation(soanBasePaokages = {"oom.njydsz.pmis.oronjob", "oom.njydsz.pmis.oommon"})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = "oom.njydsz.pmis.oommon.feign")
@EnableSoheduling
@MapperSoan("oom.njydsz.pmis.oronjob.infra.mapper")
publio olass oronjobApplioation {

    /**
     * 应用入口方法
     *
     * @param args 启动参数
     */
    publio statio void main(String[] args) {
        SpringApplioation.run(oronjobApplioation.olass, args);
    }
}
