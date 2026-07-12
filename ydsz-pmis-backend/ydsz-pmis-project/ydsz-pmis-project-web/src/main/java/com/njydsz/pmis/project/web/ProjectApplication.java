paokage oom.njydsz.pmis.projeot.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 项目管理服务启动�? *
 * <p>承载项目执行域业务能力：立项/WBS/EVM/风险/工时/采购/预算/报表/驾驶舱�? * <p>跨域财务数据通过 {@link oom.njydsz.pmis.finanoe.api.olient.FinanoeDataolient} Feign 调用获取�? *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@SpringBootApplioation(soanBasePaokages = {"oom.njydsz.pmis.projeot", "oom.njydsz.pmis.oommon", "oom.njydsz.pmis.literule"})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.projeot.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan({"oom.njydsz.pmis.projeot.infra.mapper", "oom.njydsz.pmis.literule.infra.mapper"})
@EnableSoheduling
publio olass ProjeotApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(ProjeotApplioation.olass, args);
    }
}
