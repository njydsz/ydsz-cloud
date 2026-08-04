# 工作流引擎版本 Diff 增强设计

> P2-优化：工作流引擎能力增强 — 版本级差量比较

## 目录

- [需求背景](#需求背景)
- [设计理念](#设计理念)
- [核心数据结构](#核心数据结构)
- [Diff 算法设计](#diff-算法设计)
- [REST API 设计](#rest-api-设计)
- [前端集成方案](#前端集成方案)
- [数据库变更](#数据库变更)
- [实施计划](#实施计划)

---

## 需求背景

当前 ydsz-workflow 引擎支持 BPMN 2.0 流程部署，但缺乏流程定义级别的版本管理与变更追溯能力。当业务方迭代流程时，常见痛点包括：

1. **变更不可见**：无法直观看到 v1.0 到 v1.1 改了哪些节点
2. **审计缺失**：合规审计需要记录每次部署的变更内容
3. **回滚困难**：需要基于版本差异定位问题来源
4. **协作困难**：多人并行编辑流程时，无法评估冲突

### 业务场景

- 业务管理员修改审批流后，需查看变更明细方可发布
- 审计团队要求导出最近一季度的所有流程变更记录
- 线上问题排查时快速定位到影响问题的流程版本

---

## 设计理念

### 设计目标

1. **结构级 Diff**：比较 BPMN XML 中的节点、连线、属性变化
2. **语义级 Diff**：将技术级的 XML 变化转化为业务易懂的描述
3. **可视化渲染**：前端高亮显示增删改的元素
4. **变更记录**：持久化每次版本变更记录

### 对比业界方案

| 方案 | 特点 | 适用性 |
|------|------|--------|
| Git 风格文本 Diff | 简单 BPMN XML 文本差异 | 技术向，不可读 |
| BPMN.io 模型树 Diff | 结构化节点对比 | **推荐** |
| Flowable 原生历史 | 仅记录部署元信息 | 需二次开发 |
| 自研语义 Diff | 业务语义转换后对比 | **终极方案** |

---

## 核心数据结构

### 流程定义版本表

```sql
CREATE TABLE ydsz_flow_definition_version (
    id              BIGSERIAL PRIMARY KEY,
    definition_id   BIGINT          NOT NULL COMMENT '流程定义 ID (ydsz_flow_definition.id)',
    definition_key  VARCHAR(64)     NOT NULL COMMENT '流程标识 (如 leave_apply)',
    version         INT             NOT NULL COMMENT '版本号 (自增)',
    name            VARCHAR(255)    NOT NULL COMMENT '流程名称',
    description     TEXT            COMMENT '版本描述',
    bpmn_xml        TEXT            NOT NULL COMMENT 'BPMN 2.0 XML 内容',
    bpmn_json       JSONB           NOT NULL COMMENT 'BPMN 模型 JSON (前端渲染用)',
    checksum        VARCHAR(64)     NOT NULL COMMENT 'MD5 校验和',
    change_type     VARCHAR(20)     NOT NULL COMMENT '部署类型: CREATE/UPDATE/REDEPLOY',
    change_log      TEXT            COMMENT '变更摘要（人工填写）',
    deployed_by     BIGINT          NOT NULL COMMENT '部署人',
    deployed_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (definition_key, version),
    INDEX idx_definition_key (definition_key),
    INDEX idx_deployed_at (deployed_at)
);

COMMENT ON TABLE ydsz_flow_definition_version IS '流程定义版本存档表';
```

### 版本差异表

```sql
CREATE TABLE ydsz_flow_definition_diff (
    id                    BIGSERIAL PRIMARY KEY,
    definition_key        VARCHAR(64)     NOT NULL,
    from_version          INT             NOT NULL COMMENT '起始版本',
    to_version            INT             NOT NULL COMMENT '目标版本',
    diff_json             JSONB           NOT NULL COMMENT '结构化 diff 结果',
    diff_summary          JSONB           NOT NULL COMMENT '按类别聚合的概要统计',
    diff_pretty_html      TEXT            COMMENT '渲染用 HTML（前端高亮展示）',
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (definition_key, from_version, to_version),
    INDEX idx_def_versions (definition_key, from_version, to_version)
);

COMMENT ON TABLE ydsz_flow_definition_diff IS '流程版本差异归档';
```

### Diff 结果 JSON 结构

```jsonc
{
  "definitionKey": "leave_apply",
  "fromVersion": 2,
  "toVersion": 3,
  "timestamp": "2026-08-04T10:30:00Z",
  "summary": {
    "nodesAdded": 2,
    "nodesRemoved": 1,
    "nodesModified": 3,
    "edgesAdded": 2,
    "edgesRemoved": 1,
    "edgesModified": 1,
    "propertiesChanged": 5,
    "hasBreakingChange": true
  },
  "changes": [
    {
      "category": "NODE",
      "action": "ADDED",
      "elementId": "Activity_HR_Review",
      "elementName": "HR 复审",
      "elementType": "userTask",
      "after": {
        "id": "Activity_HR_Review",
        "name": "HR 复审",
        "assignee": "${hr_leader}",
        "priority": "normal"
      }
    },
    {
      "category": "NODE",
      "action": "REMOVED",
      "elementId": "Activity_TeamLead_Review",
      "elementName": "组长审批",
      "elementType": "userTask",
      "before": {
        "id": "Activity_TeamLead_Review",
        "name": "组长审批",
        "assignee": "${team_lead}"
      }
    },
    {
      "category": "NODE",
      "action": "MODIFIED",
      "elementId": "Activity_Manager_Approve",
      "elementName": "经理审批",
      "elementType": "userTask",
      "diff": {
        "assignee": {
          "from": "${manager}",
          "to": "${manager_v2}",
          "semantic": "审批人表达式变更"
        },
        "priority": {
          "from": "normal",
          "to": "high",
          "semantic": "优先级提升"
        },
        "condition": {
          "from": "${amount > 1000}",
          "to": "${amount > 2000}",
          "semantic": "审批金额阈值从 1000 提高到 2000（⚠️ 业务影响较大）"
        }
      }
    },
    {
      "category": "EDGE",
      "action": "MODIFIED",
      "elementId": "Flow_1",
      "diff": {
        "condition": {
          "from": "${result == 'agree'}",
          "to": "${result == 'agree' && urgent == false}",
          "semantic": "紧急流程跳过条件变更"
        }
      }
    },
    {
      "category": "PROCESS",
      "action": "MODIFIED",
      "elementId": "Process_leave_apply",
      "diff": {
        "candidateStarterGroups": {
          "from": "[\"employee\"]",
          "to": "[\"employee\",\"contractor\"]",
          "semantic": "新增合同工可发起"
        }
      }
    }
  ],
  "impacts": [
    {
      "level": "WARNING",
      "message": "审批金额阈值从 1000 提高到 2000，将对现有审批中流程产生回溯影响",
      "affectedInstances": 15
    },
    {
      "level": "INFO",
      "message": "新增 HR 复审节点，后续流程自动路由",
      "affectedInstances": 0
    }
  ]
}
```

---

## Diff 算法设计

### 算法流程

```
                      ┌──────────────────┐
                      │ 输入：vN, vN+1  │
                      └────────┬─────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ 1. 解析 BPMN XML → DOM 树    │
                └──────────────┬───────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ 2. 提取节点集合 (HashMap)     │
                │    key = elementId            │
                │    value = {id,name,type,...} │
                └──────────────┬───────────────┘
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │ 3. 节点级别比对                       │
            │    - 交集 → 检查属性变更 (MODIFIED)   │
            │    - A-B → 新增节点 (ADDED)          │
            │    - B-A → 删除节点 (REMOVED)        │
            └──────────────┬───────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────────────┐
            │ 4. 连线级别比对（同上逻辑）           │
            └──────────────┬───────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────────────┐
            │ 5. 属性级比对 (compareProperties)     │
            │    - assignee, condition, priority    │
            │    - 业务规则表达式变更检测           │
            │    - 审批人/审批角色变更高亮          │
            └──────────────┬───────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────────────┐
            │ 6. 语义转换                           │
            │    - "@{user}" → "审批人表达式变更"   │
            │    - condition 数字变化 → 阈值调整    │
            │    - 节点类型变化 → 结构重构          │
            └──────────────┬───────────────────────┘
                           │
                           ▼
            ┌──────────────────────────────────────┐
            │ 7. 影响评估                           │
            │    - 对 ACTIVE 流程实例的影响         │
            │    - 对历史数据的可追溯性             │
            │    - Breaking Change 判定             │
            └──────────────┬───────────────────────┘
                           │
                           ▼
                      ┌─────────┐
                      │ Diff 结果│
                      └─────────┘
```

### 核心比对算法（伪代码）

```java
public class BpmnDiffEngine {

    /**
     * 比较两个版本，返回结构化 Diff 结果
     */
    public FlowDefinitionDiff diff(BpmnModel from, BpmnModel to) {
        FlowDefinitionDiff result = new FlowDefinitionDiff();
        
        // 1. 节点比對
        List<FlowElement> addedNodes = diffElements(
            from.getMainProcess().getFlowElements(),
            to.getMainProcess().getFlowElements(),
            ElementFlowElemcategory.NODE
        );
        
        // 2. 连线比较
        List<FlowElementAdded> addedEdges = diffSequenceFlows(
            from.getMainProcess().getFlowElements(),
            to.getMainProcess().getFlowElements()
        );
        
        // 3. 属性差量
        List<PropertyChange> propChanges = diffProperties(from, to);
        
        // 4. 语义业务转换
        result.setChanges(toBusinessSemantics(addedNodes, propChanges));
        
        // 5. 影响评估
        result.setImpacts(assessImpact(from, to));
        
        return result;
    }
    
    private List<Change> toBusinessSemantics(...) {
        // 1. assignee 变更 → "审批人变更"
        // 2. condition 含 number → "金额阈值变更"
        // 3. priority 变更 → "优先级变更"
        // 4. 节点类型变化 (userTask→serviceTask) → "节点类型重构"
    }
    
    private List<Impact> assessImpact(...) {
        // 1. 查询 ACTIVE 实例数
        // 2. 检查字段移除（BreakingChange）
        // 3. 检查表达式中的变量引用变更
    }
}
```

---

## REST API 设计

### 查询版本列表

```
GET /api/v1/flow/definition/{key}/versions
```

```json
{
  "code": 0,
  "data": {
    "definitionKey": "leave_apply",
    "versions": [
      {
        "version": 3,
        "name": "请假流程 v3.0",
        "description": "新增 HR 复审，提高审批阈值",
        "deployedAt": "2026-08-04T10:30:00Z",
        "deployedBy": "admin",
        "activeCount": 150
      },
      {
        "version": 2,
        "name": "请假流程 v2.0",
        "deployedAt": "2026-06-15T08:00:00Z",
        "deployedBy": "admin",
        "activeCount": 0
      }
    ]
  },
  "httpMethod": "GET",
  "pageSize": 20,
  "pageNum": 1,
  "total": 3
}
```

### 获取版本 Diff

```
GET /api/v1/flow/definition/{key}/diff?from=2&to=3
```

```json
{
  "code": 0,
  "data": {
    "definitionKey": "leave_apply",
    "fromVersion": 2,
    "toVersion": 3,
    "summary": {
      "nodesAdded": 2,
      "nodesRemoved": 1,
      "nodesModified": 3,
      "hasBreakingChange": true
    },
    "changes": [
      {
        "category": "NODE",
        "action": "ADDED",
        "elementId": "Activity_HR_Review",
        "elementName": "HR 复审",
        "after": { "id": "Activity_HR_Review", "name": "HR 复审", "assignee": "${hr_leader}" }
      },
      {
        "category": "PROPERTY",
        "action": "MODIFIED",
        "elementId": "Activity_Manager_Approve",
        "elementName": "经理审批",
        "changes": [
          { "property": "condition", "from": "${amount > 1000}", "to": "${amount > 2000}" }
        ]
      }
    ],
    "impacts": [
      { "level": "WARNING", "message": "审批金额阈值变更影响 15 条审批中流程" }
    ]
  }
}
```

### 获取单版本完整 BPMN XML

```
GET /api/v1/flow/definition/{key}/version/{version}/xml
```

```json
{
  "code": 0,
  "data": {
    "version": 3,
    "bpmnXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<bpmn:definitions ...>..."
  }
}
```

### 对比任意两个版本 (POST 语义查询)

```
POST /api/v1/flow/definition/{key}/compare
Content-Type: application/json

{
  "fromVersion": 2,
  "toVersion": 3,
  "includeXml": false
}
```

### 变更记录导出

```
GET /api/v1/flow/definition/{key}/versions/history?format=csv|xlsx
```

---

## 前端集成方案

### 组件设计

```
┌──────────────────────────────────────────────────────────────────┐
│ 流程版本历史                                          [导出 Excel] │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────┐    ┌─────────────────────┐              │
│  │  版本 v2.0  (旧)    │    │  版本 v3.0  (新)    │              │
│  │  ○ 组长审批         │    │  ○ 经理审批 ⚠️修改  │              │
│  │  ○ 经理审批         │    │  ○ 🆕 HR 复审       │              │
│  │  ○ 归档             │    │  ○ 归档             │              │
│  └─────────────────────┘    └─────────────────────┘              │
│                                                                  │
│  对比模式: (●) 分屏对比  ( ) 并排对比  ( ) 差异叠加             │
│                                                                  │
│  ──────────────── 差异详情 ────────────────                      │
│                                                                  │
│  📌 删除节点: 组长审批 (userTask)                                 │
│  ✅ 新增节点: HR 复审 (userTask) → 审批人: ${hr_leader}          │
│  ✏️  修改属性: 经理审批                                          │
│     - 优先级: normal → high                                     │
│     - 金额阈值: ${amount > 1000} → ${amount > 2000} ⚠️业务影响  │
│                                                                  │
│  ⚠️ 影响评估:                                                     │
│     - 15 条进行中的流程实例可能受影响                              │
│     - 建议通知相关业务负责人确认                                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Qiankun 子应用集成

```javascript
// frontend/ydsz-workflow-pc/src/views/diff/index.vue 核心逻辑

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { diffFlowVersions } from '@/api/flow'
import BpmnDiffViewer from '@/components/bpmn/BpmnDiffViewer.vue'

const route = useRoute()
const definitionKey = computed(() => route.params.key as string)

const fromVersion = ref(2)
const toVersion = ref(3)
const diffData = ref<FlowDefinitionDiff | null>(null)

async function loadDiff() {
  const res = await diffFlowVersions(definitionKey.value, {
    fromVersion: fromVersion.value,
    toVersion: toVersion.value
  })
  diffData.value = res.data
}
</script>

<template>
  <div class="flow-diff-container">
    <VersionSelector v-model:from="fromVersion" v-model:to="toVersion" :key="definitionKey" @change="loadDiff" />
    
    <DiffSummary v-if="diffData" :summary="diffData.summary" :impacts="diffData.impacts" />
    
    <BpmnDiffViewer v-if="diffData" :diff-data="diffData" class="bpmn-viewer" />
    
    <ChangeDetailList v-if="diffData" :changes="diffData.changes" />
  </div>
</template>
```

---

## 数据库变更

### 1. ydsz_flow_definition 添加版本号列

```sql
-- 现有表升级
ALTER TABLE ydsz_flow_definition ADD COLUMN IF NOT EXISTS latest_version INT DEFAULT 1;
ALTER TABLE ydsz_flow_definition ADD COLUMN IF NOT EXISTS version_count INT DEFAULT 1;
```

### 2. 新版本表（V1.2.0 脚本）

```sql
-- deploy/sql/schema/V1.2.0__workflow_version_control.sql
CREATE TABLE IF NOT EXISTS ydsz_flow_definition_version (
    id              BIGSERIAL PRIMARY KEY,
    definition_id   BIGINT          NOT NULL REFERENCES ydsz_flow_definition(id),
    definition_key  VARCHAR(64)     NOT NULL,
    version         INT             NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,
    bpmn_xml        TEXT            NOT NULL,
    bpmn_json       JSONB           NOT NULL,
    checksum        VARCHAR(64)     NOT NULL,
    change_type     VARCHAR(20)     NOT NULL DEFAULT 'UPDATE',
    change_log      TEXT,
    deployed_by     BIGINT          NOT NULL,
    deployed_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_def_ver UNIQUE (definition_key, version)
);

CREATE TABLE IF NOT EXISTS ydsz_flow_definition_diff (
    id                    BIGSERIAL PRIMARY KEY,
    definition_key        VARCHAR(64)     NOT NULL,
    from_version          INT             NOT NULL,
    to_version            INT             NOT NULL,
    diff_json             JSONB           NOT NULL,
    diff_summary          JSONB           NOT NULL,
    diff_pretty_html      TEXT,
    created_at            TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_def_diff UNIQUE (definition_key, from_version, to_version)
);

-- 索引
CREATE INDEX idx_flow_ver_key ON ydsz_flow_definition_version(definition_key);
CREATE INDEX idx_flow_ver_time ON ydsz_flow_definition_version(deployed_at);
CREATE INDEX idx_flow_diff_key ON ydsz_flow_definition_diff(definition_key, from_version, to_version);

-- ROLLBACK:
-- DROP TABLE IF EXISTS ydsz_flow_definition_diff;
-- DROP TABLE IF EXISTS ydsz_flow_definition_version;
```

---

## 实施计划

### 第一阶段：基础版本管理（1 周）

- [ ] 创建版本表 + 差异表
- [ ] 实现 BpmnModel 解析器
- [ ] 实现基础 Diff 算法
- [ ] 实现 `version/list` 和 `version/xml` API

### 第二阶段：语义 Diff（1 周）

- [ ] 实现属性级比对
- [ ] 实现语义转换（assignee/condition 描述化）
- [ ] 实现 Breaking Change 检测
- [ ] 实现 `/diff` API

### 第三阶段：前端集成（1 周）

- [ ] 实现 diff 可视化组件
- [ ] 集成 Bpmn.js 高亮渲染
- [ ] 变更详情列表组件
- [ ] 影响评估面板

### 第四阶段：增强功能（可选）

- [ ] 版本回滚（一键恢复到指定版本）
- [ ] 冲突检测（并行编辑时告警）
- [ ] 变更审批（关键变更需审批后生效）
- [ ] 审计日志导出

### 第五阶段：性能优化

- [ ] 差异结果缓存（同一版本对比结果长期有效）
- [ ] 异步计算（大流程的 diff 放到异步任务）
- [ ] 版本清理策略（只保留最近 N 个版本）

---

## 安全考量

| 风险 | 措施 |
|------|------|
| BPMN XML 含敏感表达式 | diff 结果脱敏（不输出完整表达式，只输出变更摘要） |
| 版本历史暴露 | 基于角色鉴权（仅流程管理员可访问 diff） |
| 大流程 diff 耗时 | 异步计算 + 超时降级（返回基础 diff） |

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
