# ==================== Builder Stage ====================
FROM node:22-slim AS builder

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
ENV NODE_OPTIONS=--max-old-space-size=8192
ENV TZ=Asia/Shanghai

RUN npm i -g corepack

WORKDIR /app

# Copy workspace files
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* turbo.json ./
COPY cspell.json eslint.config.mjs lefthook.yml stylelint.config.mjs vitest.config.ts vitest.workspace.ts ./

# Copy conf/ and comm/ (shared packages)
COPY conf/ ./conf/
COPY comm/ ./comm/

# Copy all apps
COPY main/ ./main/
COPY apps/ ./apps/

# Install dependencies
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

# Build all apps via turbo
RUN pnpm run build

RUN echo "Builder Success 🎉"

# ==================== Production Stage ====================
FROM nginx:stable-alpine AS production

# Enable brotli (需要 nginx-brotli 模块，nginx:stable-alpine 不含此模块)
# 选项 A: 改用 nginx:stable-alpine-perl 并安装 nginx-module-brotli
#   FROM nginx:stable-alpine AS production
#   RUN apk add --no-cache nginx-module-brotli
# 选项 B: 使用包含 brotli 的社区镜像
#   FROM fholzer/nginx-brotli:latest AS production
# 当前 bazh/deploy/nginx.conf 已配置 brotli_static on，
# 切换到此镜像后即可生效。
# 不阻塞部署：缺少 brotli 模块不影响运行，nginx 仅跳过 brotli config。

# Fix MIME type for .mjs files
RUN echo "types { application/javascript js mjs; }" > /etc/nginx/conf.d/mjs.conf \
    && rm -rf /etc/nginx/conf.d/default.conf

# Copy main app (host)
COPY --from=builder /app/main/dist /usr/share/nginx/html/main

# Copy all sub-apps
COPY --from=builder /app/apps/userinfo-web/dist /usr/share/nginx/html/ydsz-userinfo-web
COPY --from=builder /app/apps/system-web/dist /usr/share/nginx/html/ydsz-system-web
COPY --from=builder /app/apps/project-web/dist /usr/share/nginx/html/ydsz-project-web
COPY --from=builder /app/apps/message-web/dist /usr/share/nginx/html/ydsz-message-web
COPY --from=builder /app/apps/cronjob-web/dist /usr/share/nginx/html/ydsz-cronjob-web
COPY --from=builder /app/apps/workflow-web/dist /usr/share/nginx/html/ydsz-workflow-web
COPY --from=builder /app/apps/nextwiki-web/dist /usr/share/nginx/html/ydsz-nextwiki-web
COPY --from=builder /app/apps/literule-web/dist /usr/share/nginx/html/ydsz-literule-web
COPY --from=builder /app/apps/agent-web/dist /usr/share/nginx/html/ydsz-agent-web

# Copy nginx configuration
COPY bash/deploy/nginx.conf /etc/nginx/nginx.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
