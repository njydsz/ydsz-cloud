# ydsz-pmis 项目长期记忆

## 项目概况
- 云鼎数字 PMIS 项目管理系统，前后端分离架构
- 后端：Spring Boot 3.x 微服务（9 个部署单元）+ K8s/Helm
- 前端：Vue 3 + Vite 6 + 自研 micro-kernel 微前端（9 个子应用）
- 数据库：MySQL 8.x（126+ 张表）

## 关键约定
- core 只放 L1 基础设施；领域契约归 domain；事件归 event；安全（脱敏/加密）归 safe
- 删除公共 API 前必须 grep 全项目确认零调用（含 import static 场景）
- 工具类 final class + private constructor

## 已产出文档
- docs/ydsz-common-core-优化建议报告.md
- docs/ydsz-common-core-过度设计评估报告.md
- docs/ARCHITECTURE_OPTIMIZATION_PROPOSAL.md（后端 46 条优化建议）
- frontend/docs/frontend-optimization-report.html（前端 v1 30 项，已全部落地）
- frontend/docs/frontend-optimization-report-v2.html（前端 v2 21 项增量建议）
