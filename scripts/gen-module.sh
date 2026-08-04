# =====================================================================
#  新模块脚手架生成脚本
#  --------------------------------------------------------------------
#  交互式生成符合 YDSZ DDD 五层架构的新业务模块
#
#  用法:
#    bash scripts/gen-module.sh
#
#  生成结构:
#    ydsz-{name}/
#    ├── ydsz-{name}-api/        # Feign API + DTO
#    ├── ydsz-{name}-domain/     # 实体 + 枚举
#    ├── ydsz-{name}-infra/      # Mapper
#    ├── ydsz-{name}-server/     # Service
#    └── ydsz-{name}-web/        # Controller + 启动类
# =====================================================================

#!/usr/bin/env bash
set -euo pipefail

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  YDSZ 模块脚手架生成器${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 读取输入
read -rp "请输入模块名（英文小写，如 invoice）: " MODULE_NAME
read -rp "请输入端口号（9010-9020）: " MODULE_PORT
read -rp "请输入业务域（逗号分隔，如 invoice,invoice_item）: " MODULE_DOMAINS
read -rp "是否需要消息队列(y/N): " USE_MQ
read -rp "是否需要工作流集成(y/N): " USE_WORKFLOW
read -rp "是否需要规则引擎集成(y/N): " USE_RULE

# 验证
if [[ ! "$MODULE_NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
    echo -e "${RED}错误：模块名只能包含小写字母、数字和连字符，且必须以字母开头${NC}"
    exit 1
fi

if [[ ! "$MODULE_PORT" =~ ^90(1[0-9]|20)$ ]]; then
    echo -e "${RED}错误：端口号必须在 9010-9020 范围内${NC}"
    exit 1
fi

# 转为驼峰命名（Invoice）
MODULE_NAME_CAMEL=$(echo "$MODULE_NAME" | sed -r 's/(^|-)([a-z])/\U\2/g')

# 目标目录
BACKEND_DIR="$(dirname "$0")/../ydsz-backend"
TARGET_DIR="$BACKEND_DIR/ydsz-$MODULE_NAME"

if [ -d "$TARGET_DIR" ]; then
    echo -e "${RED}错误：模块 $TARGET_DIR 已存在${NC}"
    exit 1
fi

echo ""
echo -e "${YELLOW}生成配置确认：${NC}"
echo "  模块名: $MODULE_NAME"
echo "  端口:   $MODULE_PORT"
echo "  业务域: $MODULE_DOMAINS"
echo "  消息队列: $USE_MQ"
echo "  工作流: $USE_WORKFLOW"
echo "  规则引擎: $USE_RULE"
echo ""
read -rp "确认生成？(y/N): " CONFIRM

if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "已取消"
    exit 0
fi

echo -e "${GREEN}开始生成模块...${NC}"

# =====================================================================
#  1. 生成父 POM
# =====================================================================
mkdir -p "$TARGET_DIR"

cat > "$TARGET_DIR/pom.xml" << PARENT_POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-backend</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>ydsz-${MODULE_NAME}</artifactId>
    <packaging>pom</packaging>
    <name>ydsz-${MODULE_NAME}</name>
    <description>${MODULE_NAME_CAMEL} 业务模块</description>

    <modules>
        <module>ydsz-${MODULE_NAME}-api</module>
        <module>ydsz-${MODULE_NAME}-domain</module>
        <module>ydsz-${MODULE_NAME}-infra</module>
        <module>ydsz-${MODULE_NAME}-server</module>
        <module>ydsz-${MODULE_NAME}-web</module>
    </modules>
</project>
PARENT_POM

# =====================================================================
#  2. 生成各子模块
# =====================================================================

# --- api 模块 ---
API_DIR="$TARGET_DIR/ydsz-${MODULE_NAME}-api"
mkdir -p "$API_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/dto"

cat > "$API_DIR/pom.xml" << API_POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-${MODULE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-${MODULE_NAME}-api</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-core</artifactId>
        </dependency>
    </dependencies>
</project>
API_POM

# --- domain 模块 ---
DOMAIN_DIR="$TARGET_DIR/ydsz-${MODULE_NAME}-domain"
mkdir -p "$DOMAIN_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/domain/entity"

cat > "$DOMAIN_DIR/pom.xml" << DOMAIN_POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-${MODULE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-${MODULE_NAME}-domain</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-${MODULE_NAME}-api</artifactId>
        </dependency>
    </dependencies>
</project>
DOMAIN_POM

# --- infra 模块 ---
INFRA_DIR="$TARGET_DIR/ydsz-${MODULE_NAME}-infra"
mkdir -p "$INFRA_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/infra/mapper"

cat > "$INFRA_DIR/pom.xml" << INFRA_POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-${MODULE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-${MODULE_NAME}-infra</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-${MODULE_NAME}-domain</artifactId>
        </dependency>
    </dependencies>
</project>
INFRA_POM

# --- server 模块 ---
SERVER_DIR="$TARGET_DIR/ydsz-${MODULE_NAME}-server"
mkdir -p "$SERVER_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/server/service"
mkdir -p "$SERVER_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/server/service/impl"

SERVER_DEPS=""
if [[ "$USE_MQ" == "y" || "$USE_MQ" == "Y" ]]; then
    SERVER_DEPS="$SERVER_DEPS
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-queue</artifactId>
        </dependency>"
fi

if [[ "$USE_WORKFLOW" == "y" || "$USE_WORKFLOW" == "Y" ]]; then
    SERVER_DEPS="$SERVER_DEPS
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-workflow-api</artifactId>
        </dependency>"
fi

if [[ "$USE_RULE" == "y" || "$USE_RULE" == "Y" ]]; then
    SERVER_DEPS="$SERVER_DEPS
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-literule-api</artifactId>
        </dependency>"
fi

cat > "$SERVER_DIR/pom.xml" << SERVER_POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-${MODULE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-${MODULE_NAME}-server</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-${MODULE_NAME}-infra</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-cache</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-auth</artifactId>
        </dependency>${SERVER_DEPS}
    </dependencies>
</project>
SERVER_POM

# --- web 模块 ---
WEB_DIR="$TARGET_DIR/ydsz-${MODULE_NAME}-web"
mkdir -p "$WEB_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/web/controller"
mkdir -p "$WEB_DIR/src/main/resources/config"

cat > "$WEB_DIR/pom.xml" << WEB_POM
<?xml version="1.0.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.njydsz</groupId>
        <artifactId>ydsz-${MODULE_NAME}</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>ydsz-${MODULE_NAME}-web</artifactId>
    <dependencies>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-${MODULE_NAME}-server</artifactId>
        </dependency>
        <dependency>
            <groupId>com.njydsz</groupId>
            <artifactId>ydsz-common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
    <build>
        <finalName>ydsz-${MODULE_NAME}-web</finalName>
    </build>
</project>
WEB_POM

cat > "$WEB_DIR/src/main/resources/config/bootstrap.yml" << BOOTSTRAP_YAML
server:
  port: ${MODULE_PORT}
spring:
  application:
    name: ydsz-${MODULE_NAME}
  profiles:
    active: \${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      discovery:
        server-addr: \${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: \${NACOS_NAMESPACE:ydsz}
      config:
        server-addr: \${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: \${NACOS_NAMESPACE:ydsz}
        file-extension: yaml
BOOTSTRAP_YAML

# 生成启动类
cat > "$WEB_DIR/src/main/java/com/njydsz/${MODULE_NAME/api}/web/${MODULE_NAME_CAMEL}Application.java" << STARTUP_CLASS
package com.njydsz.${MODULE_NAME/api}.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ${MODULE_NAME_CAMEL} 业务模块启动类
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = "com.njydsz.${MODULE_NAME/api}")
public class ${MODULE_NAME_CAMEL}Application {

    public static void main(String[] args) {
        SpringApplication.run(${MODULE_NAME_CAMEL}Application.class, args);
    }
}
STARTUP_CLASS

# =====================================================================
#  完成
# =====================================================================
echo ""
echo -e "${GREEN}✓ 模块生成完成!${NC}"
echo ""
echo -e "路径: ${BLUE}$TARGET_DIR${NC}"
echo -e ""
echo下一步:
echo "  1. 在 ydsz-backend/pom.xml 的 <modules> 中添加 <module>ydsz-${MODULE_NAME}</module>"
echo "  2. 启动基础设施: cd deploy/docker && docker compose -f docker-compose.dev.yml up -d"
echo "  3. 构建模块: mvn -pl ydsz-${MODULE_NAME} -am clean package"
echo "  4. 运行模块: mvn -pl ydsz-${MODULE_NAME}-web spring-boot:run"
