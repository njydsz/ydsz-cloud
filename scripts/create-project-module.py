#!/usr/bin/env python3
"""
重建 ydsz-project 模块骨架（DDD 五层架构）
根据 deploy/sql/modules/V1.0.0_project.sql 中的 34 张表生成 DO/Mapper/FeignClient 骨架
"""
import pathlib
import re

BASE = pathlib.Path("d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-project")
MODULE_BASE = BASE
PACKAGE = "com.njydsz.project"
VERSION = "1.0.0-SNAPSHOT"

# 表名 → DO 类名映射（从 SQL CREATE TABLE 提取）
TABLES = {
    "ydsz_project_initiation": "ProjectInitiation",
    "ydsz_project_budget_item": "ProjectBudgetItem",
    "ydsz_project_gate_review": "ProjectGateReview",
    "ydsz_project_change": "ProjectChange",
    "ydsz_project_opportunity": "ProjectOpportunity",
    "ydsz_project_opportunity_follow": "ProjectOpportunityFollow",
    "ydsz_project_contract": "ProjectContract",
    "ydsz_project_contract_supplement": "ProjectContractSupplement",
    "ydsz_project_contract_change": "ProjectContractChange",
    "ydsz_project_contract_template": "ProjectContractTemplate",
    "ydsz_project_expense": "ProjectExpense",
    "ydsz_project_revenue": "ProjectRevenue",
    "ydsz_project_profit_snapshot": "ProjectProfitSnapshot",
    "ydsz_project_invoice": "ProjectInvoice",
    "ydsz_project_payment": "ProjectPayment",
    "ydsz_project_customer_credit": "ProjectCustomerCredit",
    "ydsz_project_profit_simulation": "ProjectProfitSimulation",
    "ydsz_project_reconcile_daily": "ProjectReconcileDaily",
    "ydsz_execution_wbs_task": "ExecutionWbsTask",
    "ydsz_execution_time_entry": "ExecutionTimeEntry",
    "ydsz_execution_risk": "ExecutionRisk",
    "ydsz_execution_delivery_standard": "ExecutionDeliveryStandard",
    "ydsz_execution_delivery_item": "ExecutionDeliveryItem",
    "ydsz_execution_closure": "ExecutionClosure",
    "ydsz_cost_allocation": "CostAllocation",
    "ydsz_cost_purchase": "CostPurchase",
    "ydsz_evm_measure": "EvmMeasure",
    "ydsz_rate_card": "RateCard",
    "ydsz_rate_internal": "RateInternal",
    "ydsz_warranty": "Warranty",
    "ydsz_ops_ticket": "OpsTicket",
    "ydsz_satisfaction": "Satisfaction",
    "ydsz_billable_utilization_snapshot": "BillableUtilizationSnapshot",
    "ydsz_alert_dispatch": "AlertDispatch",
}

# 表前缀 → 子包（用于领域聚合）
TABLE_PREFIX_PKG = {
    "ydsz_project_": "project",
    "ydsz_execution_": "execution",
    "ydsz_cost_": "cost",
    "ydsz_evm_": "evm",
    "ydsz_rate_": "rate",
    "ydsz_warranty": "warranty",
    "ydsz_ops_ticket": "ops",
    "ydsz_satisfaction": "satisfaction",
    "ydsz_billable_utilization_snapshot": "billable",
    "ydsz_alert_dispatch": "alert",
}

def table_to_pkg(table_name: str) -> str:
    for prefix, pkg in sorted(TABLE_PREFIX_PKG.items(), key=lambda x: -len(x[0])):
        if table_name.startswith(prefix):
            return pkg
    return "common"

def table_to_do_name(table_name: str) -> str:
    return TABLES[table_name]

def write_file(path: pathlib.Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"  ✓ {path.relative_to(MODULE_BASE)}")

# ============================================================
# 1. 创建目录结构和 pom.xml
# ============================================================

