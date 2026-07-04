#!/usr/bin/env bash
# =============================================================================
#  YDSZ PMIS · Ubuntu 一键安装脚本（8 个中间件）
# -----------------------------------------------------------------------------
#  适用:    Ubuntu 20.04 / 22.04 / 24.04 (x86_64 / arm64)
#  权限:    需要 root 权限（脚本内自动 sudo）
#  用法:    sudo ./install-pmis-infra.sh [--no-start] [--skip postgres,redis,...]
#           --no-start  : 只安装不启动
#           --skip      : 跳过指定中间件（逗号分隔）
#           --uninstall : 卸载全部（保留数据卷）
#  说明:    默认安装到 /opt，安装包下载到 /tmp/pmis-install
# =============================================================================
set -e

# ---------- 默认配置 ----------
INSTALL_HOME=${PMIS_INFRA_HOME:-/opt}
DOWNLOAD_DIR=/tmp/pmis-install
DATA_DIR=/var/lib/pmis
LOG_DIR=/var/log/pmis
SERVICE_USER=${SERVICE_USER:-pmis}
JAVA_HOME=${JAVA_HOME:-/opt/jdk-21}

# ---------- 版本 ----------
PG_VERSION=18
REDIS_VERSION=7.4
NACOS_VERSION=2.4.3
MINIO_RELEASE=2025-04-01
SEATA_VERSION=2.5.0
ROCKETMQ_VERSION=5.3.2
XXL_JOB_VERSION=2.4.2
ES_VERSION=8.15.3

SKIP_LIST=""
NO_START=0
UNINSTALL=0
for arg in "$@"; do
  case $arg in
    --no-start) NO_START=1 ;;
    --skip=*)   SKIP_LIST="${arg#--skip=}"; SKIP_LIST="${SKIP_LIST//,/ }" ;;
    --uninstall) UNINSTALL=1 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# ---------- 颜色 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; }
fatal(){ err "$@"; exit 1; }

should_skip() {
  for s in $SKIP_LIST; do [[ "$s" == "$1" ]] && return 0; done
  return 1
}

# ---------- root 检查 ----------
[[ $EUID -eq 0 ]] || fatal "请用 root 运行：sudo $0"

# ---------- 系统检测 ----------
log "系统信息：$(uname -a)"
. /etc/os-release
log "发行版：$PRETTY_NAME"
[[ "$ID" == "ubuntu" || "$ID" == "debian" ]] || fatal "本脚本只支持 Ubuntu/Debian"

ARCH=$(uname -m)
log "架构：$ARCH"

# ---------- 创建用户和目录 ----------
log "创建系统用户 $SERVICE_USER..."
id -u $SERVICE_USER >/dev/null 2>&1 || useradd -r -s /bin/bash $SERVICE_USER
mkdir -p $DOWNLOAD_DIR $DATA_DIR $LOG_DIR
chown -R $SERVICE_USER:$SERVICE_USER $DATA_DIR $LOG_DIR

# ---------- 通用工具 ----------
log "更新 apt 源..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl wget gnupg lsb-release ca-certificates netcat-openbsd unzip

# =============================================================================
#  JDK 21（如已安装则跳过）
# =============================================================================
install_jdk() {
  if should_skip jdk; then return; fi
  if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q '"21'; then
    log "安装 OpenJDK 21..."
    apt-get install -y -qq openjdk-21-jdk
  fi
  ok "JDK 21 已就绪：$(java -version 2>&1 | head -n1)"
}

# =============================================================================
#  PostgreSQL 18
# =============================================================================
install_postgres() {
  if should_skip postgres; then return; fi
  log "安装 PostgreSQL 18..."

  # 添加 PGDG 源
  if [[ ! -f /etc/apt/sources.list.d/pgdg.list ]]; then
    echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list
    wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /etc/apt/trusted.gpg.d/pgdg.gpg
    apt-get update -qq
  fi

  apt-get install -y -qq postgresql-18 postgresql-client-18

  # 替换配置
  PG_CONF="/etc/postgresql/18/main"
  cp $PG_CONF/postgresql.conf $PG_CONF/postgresql.conf.bak
  cp "$(dirname "$0")/../infra/postgres/postgresql.conf" $PG_CONF/postgresql.conf
  cp "$(dirname "$0")/../infra/postgres/pg_hba.conf" $PG_CONF/pg_hba.conf
  chown postgres:postgres $PG_CONF/postgresql.conf $PG_CONF/pg_hba.conf

  # 启动
  systemctl enable postgresql
  systemctl start postgresql
  sleep 2

  # 创建数据库与用户
  sudo -u postgres psql <<EOF
CREATE USER pmis WITH PASSWORD 'pmis123';
CREATE DATABASE ydsz_pmis OWNER pmis ENCODING 'UTF8';
GRANT ALL PRIVILEGES ON DATABASE ydsz_pmis TO pmis;
EOF

  # 导入初始化数据
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -f "$SCRIPT_DIR/../../../docs/V1.0.0.sql" ]]; then
    log "导入初始化 SQL（约 1-2 分钟）..."
    PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis -f "$SCRIPT_DIR/../../../docs/V1.0.0.sql" 2>&1 | tail -3
  fi

  ok "PostgreSQL 18 安装完成"
}

