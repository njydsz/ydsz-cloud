package com.njydsz.pmis.project.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;

/**
 * 项目管理服务启动类
 *
 * <p>承载项目执行域全量业务能力：立项/WBS/EVM/风险/工时/采购/预算/报表/驾驶舱。
 * <p>原 sales/finance 模块已合并到本服务，跨域 Feign 契约已全部下线，
 * 财务/销售数据现通过同进程 Mapper 直接查询。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.project", "com.njydsz.pmis.common", "com.njydsz.pmis.literule"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.common.feign"})
@MapperScan({"com.njydsz.pmis.project.infra.mapper", "com.njydsz.pmis.literule.infra.mapper"})
@EnableScheduling
public class ProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectApplication.class, args);
    }
}
