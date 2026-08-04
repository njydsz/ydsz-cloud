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
# 使用包含 brotli 模块的社区镜像，使 nginx.conf 中的 brotli_static/brotli on 配置生效
# fholzer/nginx-brotli 基于 nginx:stable-alpine 并预装 ngx_brotli 模块
FROM fholzer/nginx-brotli:stable-alpine AS production

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
COPY bash/deploy/nginx-sub-apps.conf /etc/nginx/nginx-sub-apps.conf

EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