# =============================================================================
#  Redis 7
# =============================================================================
install_redis() {
  if should_skip redis; then return; fi
  log "安装 Redis 7..."

  apt-get install -y -qq redis-server

  # 替换配置
  cp /etc/redis/redis.conf /etc/redis/redis.conf.bak
  cp "$(dirname "$0")/../infra/redis/redis.conf" /etc/redis/redis.conf
  chown redis:redis /etc/redis/redis.conf

  systemctl enable redis-server
  systemctl restart redis-server
  sleep 1

  ok "Redis 7 安装完成"
}

# =============================================================================
#  Nacos 2.4
# =============================================================================
install_nacos() {
  if should_skip nacos; then return; fi
  log "安装 Nacos $NACOS_VERSION..."

  cd $DOWNLOAD_DIR
  Nacos_file="nacos-server-$NACOS_VERSION.tar.gz"
  if [[ ! -f $Nacos_file ]]; then
    wget -q "https://github.com/alibaba/nacos/releases/download/$NACOS_VERSION/$Nacos_file"
  fi
  tar -xzf $Nacos_file -C $INSTALL_HOME
  mv $INSTALL_HOME/nacos $INSTALL_HOME/nacos 2>/dev/null || true
  ln -sfn $INSTALL_HOME/nacos/bin /usr/local/bin/nacos-bin

  # 配置
  NACOS_HOME=$INSTALL_HOME/nacos
  cp "$Nacos_HOME/conf/application.properties" "$Nacos_HOME/conf/application.properties.bak"
  cp "$(dirname "$0")/../infra/nacos/application.properties" "$Nacos_HOME/conf/application.properties"

  # 创建 systemd 单元
  cat > /etc/systemd/system/nacos.service <<EOF
[Unit]
Description=Nacos Server
After=network.target

[Service]
Type=forking
User=$SERVICE_USER
Group=$SERVICE_USER
Environment=JAVA_HOME=$JAVA_HOME
ExecStart=$NACOS_HOME/bin/startup.sh -m standalone
ExecStop=$NACOS_HOME/bin/shutdown.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable nacos

  if [[ $NO_START -eq 0 ]]; then
    systemctl start nacos
    log "等待 Nacos 启动..."
    for i in {1..30}; do
      if curl -sf http://127.0.0.1:8848/nacos/actuator/health >/dev/null 2>&1; then
        ok "Nacos 已启动"; return
      fi
      sleep 2
    done
    warn "Nacos 启动超时"
  else
    ok "Nacos 已安装（未启动）"
  fi
}

# =============================================================================
#  MinIO
# =============================================================================
install_minio() {
  if should_skip minio; then return; fi
  log "安装 MinIO..."

  if [[ ! -f /usr/local/bin/minio ]]; then
    wget -q "https://dl.min.io/server/minio/release/linux-amd64/minio" -O /usr/local/bin/minio
    chmod +x /usr/local/bin/minio
  fi

  mkdir -p $DATA_DIR/minio/data $LOG_DIR/minio
  chown -R $SERVICE_USER:$SERVICE_USER $DATA_DIR/minio $LOG_DIR/minio

  cat > /etc/systemd/system/minio.service <<EOF
[Unit]
Description=MinIO Object Storage
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
Environment="MINIO_ROOT_USER=minioadmin"
Environment="MINIO_ROOT_PASSWORD=minioadmin"
ExecStart=/usr/local/bin/minio server $DATA_DIR/minio/data --console-address ":9001"
Restart=on-failure
RestartSec=10
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable minio
  [[ $NO_START -eq 0 ]] && systemctl start minio

  ok "MinIO 安装完成（API:9100 / Console:9101）"
}

