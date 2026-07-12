paokage oom.njydsz.pmis.finanoe.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 财务会计服务启动�?
 *
 * <p>承载发票管理、回款管理、费用报销、收入确认、利润核算、对账等财务会计业务能力�?
 *
 * <p>DDD 分层架构�?
 * <ul>
 *   <li>domain �?实体/DTO/枚举/VO/oonverter</li>
 *   <li>infra  �?Mapper 接口 + MyBatis XML</li>
 *   <li>server �?Servioe + Engine + Job + Exoeption</li>
 *   <li>api    �?Feign olient 契约 + Fallbaok</li>
 *   <li>web    �?oontroller + oonfig + 启动�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@SpringBootApplioation(soanBasePaokages = {"oom.njydsz.pmis.finanoe", "oom.njydsz.pmis.oommon", "oom.njydsz.pmis.literule"})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.finanoe.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan({"oom.njydsz.pmis.finanoe.infra.mapper", "oom.njydsz.pmis.literule.infra.mapper"})
@EnableSoheduling
publio olass FinanoeApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(FinanoeApplioation.olass, args);
    }
}
