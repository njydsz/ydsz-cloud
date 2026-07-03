#!/bin/bash
# =====================================================================
#  PMIS 微服务 Docker 镜像批量构建脚本
# ---------------------------------------------------------------------
#  服务合并后共 8 个可部署服务（common/literule 为库不独立部署）：
#    gateway / iam / system / workflow / project / agent / scheduler
#  用法：
#    ./build-images.sh [tag]
#    默认 tag=1.0.0
#  产物：本地镜像，tag 形如 pmis-gateway:1.0.0
# =====================================================================
set -e

TAG="${1:-1.0.0}"
BACKEND_DIR="$(cd "$(dirname "$0")/../../ydsz-pmis-backend" && pwd)"

echo "=========================================="
echo "  PMIS Docker Build Pipeline"
echo "  TAG:    ${TAG}"
echo "  PWD:    ${BACKEND_DIR}"
echo "=========================================="

# 1. 构建基础镜像
echo "[1/3] Building base image: pmis-base:${TAG}"
cd "$(dirname "$0")"
docker build -f Dockerfile.base -t "pmis-base:${TAG}" .

# 2. 定义微服务的 artifactId → Dockerfile 映射（合并后 7 个可部署服务）
declare -A SERVICES=(
    ["ydsz-pmis-gateway"]="Dockerfile.gateway:9000"
    ["ydsz-pmis-iam"]="Dockerfile.service:9002"
    ["ydsz-pmis-workflow"]="Dockerfile.service:9004"
    ["ydsz-pmis-project"]="Dockerfile.service:9005"
    ["ydsz-pmis-agent"]="Dockerfile.service:9007"
    ["ydsz-pmis-system"]="Dockerfile.service:9008"
    ["ydsz-pmis-scheduler"]="Dockerfile.service:9012"
    # ydsz-pmis-common / ydsz-pmis-literule 为库，不独立部署
)

# 3. 为非网关服务生成统一 Dockerfile.service
if [ ! -f Dockerfile.service ]; then
    sed -e 's/ydsz-pmis-gateway/__SERVICE_NAME__/g' \
        -e 's/9000/__SERVER_PORT__/g' \
        -e 's/pmis-gateway/__APP_NAME__/g' \
        Dockerfile.gateway > Dockerfile.service.template
    sed -i 's/pmis-gateway/__APP_NAME__/g' Dockerfile.service.template
fi

# 4. 逐个构建（依赖父 pom install 完成）
cd "${BACKEND_DIR}"
for module in "${!SERVICES[@]}"; do
    IFS=':' read -ra parts <<< "${SERVICES[$module]}"
    DOCKERFILE="${parts[0]}"
    PORT="${parts[1]}"
    APP_NAME="${module#ydsz-pmis-}"
    
    echo "[2/3] Building ${module} (port ${PORT})"
    
    # 生成 Dockerfile.service
    sed -e "s/__SERVICE_NAME__/${module}/g" \
        -e "s/__SERVER_PORT__/${PORT}/g" \
        -e "s/__APP_NAME__/${APP_NAME}/g" \
        ../deploy/docker/Dockerfile.service.template > ../deploy/docker/Dockerfile.service
    
    # 进入模块目录用 maven 打包（如已打包则跳过）
    if [ ! -f "${module}/target/${module}-*.jar" ]; then
        echo "  - Running mvn package for ${module}..."
        (cd "${module}" && mvn clean package -DskipTests -q)
    fi
    
    # 复制 jar 到 deploy 临时目录（Docker COPY 需要）
    mkdir -p "../deploy/docker/build/${module}"
    cp "${module}/target/${module}"-*.jar "../deploy/docker/build/${module}/app.jar"
    
    # 构建镜像
    cd "../deploy/docker"
    docker build -f Dockerfile.service -t "pmis-${APP_NAME}:${TAG}" \
        --build-arg SERVICE_NAME="${module}" \
        --build-arg SERVER_PORT="${PORT}" \
        --build-arg APP_NAME="${APP_NAME}" \
        "build/${module}/"
    cd "${BACKEND_DIR}"
done

# 5. 清理
rm -rf "../deploy/docker/build" "../deploy/docker/Dockerfile.service" "../deploy/docker/Dockerfile.service.template"

echo "=========================================="
echo "  All 7 service images built successfully"
echo "  Base: pmis-base:${TAG}"
echo "=========================================="
docker images | grep "pmis-" | sort