# =============================================================================
#  Seata 2.5
# =============================================================================
install_seata() {
  if should_skip seata; then return; fi
  log "安装 Seata $SEATA_VERSION..."

  cd $DOWNLOAD_DIR
  if [[ ! -d $INSTALL_HOME/seata ]]; then
    wget -q "https://github.com/apache/incubator-seata/releases/download/v$SEATA_VERSION/apache-seata-$SEATA_VERSION-incubating-bin.tar.gz" -O seata.tgz
    tar -xzf seata.tgz -C $INSTALL_HOME
    mv $INSTALL_HOME/apache-seata-$SEATA_VERSION-incubating $INSTALL_HOME/seata
  fi

  SEATA_HOME=$INSTALL_HOME/seata
  cp "$SEATA_HOME/conf/application.yml" "$SEATA_HOME/conf/application.yml.bak"
  cp "$SEATA_HOME/conf/file.conf" "$SEATA_HOME/conf/file.conf.bak"
  cp "$SEATA_HOME/conf/registry.conf" "$SEATA_HOME/conf/registry.conf.bak"
  cp "$(dirname "$0")/../infra/seata/application.yml" "$SEATA_HOME/conf/application.yml"
  cp "$(dirname "$0")/../infra/seata/file.conf" "$SEATA_HOME/conf/file.conf"
  cp "$(dirname "$0")/../infra/seata/registry.conf" "$SEATA_HOME/conf/registry.conf"

  cat > /etc/systemd/system/seata.service <<EOF
[Unit]
Description=Seata Distributed Transaction Server
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
Environment=JAVA_HOME=$JAVA_HOME
ExecStart=$SEATA_HOME/bin/seata-server.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable seata
  [[ $NO_START -eq 0 ]] && systemctl start seata

  ok "Seata 安装完成（端口 8091 / 控制台 7091）"
}

# =============================================================================
#  RocketMQ 5.x
# =============================================================================
install_rocketmq() {
  if should_skip rocketmq; then return; fi
  log "安装 RocketMQ $ROCKETMQ_VERSION..."

  cd $DOWNLOAD_DIR
  if [[ ! -d $INSTALL_HOME/rocketmq ]]; then
    wget -q "https://dist.apache.org/repos/dist/release/rocketmq/$ROCKETMQ_VERSION/rocketmq-all-$ROCKETMQ_VERSION-bin-release.zip" -O rocketmq.zip
    unzip -q rocketmq.zip -d $INSTALL_HOME
    mv $INSTALL_HOME/rocketmq-all-$ROCKETMQ_VERSION-bin-release $INSTALL_HOME/rocketmq
  fi

  ROCKETMQ_HOME=$INSTALL_HOME/rocketmq
  cp "$ROCKETMQ_HOME/conf/broker.conf" "$ROCKETMQ_HOME/conf/broker.conf.bak"
  cp "$(dirname "$0")/../infra/rocketmq/broker.conf" "$ROCKETMQ_HOME/conf/broker.conf"

  mkdir -p $DATA_DIR/rocketmq/store $LOG_DIR/rocketmq
  chown -R $SERVICE_USER:$SERVICE_USER $DATA_DIR/rocketmq $LOG_DIR/rocketmq
  sed -i "s|/opt/rocketmq|$DATA_DIR/rocketmq|g" $ROCKETMQ_HOME/conf/broker.conf

  # NameServer systemd
  cat > /etc/systemd/system/rocketmq-namesrv.service <<EOF
[Unit]
Description=RocketMQ NameServer
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
Environment=JAVA_HOME=$JAVA_HOME
Environment=JAVA_OPT="-Xms512m -Xmx512m"
ExecStart=$ROCKETMQ_HOME/bin/mqnamesrv
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  # Broker systemd
  cat > /etc/systemd/system/rocketmq-broker.service <<EOF
[Unit]
Description=RocketMQ Broker
After=rocketmq-namesrv.service

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
Environment=JAVA_HOME=$JAVA_HOME
Environment=JAVA_OPT="-Xms512m -Xmx512m"
ExecStart=$ROCKETMQ_HOME/bin/mqbroker -n 127.0.0.1:9876 -c $ROCKETMQ_HOME/conf/broker.conf
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable rocketmq-namesrv rocketmq-broker
  [[ $NO_START -eq 0 ]] && {
    systemctl start rocketmq-namesrv
    sleep 5
    systemctl start rocketmq-broker
  }

  ok "RocketMQ 安装完成（NameServer:9876 / Broker:10911）"
}

# =============================================================================
#  XXL-Job 2.4
# =============================================================================
install_xxl_job() {
  if should_skip xxl-job; then return; fi
  log "安装 XXL-Job $XXL_JOB_VERSION..."

  cd $DOWNLOAD_DIR
  if [[ ! -f xxl-job-admin-$XXL_JOB_VERSION.jar ]]; then
    wget -q "https://github.com/xuxueli/xxl-job/releases/download/$XXL_JOB_VERSION/xxl-job-admin-$XXL_JOB_VERSION.jar"
  fi

  mkdir -p $INSTALL_HOME/xxl-job
  cp xxl-job-admin-$XXL_JOB_VERSION.jar $INSTALL_HOME/xxl-job/
  cp "$(dirname "$0")/../infra/xxl-job/application.properties" $INSTALL_HOME/xxl-job/

  # 初始化数据库
  PGPASSWORD=pmis123 psql -h 127.0.0.1 -U pmis -d ydsz_pmis -f "$(dirname "$0")/../infra/xxl-job/tables_xxl_job_pg.sql" 2>&1 | tail -5

  cat > /etc/systemd/system/xxl-job.service <<EOF
[Unit]
Description=XXL-Job Admin
After=network.target postgresql.service

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
Environment=JAVA_HOME=$JAVA_HOME
WorkingDirectory=$INSTALL_HOME/xxl-job
ExecStart=$JAVA_HOME/bin/java -Xms512m -Xmx512m -jar $INSTALL_HOME/xxl-job/xxl-job-admin-$XXL_JOB_VERSION.jar --spring.config.location=$INSTALL_HOME/xxl-job/application.properties
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable xxl-job
  [[ $NO_START -eq 0 ]] && systemctl start xxl-job

  ok "XXL-Job 安装完成（端口 9100，admin/123456）"
}

