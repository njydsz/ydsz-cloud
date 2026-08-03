# -*- coding: utf-8 -*-
"""为 @Nested 测试内部类补充类级注释（正确版）。

关键改进：
1. 一次性收集所有插入点，按行号从大到小插入，避免字符串偏移；
2. 只处理当前确实缺注释的类（用扫描器实时判定，不依赖旧 todo 清单）；
3. 注释文本优先取 @DisplayName，其次取分组内首个用例描述，最后用类名推导。
"""
import io
import os
import re

sys_path = r'D:\Code\ydsz\ydsz-pmis\docs\scripts'
import sys
sys.path.insert(0, sys_path)
import scan_comments as sc

ROOT = r'D:\Code\ydsz\ydsz-pmis'

CLASS_RE = re.compile(
    r"(?m)^(?P<indent>\s*)class\s+(?P<name>\w+)\s*\{"
)
DISPLAY_RE = re.compile(r'@DisplayName\("([^"]+)"\)')


def split_words(name):
    return re.findall(r'[A-Z]?[a-z]+|[A-Z]+(?![a-z])', name)


CN_MAP = {
    'Closed': '关闭状态', 'Open': '打开状态', 'Half': '半开', 'State': '状态',
    'Construct': '构造', 'Multi': '多', 'Rule': '规则', 'Update': '更新',
    'Find': '查找', 'Candidate': '候选', 'Inverted': '倒排', 'Index': '索引',
    'Compatibility': '兼容性', 'Shard': '分片', 'Determinism': '确定性',
    'Mine': '归属', 'Check': '检查', 'Static': '静态', 'Weight': '权重',
    'Distribution': '分布', 'Minimal': '最小', 'Migration': '迁移',
    'Virtual': '虚拟', 'Node': '节点', 'Count': '计数', 'Number': '数字',
    'Literal': '字面量', 'String': '字符串', 'Keyword': '关键字',
    'Identifier': '标识符', 'Operator': '运算符', 'Delimiter': '分隔符',
    'Comment': '注释', 'Whitespace': '空白', 'Position': '位置',
    'Tracking': '追踪', 'Complex': '复杂', 'Expression': '表达式',
    'Resolve': '解析', 'Url': 'URL', 'Build': '构建', 'Payload': '载荷',
    'Percent': '百分号', 'Encode': '编码', 'Canonical': '规范', 'Query': '查询',
    'List': '列表查询', 'Remove': '删除', 'ById': '按ID', 'Get': '获取',
    'Error': '错误', 'Code': '码', 'Chain': '链', 'Data': '数据',
    'Message': '消息', 'Resolution': '解析', 'Concurrency': '并发',
    'Incremental': '增量', 'Enable': '启用', 'Rebuild': '重建',
    'Key': '密钥', 'Validation': '校验', 'Encrypt': '加密', 'Decrypt': '解密',
    'Roundtrip': '往返', 'Tamper': '篡改', 'Detection': '检测',
    'Sign': '签名', 'Verify': '验证', 'Signature': '签名', 'Util': '工具',
    'Zero': '零值', 'Null': '空值', 'Normal': '正常', 'Overdue': '逾期',
    'Boundary': '边界', 'Blended': '混合', 'Margin': '利润', 'Achieved': '达成',
    'Evaluate': '评估', 'DryRun': '试运行', 'TopResult': 'Top结果',
    'Stats': '统计', 'MdcTrace': 'MDC链路', 'Register': '注册',
    'Unregister': '注销', 'Parse': '解析', 'Valid': '有效', 'Invalid': '无效',
    'Safe': '安全', 'Constructor': '构造器', 'Sequential': '顺序',
    'Parallel': '并行', 'Failure': '失败', 'Propagation': '传播',
    'Cycle': '循环', 'Append': '追加', 'Transit': '流转', 'Can': '能否',
    'Bytes': '字节', 'ToHex': '转十六进制', 'Hex': '十六进制',
    'Base64': 'Base64', 'Base32': 'Base32', 'Encoding': '编码',
    'Crc32': 'CRC32', 'MurmurHash32': 'MurmurHash32', 'Base62': 'Base62',
    'Base58': 'Base58', 'ConsistentHash': '一致性哈希',
    'UpdateThenInsert': '先更新后插入', 'Idempotent': '幂等',
    'GetById': '按ID查询', 'RemoveById': '按ID删除',
    'Counting': '计数', 'Publisher': '发布器', 'Alert': '告警',
    'Converger': '聚合', 'Sample': '示例', 'App': '应用', 'Controller': '控制器',
    'Retry': '重试', 'DingTalk': '钉钉', 'Feishu': '飞书', 'WeCom': '企业微信',
    'Heartbeat': '心跳', 'Cluster': '集群', 'Offline': '离线', 'RateLimit': '限流',
    'CircuitBreaker': '熔断', 'Ack': '确认', 'ConnectionLimit': '连接数限制',
    'Compression': '压缩', 'SlowConnection': '慢连接', 'Idle': '空闲',
    'Ssl': 'TLS', 'TrafficShaping': '流量整形', 'Reconnect': '重连',
    'TccLogStoreType': 'TCC日志存储', 'NullPlaceholder': '空值占位',
    'RetryFlushTask': '重试刷新任务', 'Cache': '缓存', 'Notify': '通知',
    'Project': '项目', 'Total': '总量', 'Auth': '认证', 'Requests': '请求数',
    'Failures': '失败数', 'Rejected': '拒绝数', 'SecurityHeaders': '安全头',
    'Injected': '注入数', 'Rate': '限流', 'Llm': 'LLM', 'Memory': '记忆',
    'Rag': 'RAG', 'Vector': '向量', 'Store': '存储', 'Stats': '统计',
    'Citation': '引用', 'Token': 'Token', 'Usage': '用量', 'Record': '记录',
    'Dag': 'DAG', 'Execution': '执行', 'Result': '结果', 'Cost': '成本',
    'Analysis': '分析', 'Document': '文档', 'Ingestion': '摄入', 'Service': '服务',
    'Metric': '指标', 'Metrics': '指标', 'Trace': '链路', 'Tracing': '追踪',
    'Otel': 'OTel', 'Exporter': '导出器', 'Factory': '工厂', 'Tls': 'TLS',
    'Span': 'Span', 'Error': '错误', 'Event': '事件', 'Processor': '处理器',
    'Reason': '原因', 'Serializer': '序列化器', 'Deserializer': '反序列化器',
    'Json': 'JSON', 'Config': '配置', 'Properties': '属性', 'Backoff': '退避',
    'ThreadPool': '线程池', 'WatchDog': '看门狗', 'Watching': '监控中',
    'Tenant': '租户', 'Field': '字段', 'Value': '值', 'Isolation': '隔离',
    'Interceptor': '拦截器', 'Flow': '流程', 'Form': '表单', 'Type': '类型',
    'Instance': '实例', 'View': '视图', 'DTO': 'DTO', 'Task': '任务',
    'Preview': '预览', 'Request': '请求', 'Raw': '原始', 'Template': '模板',
    'Channel': '渠道', 'Locale': '语言', 'Param': '参数', 'Params': '参数',
}


