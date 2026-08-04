# JMeter 压测计划说明

## 文件结构

```
jmeter/
├── README.md                    # 本文件
├── ydsz_loadtest.jmx            # JMeter 测试计划
└── test_data/
    └── users.csv                # 测试用户数据（CSV 参数化）
```

## 快速开始

### 1. 安装 JMeter

```bash
# Windows
choco install jmeter

# macOS
brew install apache-jmeter

# 手动下载：https://jmeter.apache.org/download_jmeter.cgi
```

### 2. 准备测试数据

编辑 `test_data/users.csv`，确保文件中的用户压测前已存在于数据库：

```csv
username,password
loadtest_user_1,Load@Test123
loadtest_user_2,Load@Test123
...
```

### 3. 运行压测

#### 命令行模式（推荐，适合 CI 集成）

```bash
jmeter -n -t ydsz_loadtest.jmx \
       -l results.jtl \
       -e -o reports/dashboard \
       -Jthreads=100 \
       -Jrampup=60 \
       -Jduration=600 \
       -Jhost=ydsz-gateway.local \
       -Jport=9000
```

#### GUI 模式（仅调试使用）

```bash
jmeter -t ydsz_loadtest.jmx
```

### 4. 查看结果

压测完成后打开 `reports/dashboard/index.html` 查看详细 Dashboard。

## 测试计划结构

### 场景 1：登录压测

- **线程数**：`-Jthreads`（默认 100）
- **循环次数**：`duration / 10`
- **采样器**：POST /api/v1/user/login
- **断言**：HTTP 200 + 响应 body 包含 token

### 场景 2：业务混合压测

- **线程数**：`-Jthreads`（默认 100）
- **循环次数**：`duration / 30`
- **采样器分布**（随机顺序）：
  - GET /api/v1/project/list（40%）
  - GET /api/v1/user/me（25%）
  - GET /api/v1/flow/task/pending（20%）
  - POST /api/v1/flow/start（5%）

### 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `threads` | 100 | 并发线程数 |
| `rampup` | 60 | 爬坡时间（秒） |
| `duration` | 600 | 稳态持续时间（秒） |
| `host` | ydsz-gateway.local | 网关地址 |
| `port` | 9000 | 网关端口 |

## 自定义扩展

### 添加新接口

1. 在线程组下添加 HTTP Request 采样器
2. 配置 Path / Method / Body
3. 添加 Response Assertion
4. 添加 Uniform Random Timer（思考时间）

### 调整流量权重

使用 **Throughput Controller** 控制不同场景的执行比例。

### 接入 Grafana 实时监控

1. 启用 Backend Listener
2. 配置 InfluxDB URL 和 database
3. 在 Grafana 中导入 JMeter Dashboard（ID: 5496）

## 注意事项

1. **禁用 "查看结果树"**：该监听器消耗大量内存，压测时务必设置为 `enabled="false"`
2. **JMeter 自身内存**：大并发时需调整 `HEAP`（`jmeter.bat` 中 `-Xmx` 设为 4g+）
3. **网络带宽**：单机压测受限于网卡（千兆约 125MB/s），分布式压测可突破限制
4. **日志级别**：压测时建议设置 log_level=WARN，避免大量日志拖慢执行

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