# =============================================================================
#  Elasticsearch 8.15
# =============================================================================
install_elasticsearch() {
  if should_skip elasticsearch; then return; fi
  log "安装 Elasticsearch $ES_VERSION..."

  if [[ ! -d $INSTALL_HOME/elasticsearch ]]; then
    cd $DOWNLOAD_DIR
    wget -q "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$ES_VERSION-linux-x86_64.tar.gz"
    tar -xzf elasticsearch-$ES_VERSION-linux-x86_64.tar.gz -C $INSTALL_HOME
    mv $INSTALL_HOME/elasticsearch-$ES_VERSION $INSTALL_HOME/elasticsearch
  fi

  ES_HOME=$INSTALL_HOME/elasticsearch
  cp "$ES_HOME/config/elasticsearch.yml" "$ES_HOME/config/elasticsearch.yml.bak"
  cp "$(dirname "$0")/../infra/elasticsearch/elasticsearch.yml" "$ES_HOME/config/elasticsearch.yml"
  mkdir -p $ES_HOME/config/jvm.options.d
  cp "$(dirname "$0")/../infra/elasticsearch/jvm.options.d/heap.options" $ES_HOME/config/jvm.options.d/

  # ES 不能以 root 启动
  chown -R $SERVICE_USER:$SERVICE_USER $ES_HOME

  # 修改启动用户
  sed -i "s|^ES_USER=.*|ES_USER=$SERVICE_USER|" $ES_HOME/bin/elasticsearch

  cat > /etc/systemd/system/elasticsearch.service <<EOF
[Unit]
Description=Elasticsearch
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
LimitNOFILE=65535
LimitNPROC=4096
LimitMEMLOCK=infinity
Environment=JAVA_HOME=$JAVA_HOME
Environment=ES_JAVA_OPTS="-Xms512m -Xmx512m"
ExecStart=$ES_HOME/bin/elasticsearch
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable elasticsearch
  [[ $NO_START -eq 0 ]] && systemctl start elasticsearch

  ok "Elasticsearch 安装完成（端口 9200）"
}

# =============================================================================
#  卸载
# =============================================================================
uninstall_all() {
  log "卸载全部中间件..."

  for svc in elasticsearch xxl-job rocketmq-broker rocketmq-namesrv seata minio nacos; do
    systemctl stop $svc 2>/dev/null || true
    systemctl disable $svc 2>/dev/null || true
    rm -f /etc/systemd/system/$svc.service
  done
  systemctl daemon-reload

  apt-get purge -y -qq postgresql-18 redis-server 2>/dev/null || true
  apt-get autoremove -y -qq 2>/dev/null || true

  rm -rf $INSTALL_HOME/nacos $INSTALL_HOME/seata $INSTALL_HOME/rocketmq $INSTALL_HOME/xxl-job $INSTALL_HOME/elasticsearch
  rm -f /usr/local/bin/minio
  rm -rf $DATA_DIR/minio $DATA_DIR/rocketmq

  ok "全部中间件已卸载（数据保留在 $DATA_DIR）"
}

# =============================================================================
#  主流程
# =============================================================================
echo "============================================================"
echo "  YDSZ PMIS · Ubuntu 中间件一键安装"
echo "  安装位置: $INSTALL_HOME"
echo "  数据目录: $DATA_DIR"
echo "  日志目录: $LOG_DIR"
echo "============================================================"

if [[ $UNINSTALL -eq 1 ]]; then
  uninstall_all
  exit 0
fi

install_jdk
install_postgres
install_redis
install_nacos
install_minio
install_seata
install_rocketmq
install_xxl_job
install_elasticsearch

echo
echo "============================================================"
ok "全部中间件安装完成！"
echo
echo "下一步:"
echo "  1. 检查状态:  deploy/ubuntu/infra-manager.sh status"
echo "  2. 导入 Nacos 共享配置:  deploy/ubuntu/scripts/import-nacos-config.sh"
echo "  3. 启动后端:  deploy/ubuntu/scripts/start-all.sh"
echo "============================================================"
