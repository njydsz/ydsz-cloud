# 启动基础环境
docker compose up -d

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f nacos

# 停止
docker compose down

# 停止并清理数据卷
docker compose down -v