def create_parent_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-backend</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project</artifactId>
    <packaging>pom</packaging>
    <name>ydsz-project</name>
    <description>项目核心业务域 - 立项/合同/执行/成本/EVM/利润/资源/质保/工单/满意度</description>
    <modules>
        <module>ydsz-project-api</module>
        <module>ydsz-project-domain</module>
        <module>ydsz-project-infra</module>
        <module>ydsz-project-server</module>
        <module>ydsz-project-web</module>
    </modules>
</project>
'''
    write_file(MODULE_BASE / "pom.xml", content)

def create_api_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-project</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project-api</artifactId>
    <name>ydsz-project-api</name>
    <description>API Layer - Feign Client + DTO/VO + Fallback</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-feign</artifactId>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
        </dependency>
    </dependencies>
</project>
'''
    write_file(MODULE_BASE / "ydsz-project-api" / "pom.xml", content)

def create_domain_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-project</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project-domain</artifactId>
    <name>ydsz-project-domain</name>
    <description>Domain Layer - Entity/DTO/VO/Enum/Converter</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-annotation</artifactId>
        </dependency>
        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations-jakarta</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
'''
    write_file(MODULE_BASE / "ydsz-project-domain" / "pom.xml", content)

def create_infra_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-project</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project-infra</artifactId>
    <name>ydsz-project-infra</name>
    <description>Infrastructure Layer - MyBatis Mapper + Repository</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-project-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
        </dependency>
    </dependencies>
</project>
'''
    write_file(MODULE_BASE / "ydsz-project-infra" / "pom.xml", content)

def create_server_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-project</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project-server</artifactId>
    <name>ydsz-project-server</name>
    <description>Application Layer - Service + Config</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-project-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-lock</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-feign</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-audit</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-json</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-config</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-excel</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-notify</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>dynamic-datasource-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>
</project>
'''
    write_file(MODULE_BASE / "ydsz-project-server" / "pom.xml", content)

def create_web_pom():
    content = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-project</artifactId>
        <version>{VERSION}</version>
    </parent>
    <artifactId>ydsz-project-web</artifactId>
    <name>ydsz-project-web</name>
    <description>Web Layer - REST Controller + Bootstrap</description>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-project-server</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-project-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-project-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-audit</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-bootstrap</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        </dependency>
    </dependencies>

    <build>
        <finalName>${{project.artifactId}}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>repackage</id>
                        <configuration>
                            <classifier>exec</classifier>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
'''
    write_file(MODULE_BASE / "ydsz-project-web" / "pom.xml", content)

# ============================================================
# 2. Application + bootstrap.yml
# ============================================================

def create_application():
    content = f'''package {PACKAGE}.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 项目核心业务域启动类。
 *
 * <p>承载立项/合同/执行/EVM/成本/利润/资源/质保/工单/满意度等核心业务逻辑。
 * 复用 common-lock、common-audit、common-cache、common-excel、common-notify 等公共模块。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@SpringBootApplication(scanBasePackages = {{"{PACKAGE}", "com.njydsz.common"}})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszAudit
@EnableYdszSafe
@EnableYdszFeign(basePackages = {{"{PACKAGE}.api", "com.njydsz.common.feign"}})
@MapperScan("{PACKAGE}.infra.mapper")
@EnableScheduling
public class ProjectApplication {{

    public static void main(String[] args) {{
        SpringApplication.run(ProjectApplication.class, args);
    }}
}}
'''
    write_file(MODULE_BASE / "ydsz-project-web/src/main/java/com/njydsz/project/web/ProjectApplication.java", content)

