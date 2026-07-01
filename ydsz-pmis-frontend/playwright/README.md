# Playwright E2E

> 批次 21 / P1 - 3 条核心业务流 E2E

## 目录

```
playwright/
├── package.json         # @playwright/test 依赖
└── README.md            # 本文件
e2e/
├── fixtures/
│   ├── auth.fixture.ts  # 登录 / 角色切换
│   └── utils.ts         # 通用工具 (loading/dialog/toast)
├── 01-initiation-contract-closure.spec.ts  # E2E #1: 立项 → 合同 → 结项
├── 02-risk-alert.spec.ts                    # E2E #2: 风险预警
├── 03-ai-orchestration.spec.ts              # E2E #3: AI 编排
├── smoke.spec.ts                            # 冒烟测试
└── report/                                  # 输出报告
```

## 准备

```bash
# 安装 Playwright 测试框架
cd ydsz-pmis-frontend
pnpm add -D @playwright/test@1.48.0

# 安装浏览器 (一次性, 约 200MB)
pnpm exec playwright install chromium

# Linux 服务器还需要依赖
pnpm exec playwright install-deps chromium
```

## 跑测试

```bash
# 1) 启动后端 (一个终端)
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-gateway spring-boot:run

# 2) 启动前端 (另一个终端)
cd ydsz-pmis-frontend
pnpm run dev

# 3) 跑 E2E (第三个终端)
cd ydsz-pmis-frontend

# 跑全部 (headless)
pnpm exec playwright test

# 跑指定文件
pnpm exec playwright test e2e/01-initiation-contract-closure.spec.ts

# 调试模式 (UI 调试器)
pnpm exec playwright test --ui

# 显示浏览器
pnpm exec playwright test --headed

# 通过 API 登录 (更稳, 推荐)
E2E_LOGIN_VIA_API=1 pnpm exec playwright test

# 跑某个项目
pnpm exec playwright test --project=chromium
```

## 测试账号

| 角色 | 用户名 | 密码 | 用途 |
|------|--------|------|------|
| admin | admin | admin123 | 超管, 审批/AI 编排 |
| pm    | pm    | pm123    | PM, 立项/合同/风险登记 |
| cfo   | cfo   | cfo123   | CFO, 查看/财务审批 |
| risk  | risk  | risk123  | 风险管理员 |
| agent | agent | agent123 | AI Agent 角色 |

> 通过环境变量覆盖: `E2E_USER_ADMIN=xxx`, `E2E_PASS_ADMIN=xxx`

## 报告

```bash
# HTML 报告
pnpm exec playwright show-report

# 输出目录
e2e-report/
├── test-results/   # 失败用例的视频/截图/trace
├── report.json     # JSON 报告 (CI 解析)
└── index.html      # HTML 报告
```

## CI 集成

```yaml
# .github/workflows/e2e.yml
name: E2E
on: [push]
jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - uses: pnpm/action-setup@v4
        with: { version: 9 }
      - run: pnpm install
      - run: pnpm exec playwright install --with-deps chromium
      - run: pnpm run build
      - run: |
          pnpm exec playwright test \
            --reporter=github,html,json \
            E2E_BASE_URL=http://localhost:5173 \
            E2E_API_BASE_URL=http://localhost:9001
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: e2e-report
          path: e2e-report/
```

## 编写新用例

```ts
// e2e/your-flow.spec.ts
import { test, expect } from './fixtures/utils'
import { E2EUser } from './fixtures/auth.fixture'

test('你的业务流', async ({ page, loginAs }) => {
  await loginAs('admin' as E2EUser)
  await page.goto('/your/page')
  await expect(page.locator('.vxe-table')).toBeVisible()
  // ...
})
```

## 已知陷阱

1. **ElMessage 遮挡** — 用 `dismissMessages(page)` 在断言前清理
2. **Dialog 关闭后焦点丢失** — 用 `page.keyboard.press('Escape')` 主动关闭
3. **Vxe-table 行选择** — `.vxe-body--row` 选择器; 也支持 `.el-table__row` 兜底
4. **权限 disabled 按钮** — 断言 `toBeDisabled()` 而不是 not visible
5. **空数据状态** — `ElEmpty` 出现时, 表格行数 = 0, 单独写用例
6. **WebSocket 编排** — 等待时间需要 ≥ 5s, 避免 flaky
7. **中文文本断言** — 用 `hasText: /...正则.../i`, 兼容多语言

## 与 Vitest 单元测试的边界

| 维度 | Vitest | Playwright |
|------|--------|------------|
| 跑测速度 | < 1s / 用例 | 3-30s / 用例 |
| 渲染 | jsdom | 真实浏览器 |
| 异步 | 同步 mock | 真实网络 |
| 用例数 | 100+ | 5-10 关键链路 |
| 部署 | 每次 PR | 每日 + 关键 PR |
