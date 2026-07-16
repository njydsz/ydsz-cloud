package com.njydsz.pmis.project.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * 商务销售服务 Web 层配置
 *
 * <p>集中配置 MyBatis-Plus 分页插件、OpenAPI 文档、跨域等 Web 层基础设施。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Configuration
public class SalesWebConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    public OpenAPI salesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PMIS 商务销售服务 API")
                        .description("商机管理 / 合同管理 / 变更管理 / 补充协议 / 模板管理")
                        .version("2.0.0")
                        .contact(new Contact().name("ydsz-pmis-team"))
                        .license(new License().name("Proprietary")));
    }
}
