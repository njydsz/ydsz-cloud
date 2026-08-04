# Schema 版本化状态跟踪

> 本文件跟踪 deploy/sql/schema/ 目录的 DDL 填充状态。
> `verify/schema_check.sh` 在 `--strict` 模式下会读取本文件判断校验级别。

## 当前状态：PLACEHOLDER

| 文件 | 状态 | 说明 |
|------|------|------|
| `V1.0.0__init.sql` | ⏳ PLACEHOLDER | 占位文件，待从开发/生产库导出真实 DDL |
| `V1.0.1__indexes_and_partition.sql` | ✅ READY | 真实增量脚本（索引 + 分区表） |
| `V1.0.2__*.sql` | 🔲 TODO | 预留：后续增量变更 |

## 激活真实校验

当 `V1.0.0__init.sql` 从数据库导出真实 DDL 后，需要：

1. 将本文件的状态从 `PLACEHOLDER` 更新为 `LIVE`
2. 重新运行 `verify/scripts/export_baseline.sh` 生成 baseline 文件
3. CI 进入严格模式，每次 PR 自动 diff 校验

## CI 校验模式

| 状态 | 校验行为 |
|------|----------|
| `PLACEHOLDER` | 宽松模式：仅校验脚本可执行性、SQL 语法合法性、版本文件递增 |
| `LIVE` | 严格模式：全量 schema diff + 表数/视图数/列数阈值校验 |
