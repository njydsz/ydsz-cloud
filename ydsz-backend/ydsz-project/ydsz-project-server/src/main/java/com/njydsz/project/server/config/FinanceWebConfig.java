package com.njydsz.project.server.config;

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
 * 财务会计服务 Web 层配置
 *
 * <p>集中配置 MyBatis-Plus 分页插件、OpenAPI 文档、跨域等 Web 层基础设施。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Configuration
public class FinanceWebConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    public OpenAPI financeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("YDSZ 财务会计服务 API")
                        .description("发票管理 / 回款管理 / 费用报销 / 收入确认 / 利润核算 / 对账 / 信用评估")
                        .version("1.0.0")
                        .contact(new Contact().name("ydsz-team"))
                        .license(new License().name("Proprietary")));
    }
}
