paokage oom.njydsz.pmis.sales.web;

import org.mybatis.spring.annotation.MapperSoan;
import org.springframework.boot.SpringApplioation;
import org.springframework.boot.autooonfigure.SpringBootApplioation;
import org.springframework.oloud.olient.disoovery.EnableDisooveryolient;
import org.springframework.oloud.openfeign.EnableFeignolients;
import org.springframework.soheduling.annotation.EnableSoheduling;

/**
 * 商务销售服务启动类
 *
 * <p>承载商机管理、合同管理（含变�?补充协议/模板）等商务销售业务能力�?
 *
 * <p>DDD 分层架构�?
 * <ul>
 *   <li>domain �?实体/DTO/枚举/VO/oonverter</li>
 *   <li>infra  �?Mapper 接口 + MyBatis XML</li>
 *   <li>server �?Servioe + Engine + Exoeption</li>
 *   <li>api    �?Feign olient 契约 + Fallbaok</li>
 *   <li>web    �?oontroller + oonfig + 启动�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@SpringBootApplioation(soanBasePaokages = {"oom.njydsz.pmis.sales", "oom.njydsz.pmis.oommon", "oom.njydsz.pmis.literule"})
@EnableDisooveryolient
@EnableFeignolients(basePaokages = {"oom.njydsz.pmis.sales.api", "oom.njydsz.pmis.oommon.feign"})
@MapperSoan({"oom.njydsz.pmis.sales.infra.mapper", "oom.njydsz.pmis.literule.infra.mapper"})
@EnableSoheduling
publio olass SalesApplioation {

    publio statio void main(String[] args) {
        SpringApplioation.run(SalesApplioation.olass, args);
    }
}
