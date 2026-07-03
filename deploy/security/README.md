# PMIS 安全扫描目录（批次 19 补全）
# --------------------------------------------------------------------------
# 用途：PMIS 生产环境上线前的安全合规与渗透测试产物。
# 覆盖：等保三级 2.0 + OWASP Top 10 + GDPR / 个人信息保护法
# 输出：扫描报告存于 reports/，CI/CD 集成后归档 30 天。
# --------------------------------------------------------------------------

## 目录结构

```
deploy/security/
├── zap-scan.sh                # OWASP ZAP 主动扫描（API 漏洞）
├── dependency-check.sh        # 依赖安全扫描（OWASP dep-check）
├── crypto-verify.sh           # 加密算法 / TLS / JWT 验证
├── permission-test.sh         # 权限越权 / 跨角色测试
├── suppressions.xml           # 已知误报抑制清单
├── reports/                   # 扫描报告输出目录（自动创建）
└── README.md                  # 本文件
```

## 4 个安全验证脚本

### 1. zap-scan.sh（OWASP ZAP 主动扫描）
- 工具：OWASP ZAP 2.14+
- 模式：spider + ascan
- 范围：14 个微服务 API
- 退出码：0 无高危 / 1 有高危 / 2 仅中危

### 2. dependency-check.sh（依赖安全扫描）
- 工具：OWASP Dependency-Check 8.x + npm audit
- 范围：Java 14 模块 + 前端 1 模块
- 阈值：CVSS >= 7.0 失败

### 3. crypto-verify.sh（加密算法验证）
- 验证项：BCrypt / JWT / TLS 1.2+ / 强密码套件 / Cookie 属性 / CORS
- 范围：14 个微服务

### 4. permission-test.sh（权限一致性测试）
- 验证项：401/403 / 跨角色访问 / 越权删除 / 越权审批
- 范围：9 大业务模块

## 快速使用

```bash
cd deploy/security
chmod +x *.sh

# 1. OWASP ZAP 主动扫描
./zap-scan.sh http://staging.pmis.example.com baseline

# 2. 依赖安全扫描（需先安装 dependency-check）
./dependency-check.sh

# 3. 加密算法验证
./crypto-verify.sh https://staging.pmis.example.com

# 4. 权限一致性测试
./permission-test.sh http://staging.pmis.example.com
```

## 配合等保三级测评

参见 [docs/security/dengbao-2.0-3-level-checklist.md](../../docs/security/dengbao-2.0-3-level-checklist.md)，覆盖：

- 安全通信网络
- 安全区域边界
- 安全计算环境
- 安全管理中心
- 安全管理制度

## 配合 CI/CD

```yaml
# GitLab CI
security-scan:
  stage: security
  script:
    - ./deploy/security/dependency-check.sh
    - ./deploy/security/zap-scan.sh ${STAGING_URL} baseline
    - ./deploy/security/crypto-verify.sh ${STAGING_URL}
    - ./deploy/security/permission-test.sh ${STAGING_URL}
  artifacts:
    paths:
      - deploy/security/reports/
    expire_in: 30 days
  allow_failure: false  # 安全扫描失败阻塞
  only:
    - main
```

## 高危漏洞清单（历史教训）

| 漏洞类型 | 案例 | 防御措施 |
|----------|------|----------|
| SQL 注入 | MyBatis `${}` 字面量 | 强制 `#{}` 参数化 |
| XSS | innerHTML 直接渲染 | Vue v-text / v-html + CSP |
| CSRF | 表单无 token | Spring Security CSRF |
| 越权 | IDOR 直接传 ID | 业务层检查 ownership |
| 弱密码 | 8 位数字 | 复杂度 + 90 天轮换 |
| 明文传输 | HTTP | 强制 HTTPS + HSTS |
| 弱算法 | MD5 / SHA1 | 升级到 BCrypt + SHA-256 |
| JWT none | alg=none | HS256/RS256 强校验 |
| 越权审批 | 无角色检查 | @PreAuthorize + 后端校验 |
| 信息泄露 | 错误堆栈 | 统一异常 + 脱敏 |

## 应急响应

| 等级 | 描述 | 响应时间 |
|------|------|----------|
| P0 | 高危漏洞被利用 | 1h |
| P1 | 高危漏洞 | 24h |
| P2 | 中危漏洞 | 1 周 |
| P3 | 低危漏洞 | 1 月 |
