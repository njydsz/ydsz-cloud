# =============================================================================
#  YDSZ PMIS · 前端 Dockerfile（多阶段构建：Node 构建 + Nginx 运行）
# -----------------------------------------------------------------------------
#  构建示例:
#    docker build -t ydsz-pmis/frontend:v1.3.0 \
#      --build-arg API_BASE=/api \
#      -f ydsz-pmis-frontend/Dockerfile ydsz-pmis-frontend/
#
#  设计要点（对齐阿里/字节前端容器化规范）:
#    1. 多阶段构建，构建期 Node + 源码，运行期仅 Nginx + 静态资源
#    2. 运行镜像 ≤ 50MB（nginx:alpine）
#    3. 显式非 root 用户（nginx 用户）
#    4. pnpm 包管理器（与项目一致）
#    5. BuildKit 缓存挂载加速 pnpm install
#    6. gzip/brotli 预压缩（Nginx 静态启用）
#    7. SPA history 模式 fallback
#    8. 健康检查
# =============================================================================
# syntax=docker/dockerfile:1.7

# -----------------------------------------------------------------------------
#  Stage 1: Builder（pnpm 构建）
# -----------------------------------------------------------------------------
FROM node:20-alpine AS builder

ARG API_BASE=http://ydsz-pmis-gateway:9000
ARG NODE_ENV=production

ENV NODE_ENV=${NODE_ENV} \
    PNPM_HOME=/pnpm \
    PATH=/pnpm:$PATH \
    CI=true

WORKDIR /build

# 启用 pnpm + corepack（Node 20 内置）
RUN corepack enable && corepack prepare pnpm@9.12.3 --activate

# 先拷贝 lockfile 和 package.json，利用 Docker 层缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY index.html ./

# 安装依赖（BuildKit 缓存挂载）
RUN --mount=type=cache,target=/pnpm/store \
    pnpm install --frozen-lockfile --ignore-scripts

# 拷贝源码
COPY . .

# 构建产物（vite build 输出到 dist/）
ENV VITE_API_BASE_URL=${API_BASE}
RUN pnpm run build:prod

# -----------------------------------------------------------------------------
#  Stage 2: Runtime（Nginx 静态服务）
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="ydsz-pmis-frontend" \
      org.opencontainers.image.source="https://github.com/njydsz/ydsz-pmis" \
      org.opencontainers.image.licenses="UNLICENSED" \
      org.opencontainers.image.description="南京云顶 PMIS · 前端 Nginx 静态服务"

# 安装 curl（健康检查）+ tzdata
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    rm -rf /var/cache/apk/*

# Nginx 配置：SPA fallback + gzip + 静态资源缓存 + 反向代理 /api → gateway
RUN cat > /etc/nginx/conf.d/default.conf <<'NGINX_EOF'
server {
    listen       80;
    server_name  _;

    # 安全头（OWASP 推荐）
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;

    # gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_min_length 1024;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/javascript application/javascript application/json application/xml image/svg+xml;

    root   /usr/share/nginx/html;
    index  index.html;

    # SPA history 模式 fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源长缓存（hash 文件名，可安全长缓存）
    location ~* \.(?:js|css|woff2?|ttf|eot|svg|png|jpg|jpeg|gif|ico|webp)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # index.html 不缓存（保证发布即时生效）
    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        expires 0;
    }

    # 反向代理 API → 网关（K8s Service DNS）
    location /api/ {
        proxy_pass http://ydsz-pmis-gateway:9000/;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout    60s;
        proxy_send_timeout    60s;
    }

    # 健康检查端点
    location = /healthz {
        access_log off;
        return 200 "ok\n";
        add_header Content-Type text/plain;
    }
}
NGINX_EOF

# 拷贝构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 非 root 运行（nginx 用户 UID 101）
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown nginx:nginx /var/run/nginx.pid

USER nginx

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD curl -sf http://127.0.0.1/healthz || exit 1
