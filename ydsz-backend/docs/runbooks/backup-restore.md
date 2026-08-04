# 数据备份与恢复手册

## 一、备份策略

| 类型 | 频率 | 保留 | 存储 |
|------|------|------|------|
| PostgreSQL 全量 | 每日 02:00 | 7 天日备 | MinIO/S3 `backups/daily/` |
| PostgreSQL 全量 | 每周日 02:00 | 4 周 | MinIO/S3 `backups/weekly/` |
| PostgreSQL 全量 | 每月 1 日 02:00 | 12 月 | MinIO/S3 `backups/monthly/` |
| Nacos 配置 | 每日 | 7 天 | 导出 YAML 到 Git 仓库 |
| MinIO 文件 | 依赖 MinIO 自身 | - | MinIO 版本控制 |

## 二、备份执行（CronJob 示例）

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: pg-backup
  namespace: ydsz-prod
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: backup
              image: postgres:17-alpine
              command:
                - /bin/sh
                - -c
                - |
                  pg_dump -h postgres -U ydsz -d ydsz -Fc \
                    | mc pipe minio/backups/daily/ydsz-$(date +%Y%m%d).dump
              env:
                - name: PGPASSWORD
                  valueFrom:
                    secretKeyRef: { name: pg-secret, key: password }
          restartPolicy: OnFailure
```

## 三、恢复演练（每月执行）

```bash
# 1. 创建恢复专用数据库
createdb -h <pg-host> -U ydsz ydsz_restore_test

# 2. 恢复备份
pg_restore -h <pg-host> -U ydsz -d ydsz_restore_test \
  --no-owner --no-privileges \
  backups/daily/ydsz-YYYYMMDD.dump

# 3. 验证数据完整性
psql -h <pg-host> -U ydsz -d ydsz_restore_test -c \
  "SELECT count(*) FROM ydsz_project_contract;"

# 4. 清理
dropdb ydsz_restore_test
```

## 四、紧急恢复（P0）

```bash
# 场景：误删数据 / 生产库损坏
# 目标：最小化数据丢失

# 1. 立即停止写入（防止 WAL 被覆盖）
kubectl scale deploy ydsz-project --replicas=0 -n ydsz-prod

# 2. 找到最近可用备份 + 归档 WAL（如有 PITR）
#    - 无 PITR：恢复到最近备份，接受最多 24h 数据丢失
#    - 有 PITR：恢复到误删时间点前 1 分钟

# 3. 恢复
pg_restore -h <pg-host> -U ydsz -d ydsz --clean --if-exists \
  backups/daily/ydsz-YYYYMMDD.dump

# 4. 验证 + 恢复服务
# 5. 输出事故报告
```

## 五、验证清单（备份后）

- [ ] `pg_restore --list` 能正确列出备份内容
- [ ] 备份文件大小合理（> 10MB，与业务量匹配）
- [ ] 备份上传到 MinIO 成功
- [ ] 上个月恢复演练通过
