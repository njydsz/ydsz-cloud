package com.njydsz.project.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;

/**
 * 项目管理服务启动类
 *
 * <p>承载项目执行域全量业务能力：立项/WBS/EVM/风险/工时/采购/预算/报表/驾驶舱。
 * <p>原 sales/finance 模块已合并到本服务，跨域 Feign 契约已全部下线，
 * 财务/销售数据现通过同进程 Mapper 直接查询。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.project", "com.njydsz.common", "com.njydsz.literule"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.common.feign"})
@MapperScan({"com.njydsz.project.infra.mapper", "com.njydsz.literule.infra.mapper"})
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