def create_bootstrap_yml():
    content = '''# =====================================================================
#  YDSZ 项目核心业务域 bootstrap 配置
#  --------------------------------------------------------------------
#  端口：9003
#  职责：立项/合同/执行/EVM/成本/利润/资源/质保/工单/满意度
#  依赖：Nacos 服务发现/配置中心、PostgreSQL、Redis
# =====================================================================

server:
  port: 9003
  max-http-request-header-size: 16KB
  servlet:
    context-path: /

spring:
  application:
    name: ydsz-project
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  main:
    web-application-type: servlet
    allow-bean-definition-overriding: true
  cloud:
    nacos:
      discovery:
        enabled: true
        register-enabled: true
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:ydsz}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        metadata:
          version: 1.0.0
          port: 9003
        ip-type: IPv4
      config:
        enabled: true
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:ydsz}
        group: ${NACOS_GROUP:${spring.profiles.active}}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}
        file-extension: yaml
        refresh-enabled: true
        shared-configs:
          - data-id: ydsz-common.yaml
            group: ${spring.profiles.active}
            refresh: true
          - data-id: ydsz-project.yml
            group: ${spring.profiles.active}
            refresh: true
'''
    write_file(MODULE_BASE / "ydsz-project-web/src/main/resources/bootstrap.yml", content)

# ============================================================
# 3. FeignClient 接口 (api 层)
# ============================================================

