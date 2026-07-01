# PMIS 前端 any 警告收口 SOP（批次 19 P2-4）

> 目标：消除 `pnpm lint` 报告的 79 个 any 警告，统一为强类型
> 工具：[scan-any-warnings.mjs](../../ydsz-pmis-frontend/scripts/scan-any-warnings.mjs)
> 类型工具：[api.ts](../../ydsz-pmis-frontend/src/types/api.ts)（ApiResponse / PageData / BusinessEntity）

## 1. 收口策略

### 1.1 分批路线图

| 批次 | 范围 | 数量 | 完成时间 | 负责人 |
|------|------|------|----------|--------|
| 批次 19 | API 层 + execute/reconcile/alert 页面 | ~30 | 本周 | 前端组 |
| 批次 20 | execute/cockpit/profit 页面 + composables | ~25 | 下周 | 前端组 |
| 批次 21 | 资源池/调度/系统管理 页面 | ~20 | 第 3 周 | 前端组 |
| 批次 22 | 兜底收口 | ~4 | 第 4 周 | 前端组 |

### 1.2 收口原则

1. **优先使用 src/types/api.ts 中的统一类型**：
   - `ApiResponse<T>` 替代 `Promise<any>`
   - `PageData<T>` 替代 `Promise<any[]>` 或 `Promise<{ records: any[] }>`
   - `BusinessEntity` 替代 `any` 类型对象

2. **业务类型从 src/api/*/types.ts 引入**：
   ```ts
   import type { ProjectInitiation } from '@/api/project/initiation/types'
   import type { ApiResponse, PageData } from '@/types/api'
   
   // Before
   const fetchData = (): Promise<any> => ...
   
   // After
   const fetchData = (): Promise<ApiResponse<PageData<ProjectInitiation>>> => ...
   ```

3. **表格列定义用 ColumnDef<T>**：
   ```ts
   import type { TableColumnCtx } from 'element-plus'
   
   const columns: TableColumnCtx<ProjectInitiation>[] = [...]
   ```

4. **ECharts option 用 EChartsOption**：
   ```ts
   import type { EChartsOption } from 'echarts'
   const option: EChartsOption = { ... }
   ```

5. **避免 any 的常见替代**：
   - `unknown` + 类型守卫（zod / 自定义 validator）
   - `Record<string, T>` 替代 `any` 字典
   - `Partial<T>` 替代 `any` 局部
   - `Pick<T, K>` 替代 `any` 子集

### 1.3 ESLint 规则

[.eslintrc.cjs](../../ydsz-pmis-frontend/.eslintrc.cjs) 已升级：
- `@typescript-eslint/no-explicit-any`: `warn` → **`error`**
- 新增 4 个 `no-unsafe-*` 警告（连带规则）
- 过渡期允许 `src/api/**/index.ts` + `src/views/execution/{reconcile,alert}/**` 保留 `warn`

### 1.4 工具脚本

```bash
# 扫描所有 any 警告
node scripts/scan-any-warnings.mjs

# 仅看 Top 10
node scripts/scan-any-warnings.mjs --top=10

# 输出 JSON（CI 集成用）
node scripts/scan-any-warnings.mjs --json > any-report.json
```

## 2. 收口示例

### 2.1 通用 API 调用

```ts
// Before
export function getProjectList(params: any): Promise<any> {
  return request.get('/api/v1/project/initiation/page', { params })
}

// After
import type { ProjectInitiation } from './types'
import type { PageData, ApiResponse } from '@/types/api'

export function getProjectList(params: PageQuery): Promise<ApiResponse<PageData<ProjectInitiation>>> {
  return request.get('/api/v1/project/initiation/page', { params })
}
```

### 2.2 表格列定义

```ts
// Before
const columns: any[] = [
  { prop: 'name', label: '项目名称' },
  { prop: 'amount', label: '金额' }
]

// After
import type { TableColumnCtx } from 'element-plus'
const columns: TableColumnCtx<ProjectInitiation>[] = [
  { prop: 'name', label: '项目名称' },
  { prop: 'amount', label: '金额' }
]
```

### 2.3 ECharts 配置

```ts
// Before
const option = {
  xAxis: { type: 'category', data: [] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar' }]
}

// After
import type { EChartsOption } from 'echarts'
const option: EChartsOption = {
  xAxis: { type: 'category', data: [] },
  yAxis: { type: 'value' },
  series: [{ type: 'bar' }]
}
```

### 2.4 字典 / 枚举

```ts
// Before
const statusMap: any = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  CLOSED: '已结项'
}

// After
import type { ProjectStatus } from '@/api/project/initiation/types'
const statusMap: Record<ProjectStatus, string> = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  CLOSED: '已结项'
}
```

### 2.5 复杂对象

```ts
// Before
function parseFormData(data: any): any {
  return data.map((item: any) => ({
    id: item.id,
    name: item.name
  }))
}

// After
interface RawFormItem {
  id: number
  name: string
  [key: string]: unknown
}

interface FormItem {
  id: number
  name: string
}

function parseFormData(data: RawFormItem[]): FormItem[] {
  return data.map((item) => ({
    id: item.id,
    name: item.name
  }))
}
```

## 3. 验证

### 3.1 自动化验证

```bash
# 收口后必须全绿
cd ydsz-pmis-frontend
pnpm lint
# 期望：0 errors, 0 warnings（除过渡期白名单外）

pnpm test
# 期望：所有单测通过
```

### 3.2 CI 集成

```yaml
lint-frontend:
  stage: test
  script:
    - cd ydsz-pmis-frontend
    - node scripts/scan-any-warnings.mjs
    - pnpm lint
  allow_failure: false
```

## 4. 验收依据

- [开发计划 P2 阶段质量验收](../../开发计划.md) - 79 个 any 警告收口 100%
- pnpm lint 输出：0 errors, 0 warnings
- scan-any-warnings.mjs 退出码：0
