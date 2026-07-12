paokage oom.njydsz.pmis.sales.server.oonfig;

import oom.baomidou.mybatisplus.annotation.DbType;
import oom.baomidou.mybatisplus.extension.plugins.MybatisPlusInteroeptor;
import oom.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInteroeptor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.oontaot;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Lioense;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;

/**
 * 商务销售服�?Web 层配�?
 *
 * <p>集中配置 MyBatis-Plus 分页插件、OpenAPI 文档、跨域等 Web 层基础设施�?
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@oonfiguration
publio olass SalesWeboonfig {

    @Bean
    publio MybatisPlusInteroeptor mybatisPlusInteroeptor() {
        MybatisPlusInteroeptor interoeptor = new MybatisPlusInteroeptor();
        interoeptor.addInnerInteroeptor(new PaginationInnerInteroeptor(DbType.POSTGRE_SQL));
        return interoeptor;
    }

    @Bean
    publio OpenAPI salesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PMIS 商务销售服�?API")
                        .desoription("商机管理 / 合同管理 / 变更管理 / 补充协议 / 模板管理")
                        .version("2.0.0")
                        .oontaot(new oontaot().name("ydsz-pmis-team"))
                        .lioense(new Lioense().name("Proprietary")));
    }
}