def describe(name):
    words = split_words(name)
    if words and words[-1].lower() in ('cases', 'test', 'tests'):
        words = words[:-1]
    if not words:
        return name
    parts = [CN_MAP.get(w, w) for w in words]
    return ''.join(parts) + '用例'


def get_display(lines, line_no_0based):
    """从类声明行向上找 @DisplayName。"""
    for k in range(line_no_0based - 1, max(line_no_0based - 5, -1), -1):
        t = lines[k].strip()
        dm = DISPLAY_RE.match(t)
        if dm:
            return dm.group(1)
        if t.startswith('@'):
            continue
        if not t:
            continue
        break
    return None


def get_first_case(lines, line_no_0based):
    """从类声明行向下找第一个 @DisplayName（分组内首个用例）。"""
    for k in range(line_no_0based + 1, min(line_no_0based + 80, len(lines))):
        t = lines[k].strip()
        dm = DISPLAY_RE.search(t)
        if dm:
            return '「' + dm.group(1) + '」等'
        if t.startswith('class '):
            break
    return None


def main():
    # 收集所有待处理的测试文件：实时扫描缺类注释的 @Nested 类
    java_files = sc.walk(sc.BACKEND, ('.java',))
    modified_files = 0
    total_inserts = 0

    for p in java_files:
        if '/test/' not in p.replace('\\', '/'):
            continue
        r = sc.analyze_java(p)
        if not r['no_doc_classes']:
            continue
        with io.open(p, 'r', encoding='utf-8', errors='replace') as f:
            src = f.read()
        lines = src.split('\n')

        # 收集插入点（行号从大到小）
        inserts = []  # (pos, comment)
        for m in CLASS_RE.finditer(src):
            name = m.group('name')
            if name not in r['no_doc_classes']:
                continue
            line_no = src[: m.start()].count('\n')
            # 确认该 class 上方是 @Nested 注解（避免误处理普通内部类）
            nested = False
            for k in range(line_no - 1, max(line_no - 4, -1), -1):
                t = lines[k].strip()
                if t.startswith('@Nested'):
                    nested = True
                    break
                if t.startswith('@'):
                    continue
                if not t:
                    continue
                break
            if not nested:
                continue
            display = get_display(lines, line_no) or get_first_case(lines, line_no) or describe(name)
            indent = m.group('indent')
            comment = f"{indent}/**\n{indent} * 测试分组：{display}\n{indent} */\n"
            inserts.append((m.start(), comment))

        if not inserts:
            continue

        # 从大到小排序插入，避免偏移
        inserts.sort(key=lambda x: -x[0])
        for pos, comment in inserts:
            src = src[:pos] + comment + src[pos:]
            total_inserts += 1
        with io.open(p, 'w', encoding='utf-8', newline='') as f:
            f.write(src)
        modified_files += 1
        print(f'[OK] {os.path.relpath(p, ROOT)} (+{len(inserts)})')

    print(f'完成：修改 {modified_files} 个文件，共插入 {total_inserts} 处注释')


if __name__ == '__main__':
    main()