def create_feign_clients():
    api_pkg = MODULE_BASE / "ydsz-project-api/src/main/java/com/njydsz/project/api/client"

    clients = {
        "ProjectInitiationClient": {
            "name": "ydsz-project",
            "desc": "项目立项 Feign 接口",
            "methods": [
                "getById(String id)",
                "page(ProjectInitiationPageQuery query)",
                "getByCode(String projectCode)",
            ],
        },
        "ProjectContractClient": {
            "name": "ydsz-project",
            "desc": "合同管理 Feign 接口",
            "methods": [
                "getById(String id)",
                "page(ProjectContractPageQuery query)",
                "listByInitiationId(String initiationId)",
            ],
        },
        "ExecutionWbsClient": {
            "name": "ydsz-project",
            "desc": "WBS 任务执行 Feign 接口",
            "methods": [
                "getById(String id)",
                "listByInitiationId(String initiationId)",
                "getTree(String initiationId)",
            ],
        },
        "ExecutionTimeEntryClient": {
            "name": "ydsz-project",
            "desc": "工时录入 Feign 接口",
            "methods": [
                "page(TimeEntryPageQuery query)",
                "getByEmployeeAndDate(String employeeId, String date)",
            ],
        },
        "EvmMeasureClient": {
            "name": "ydsz-project",
            "desc": "EVM 挣值管理 Feign 接口",
            "methods": [
                "getByInitiationId(String initiationId)",
                "getLatestSnapshot(String initiationId)",
            ],
        },
        "FinanceClient": {
            "name": "ydsz-project",
            "desc": "财务数据 Feign 接口（利润/成本/发票/回款）",
            "methods": [
                "getProfitSnapshot(String initiationId)",
                "getCostSummary(String initiationId)",
                "getRevenueSummary(String initiationId)",
            ],
        },
        "RateCardClient": {
            "name": "ydsz-project",
            "desc": "费率卡 Feign 接口",
            "methods": [
                "getByLevel(String levelCode)",
                "listAll()",
            ],
        },
    }

    imports = """import com.njydsz.common.core.model.Result;
import com.njydsz.common.feign.constant.FeignClientConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;"""

    for name, cfg in clients.items():
        methods_code = []
        for m in cfg["methods"]:
            if "page" in m.lower():
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/page")\n    Result<?> {m.split("(")[0]}({m.split("(")[1]};')
            elif "list" in m.lower() or "getTree" in m.lower():
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/list")\n    Result<?> {m.split("(")[0]}({m.split("(")[1]};')
            elif "getById" in m:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/getById")\n    Result<?> {m.split("(")[0]}(@RequestParam("id") String id);')
            elif "getByCode" in m:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/getByCode")\n    Result<?> {m.split("(")[0]}(@RequestParam("projectCode") String projectCode);')
            elif "getByInitiationId" in m:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/getByInitiationId")\n    Result<?> {m.split("(")[0]}(@RequestParam("initiationId") String initiationId);')
            elif "getByLevel" in m:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/getByLevel")\n    Result<?> {m.split("(")[0]}(@RequestParam("levelCode") String levelCode);')
            elif "getByEmployeeAndDate" in m:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/getByEmployeeAndDate")\n    Result<?> {m.split("(")[0]}(@RequestParam("employeeId") String employeeId, @RequestParam("date") String date);')
            else:
                methods_code.append(f'    @GetMapping("/project/{name.lower().replace("client","")}/{m.split("(")[0]}")\n    Result<?> {m.split("(")[0]}();')

        content = f'''package {PACKAGE}.api.client;

{imports}

/**
 * {cfg["desc"]}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@FeignClient(
    name = "{cfg["name"]}",
    contextId = "{name[0].lower() + name[1:]}",
    path = FeignClientConstants.BASE_PATH)
public interface {name} {{

{chr(10).join(methods_code)}
}}
'''
        write_file(api_pkg / f"{name}.java", content)

    # FallbackFactory
    for name in clients:
        fallback_content = f'''package {PACKAGE}.api.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.model.Result;
import com.njydsz.common.exception.custom.SysException;

/**
 * {name} 降级工厂。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class {name}FallbackFactory implements FallbackFactory<{name}> {{

    @Override
    public {name} create(Throwable cause) {{
        log.error("[Feign降级] {name} 调用失败", cause);
        return new {name}() {{
            @Override
            public Result<?> getById(String id) {{
                throw new SysException("项目服务暂不可用，请稍后重试");
            }}
        }};
    }}
}}
'''
        write_file(api_pkg / f"{name}FallbackFactory.java", fallback_content)

# ============================================================
# 4. DO 类 (domain 层)
# ============================================================

def create_do_classes():
    for table_name, do_name in TABLES.items():
        pkg = table_to_pkg(table_name)
        do_pkg_path = MODULE_BASE / f"ydsz-project-domain/src/main/java/com/njydsz/project/domain/entity/{pkg}"
        table_comment = get_table_comment(table_name)

        content = f'''package {PACKAGE}.domain.entity.{pkg};

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {table_comment} DO。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("{table_name}")
public class {do_name}DO implements Serializable {{

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private String tenantId;

    @Version
    private Integer version;
}}
'''
        write_file(do_pkg_path / f"{do_name}DO.java", content)

# ============================================================
# 5. Mapper 接口 (infra 层)
# ============================================================

def create_mapper_classes():
    for table_name, do_name in TABLES.items():
        pkg = table_to_pkg(table_name)
        mapper_pkg_path = MODULE_BASE / f"ydsz-project-infra/src/main/java/com/njydsz/project/infra/mapper/{pkg}"

        content = f'''package {PACKAGE}.infra.mapper.{pkg};

import {PACKAGE}.domain.entity.{pkg}.{do_name}DO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {do_name} Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface {do_name}Mapper extends BaseMapper<{do_name}DO> {{
}}
'''
        write_file(mapper_pkg_path / f"{do_name}Mapper.java", content)

# ============================================================
# 6. Repository 接口 (domain 层)
# ============================================================

def create_repository_classes():
    for table_name, do_name in TABLES.items():
        pkg = table_to_pkg(table_name)
        repo_pkg_path = MODULE_BASE / f"ydsz-project-domain/src/main/java/com/njydsz/project/domain/repository/{pkg}"

        content = f'''package {PACKAGE}.domain.repository.{pkg};

import {PACKAGE}.domain.entity.{pkg}.{do_name}DO;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * {do_name} Repository。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface I{do_name}Repository extends IService<{do_name}DO> {{
}}
'''
        write_file(repo_pkg_path / f"I{do_name}Repository.java", content)

# ============================================================
# 7. Repository 实现 (infra 层)
# ============================================================

def create_repository_impl_classes():
    for table_name, do_name in TABLES.items():
        pkg = table_to_pkg(table_name)
        impl_pkg_path = MODULE_BASE / f"ydsz-project-infra/src/main/java/com/njydsz/project/infra/repository/{pkg}"

        content = f'''package {PACKAGE}.infra.repository.{pkg};

import {PACKAGE}.domain.entity.{pkg}.{do_name}DO;
import {PACKAGE}.domain.repository.{pkg}.I{do_name}Repository;
import {PACKAGE}.infra.mapper.{pkg}.{do_name}Mapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * {do_name} Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class {do_name}Repository extends ServiceImpl<{do_name}Mapper, {do_name}DO>
        implements I{do_name}Repository {{
}}
'''
        write_file(impl_pkg_path / f"{do_name}Repository.java", content)

# ============================================================
# 辅助函数
# ============================================================

def get_table_comment(table_name: str) -> str:
    comments = {
        "ydsz_project_initiation": "项目立项主表",
        "ydsz_project_budget_item": "立项预算明细",
        "ydsz_project_gate_review": "门径评审记录",
        "ydsz_project_change": "项目变更记录",
        "ydsz_project_opportunity": "商机",
        "ydsz_project_opportunity_follow": "商机跟进记录",
        "ydsz_project_contract": "合同主表",
        "ydsz_project_contract_supplement": "合同补充协议",
        "ydsz_project_contract_change": "合同变更记录",
        "ydsz_project_contract_template": "合同模板",
        "ydsz_project_expense": "项目费用",
        "ydsz_project_revenue": "项目收入",
        "ydsz_project_profit_snapshot": "利润快照",
        "ydsz_project_invoice": "发票",
        "ydsz_project_payment": "回款",
        "ydsz_project_customer_credit": "客户授信",
        "ydsz_project_profit_simulation": "利润模拟",
        "ydsz_project_reconcile_daily": "日对账",
        "ydsz_execution_wbs_task": "WBS 任务",
        "ydsz_execution_time_entry": "工时录入",
        "ydsz_execution_risk": "执行风险",
        "ydsz_execution_delivery_standard": "交付标准",
        "ydsz_execution_delivery_item": "交付项",
        "ydsz_execution_closure": "项目结项",
        "ydsz_cost_allocation": "成本分摊",
        "ydsz_cost_purchase": "采购成本",
        "ydsz_evm_measure": "EVM 挣值测量",
        "ydsz_rate_card": "费率卡",
        "ydsz_rate_internal": "内部费率",
        "ydsz_warranty": "质保",
        "ydsz_ops_ticket": "运维工单",
        "ydsz_satisfaction": "满意度",
        "ydsz_billable_utilization_snapshot": "可计费利用率快照",
        "ydsz_alert_dispatch": "告警派发",
    }
    return comments.get(table_name, table_name)

# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":
    print("=" * 60)
    print("重建 ydsz-project 模块骨架")
    print("=" * 60)

    print("\n[1/7] 创建 pom.xml...")
    create_parent_pom()
    create_api_pom()
    create_domain_pom()
    create_infra_pom()
    create_server_pom()
    create_web_pom()

    print("\n[2/7] 创建 Application + bootstrap.yml...")
    create_application()
    create_bootstrap_yml()

    print("\n[3/7] 创建 FeignClient 接口...")
    create_feign_clients()

    print("\n[4/7] 创建 DO 实体类...")
    create_do_classes()

    print("\n[5/7] 创建 Mapper 接口...")
    create_mapper_classes()

    print("\n[6/7] 创建 Repository 接口...")
    create_repository_classes()

    print("\n[7/7] 创建 Repository 实现...")
    create_repository_impl_classes()

    print(f"\n✓ 完成！共生成 {len(TABLES)} 张表的 DO/Mapper/Repository")
    print(f"  模块路径: {MODULE_BASE}")