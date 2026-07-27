#!/usr/bin/env python3
"""
Java 注释自动增强脚本
按照互联网大厂标准，为 Java 文件自动添加缺失的 Javadoc 注释。

规范标准：
1. 类级 Javadoc: 类描述 + @author + @since
2. 字段级注释: /** 字段描述 */
3. 方法级 Javadoc: 方法描述 + @param + @return + @throws
4. 枚举常量注释: /** 常量描述 */
5. 常量注释: /** 常量描述 */
"""

import os
import re
import sys
import argparse
from pathlib import Path

# ========== 英文→中文 词典 ==========
WORD_MAP = {
    # 基础词汇
    'id': 'ID', 'name': '名称', 'code': '编码', 'type': '类型', 'status': '状态',
    'level': '级别', 'order': '排序', 'sort': '排序', 'index': '索引',
    'count': '数量', 'size': '大小', 'length': '长度', 'total': '总数',
    'list': '列表', 'map': '映射', 'set': '集合', 'array': '数组',
    'key': '键', 'value': '值', 'data': '数据', 'info': '信息',
    'config': '配置', 'property': '属性', 'properties': '属性配置',
    'message': '消息', 'content': '内容', 'text': '文本', 'body': '体',
    'title': '标题', 'desc': '描述', 'description': '描述', 'remark': '备注',
    'comment': '注释', 'label': '标签', 'tag': '标签', 'tags': '标签列表',
    'result': '结果', 'response': '响应', 'request': '请求', 'error': '错误',
    'exception': '异常', 'warning': '警告', 'success': '成功', 'fail': '失败',
    'failure': '失败', 'enable': '启用', 'disable': '禁用', 'enabled': '已启用',
    'disabled': '已禁用', 'active': '活跃', 'inactive': '非活跃',
    'start': '开始', 'end': '结束', 'stop': '停止', 'pause': '暂停',
    'create': '创建', 'update': '更新', 'delete': '删除', 'remove': '移除',
    'add': '添加', 'save': '保存', 'load': '加载', 'get': '获取',
    'set': '设置', 'put': '放入', 'post': '提交', 'query': '查询',
    'find': '查找', 'search': '搜索', 'check': '检查', 'validate': '校验',
    'verify': '验证', 'confirm': '确认', 'cancel': '取消', 'reset': '重置',
    'clear': '清除', 'clean': '清理', 'flush': '刷新', 'refresh': '刷新',
    'close': '关闭', 'open': '打开', 'init': '初始化', 'initialize': '初始化',
    'register': '注册', 'unregister': '注销', 'subscribe': '订阅',
    'publish': '发布', 'notify': '通知', 'alert': '告警', 'warn': '警告',
    'log': '日志', 'trace': '追踪', 'debug': '调试', 'monitor': '监控',
    'metric': '指标', 'metrics': '指标', 'stats': '统计', 'statistic': '统计',
    'health': '健康', 'ready': '就绪', 'live': '存活',
    # 用户/权限
    'user': '用户', 'account': '账号', 'role': '角色', 'permission': '权限',
    'auth': '认证', 'token': '令牌', 'password': '密码', 'pwd': '密码',
    'login': '登录', 'logout': '登出', 'session': '会话',
    'menu': '菜单', 'dept': '部门', 'department': '部门', 'post': '岗位',
    'org': '组织', 'organization': '组织', 'company': '公司', 'tenant': '租户',
    'admin': '管理员', 'super': '超级', 'owner': '所有者', 'creator': '创建人',
    'operator': '操作人', 'assignee': '指派人', 'approver': '审批人',
    # 时间
    'time': '时间', 'date': '日期', 'datetime': '日期时间', 'timestamp': '时间戳',
    'expire': '过期', 'expiry': '过期', 'timeout': '超时', 'ttl': '存活时间',
    'duration': '持续时间', 'interval': '间隔', 'period': '周期', 'frequency': '频率',
    'delay': '延迟', 'wait': '等待', 'sleep': '休眠',
    'created': '创建', 'updated': '更新', 'modified': '修改', 'deleted': '删除',
    'createdAt': '创建时间', 'updatedAt': '更新时间', 'deletedAt': '删除时间',
    'createdBy': '创建人', 'updatedBy': '更新人', 'deletedBy': '删除人',
    # 文件/存储
    'file': '文件', 'folder': '文件夹', 'directory': '目录', 'dir': '目录',
    'path': '路径', 'url': 'URL', 'uri': 'URI', 'link': '链接',
    'upload': '上传', 'download': '下载', 'preview': '预览',
    'storage': '存储', 'bucket': '存储桶', 'object': '对象',
    'chunk': '分块', 'part': '分片', 'block': '块',
    # 网络/服务
    'host': '主机', 'port': '端口', 'address': '地址', 'addr': '地址',
    'ip': 'IP', 'network': '网络', 'connection': '连接', 'conn': '连接',
    'client': '客户端', 'server': '服务端', 'service': '服务',
    'endpoint': '端点', 'api': 'API', 'http': 'HTTP', 'https': 'HTTPS',
    'header': '请求头', 'headers': '请求头', 'cookie': 'Cookie',
    'param': '参数', 'params': '参数', 'parameter': '参数', 'args': '参数',
    # 业务
    'project': '项目', 'task': '任务', 'job': '作业', 'flow': '流程',
    'workflow': '工作流', 'process': '流程', 'step': '步骤', 'node': '节点',
    'rule': '规则', 'policy': '策略', 'strategy': '策略', 'plan': '计划',
    'agent': '智能体', 'model': '模型', 'prompt': '提示词', 'tool': '工具',
    'chat': '对话', 'conversation': '会话', 'message': '消息',
    'document': '文档', 'doc': '文档', 'page': '页面', 'sheet': '工作表',
    'row': '行', 'column': '列', 'col': '列', 'cell': '单元格',
    'table': '表', 'schema': '模式', 'field': '字段', 'column': '列',
    # 状态/动作
    'pending': '待处理', 'running': '运行中', 'completed': '已完成',
    'failed': '已失败', 'succeeded': '已成功', 'cancelled': '已取消',
    'skipped': '已跳过', 'paused': '已暂停', 'queued': '排队中',
    'retry': '重试', 'retries': '重试次数', 'attempt': '尝试',
    'limit': '限制', 'max': '最大', 'min': '最小', 'default': '默认',
    'current': '当前', 'next': '下一个', 'prev': '上一个', 'previous': '上一个',
    'first': '第一个', 'last': '最后一个', 'all': '全部', 'none': '无',
    'true': '是', 'false': '否', 'null': '空', 'empty': '空',
    # 其他
    'version': '版本', 'revision': '修订', 'ref': '引用', 'reference': '引用',
    'source': '来源', 'target': '目标', 'destination': '目标',
    'input': '输入', 'output': '输出', 'result': '结果', 'return': '返回',
    'cause': '原因', 'reason': '原因', 'detail': '详情', 'details': '详情',
    'summary': '摘要', 'brief': '简述', 'overview': '概览',
    'group': '分组', 'category': '分类', 'category': '分类', 'type': '类型',
    'priority': '优先级', 'weight': '权重', 'score': '分数', 'rank': '排名',
    'flag': '标志', 'mark': '标记', 'sign': '签名', 'signature': '签名',
    'secret': '密钥', 'key': '密钥', 'encrypt': '加密', 'decrypt': '解密',
    'hash': '哈希', 'digest': '摘要', 'checksum': '校验和',
    'compress': '压缩', 'decompress': '解压', 'encode': '编码', 'decode': '解码',
    'serialize': '序列化', 'deserialize': '反序列化',
    'parse': '解析', 'format': '格式化', 'render': '渲染',
    'convert': '转换', 'transform': '转换', 'map': '映射',
    'filter': '过滤', 'sort': '排序', 'merge': '合并', 'split': '分割',
    'join': '连接', 'match': '匹配', 'replace': '替换',
    'cache': '缓存', 'buffer': '缓冲', 'queue': '队列', 'stack': '栈',
    'pool': '池', 'worker': '工作线程', 'thread': '线程',
    'lock': '锁', 'unlock': '解锁', 'acquire': '获取', 'release': '释放',
    'commit': '提交', 'rollback': '回滚', 'transaction': '事务',
    'migrate': '迁移', 'upgrade': '升级', 'downgrade': '降级',
    'backup': '备份', 'restore': '恢复', 'recover': '恢复',
    'import': '导入', 'export': '导出', 'sync': '同步', 'async': '异步',
    'batch': '批量', 'bulk': '批量', 'chunk': '分块',
    'stream': '流', 'pipe': '管道', 'channel': '通道',
    'event': '事件', 'listener': '监听器', 'handler': '处理器',
    'interceptor': '拦截器', 'filter': '过滤器', 'aspect': '切面',
    'factory': '工厂', 'builder': '构建器', 'provider': '提供器',
    'resolver': '解析器', 'dispatcher': '分发器', 'router': '路由器',
    'adapter': '适配器', 'wrapper': '包装器', 'proxy': '代理',
    'delegate': '委托', 'callback': '回调', 'hook': '钩子',
    'context': '上下文', 'environment': '环境', 'env': '环境',
    'option': '选项', 'options': '选项', 'setting': '设置', 'settings': '设置',
    'feature': '特性', 'flag': '标志', 'switch': '开关',
    'template': '模板', 'pattern': '模式', 'prototype': '原型',
    'sample': '样本', 'example': '示例', 'demo': '演示',
    'test': '测试', 'mock': '模拟', 'stub': '桩',
    'real': '真实', 'fake': '虚假', 'dummy': '占位',
    'local': '本地', 'remote': '远程', 'global': '全局',
    'inner': '内部', 'outer': '外部', 'extra': '额外',
    'main': '主', 'sub': '子', 'parent': '父', 'child': '子',
    'before': '前置', 'after': '后置', 'pre': '前置', 'post': '后置',
    'forward': '转发', 'redirect': '重定向',
    'available': '可用', 'unavailable': '不可用',
    'visible': '可见', 'hidden': '隐藏',
    'readonly': '只读', 'writeonly': '只写',
    'required': '必填', 'optional': '可选',
    'unique': '唯一', 'duplicate': '重复',
    'public': '公共', 'private': '私有', 'protected': '受保护',
    'static': '静态', 'dynamic': '动态',
    'final': '最终', 'abstract': '抽象',
    'synchronized': '同步', 'volatile': '易失',
    'serializable': '可序列化', 'transient': '瞬态',
    # DTO/VO/Entity 后缀
    'dto': 'DTO', 'vo': 'VO', 'entity': '实体', 'do': 'DO',
    'req': '请求', 'resp': '响应', 'rsp': '响应',
    'query': '查询', 'command': '命令',
    # 特殊
    'cron': '定时', 'trigger': '触发器', 'schedule': '调度',
    'execution': '执行', 'execute': '执行', 'run': '运行',
    'dispatch': '分发', 'invoke': '调用', 'call': '调用',
    'handle': '处理', 'process': '处理', 'deal': '处理',
    'send': '发送', 'receive': '接收', 'reply': '回复',
    'forward': '转发', 'broadcast': '广播',
    'read': '读取', 'write': '写入', 'scan': '扫描',
    'exist': '存在', 'exists': '存在', 'has': '是否有',
    'is': '是否', 'can': '是否能', 'should': '是否应该',
    'with': '带有', 'without': '不带',
    'from': '来源', 'to': '目标', 'into': '到', 'onto': '到',
    'by': '按', 'for': '为', 'of': '的',
    'and': '与', 'or': '或', 'not': '非',
    'new': '新建', 'old': '旧',
    'high': '高', 'low': '低', 'medium': '中',
    'big': '大', 'small': '小', 'large': '大',
    'fast': '快速', 'slow': '慢速',
    'hot': '热', 'cold': '冷',
    'safe': '安全', 'unsafe': '不安全',
    'raw': '原始', 'plain': '纯文本', 'rich': '富文本',
    'full': '完整', 'partial': '部分',
    'simple': '简单', 'complex': '复杂',
    'auto': '自动', 'manual': '手动',
    'self': '自身', 'this': '当前',
    'other': '其他', 'another': '另一个',
    'each': '每个', 'every': '每个', 'any': '任意', 'some': '某些',
    'more': '更多', 'less': '更少', 'most': '最多', 'least': '最少',
    'only': '仅', 'just': '仅',
    'once': '一次', 'twice': '两次', 'multiple': '多次',
    'single': '单个', 'multi': '多个', 'multiple': '多个',
    # 领域
    'rag': 'RAG 检索增强生成', 'llm': '大语言模型', 'embedding': '向量化',
    'vector': '向量', 'chunk': '文本块', 'token': 'Token',
    'guardrail': '护栏', 'guard': '护栏',
    'react': 'ReAct 模式', 'plan': '计划', 'execute': '执行',
    'cost': '成本', 'price': '价格', 'usage': '用量',
    'memory': '记忆', 'history': '历史',
    'knowledge': '知识', 'wiki': '知识库',
    'retrieval': '检索', 'augmented': '增强',
    'generation': '生成', 'completion': '补全',
    'prompt': '提示词', 'injection': '注入',
    'sensitive': '敏感', 'pii': '个人隐私信息',
    'desensitize': '脱敏', 'mask': '掩码',
    # 通信
    'sms': '短信', 'email': '邮件', 'push': '推送',
    'webhook': 'Webhook', 'callback': '回调',
    'websocket': 'WebSocket', 'socket': 'Socket',
    'channel': '通道', 'topic': '主题', 'queue': '队列',
    'exchange': '交换机', 'routing': '路由',
    'subscribe': '订阅', 'unsubscribe': '取消订阅',
    'consumer': '消费者', 'producer': '生产者',
    # 架构
    'controller': '控制器', 'service': '服务', 'repository': '仓储',
    'mapper': '映射器', 'converter': '转换器',
    'configuration': '配置类', 'config': '配置',
    'auto': '自动', 'properties': '属性配置',
    'indicator': '健康指示器', 'health': '健康检查',
    'listener': '监听器', 'handler': '处理器',
    'interceptor': '拦截器', 'filter': '过滤器',
    'aspect': '切面', 'pointcut': '切点',
    'annotation': '注解', 'enums': '枚举',
    'constant': '常量', 'constants': '常量',
    'util': '工具', 'utils': '工具', 'helper': '辅助',
    'exception': '异常', 'error': '错误',
    'response': '响应', 'request': '请求',
    'base': '基类', 'abstract': '抽象',
}

# 类型→中文描述 映射
TYPE_SUFFIX_MAP = {
    'Controller': '控制器',
    'Service': '服务',
    'ServiceImpl': '服务实现',
    'Repository': '仓储',
    'Mapper': 'MyBatis 映射器',
    'Converter': 'MapStruct 转换器',
    'DTO': '数据传输对象',
    'VO': '视图对象',
    'PostDTO': '创建请求 DTO',
    'PutDTO': '更新请求 DTO',
    'QueryDTO': '查询请求 DTO',
    'Request': '请求',
    'Response': '响应',
    'Config': '配置',
    'Configuration': '自动配置类',
    'Properties': '属性配置',
    'Indicator': '健康检查指示器',
    'Metrics': '指标监控',
    'Listener': '监听器',
    'Handler': '处理器',
    'Interceptor': '拦截器',
    'Filter': '过滤器',
    'Aspect': '切面',
    'Exception': '异常',
    'Enum': '枚举',
    'Constants': '常量',
    'Util': '工具类',
    'Utils': '工具类',
    'Helper': '辅助类',
    'Builder': '构建器',
    'Factory': '工厂',
    'Provider': '提供者',
    'Strategy': '策略',
    'Adapter': '适配器',
    'Wrapper': '包装器',
    'Context': '上下文',
    'Event': '事件',
    'Entity': '实体',
    'DO': '数据库实体',
    'BO': '业务对象',
    'TO': '传输对象',
}

def split_camel_case(name):
    """将驼峰命名拆分为单词列表"""
    if not name:
        return []
    # 处理全大写缩写（如 URL, ID, DTO）
    result = []
    current = ''
    for i, ch in enumerate(name):
        if ch.isupper():
            if current and (len(current) > 1 or (i > 0 and not name[i-1].isupper())):
                result.append(current.lower())
                current = ''
            current += ch
        else:
            current += ch
    if current:
        result.append(current.lower())
    return result

def translate_name(name):
    """将英文名称翻译为中文描述"""
    if not name:
        return ''
    
    # 直接匹配
    if name.lower() in WORD_MAP:
        return WORD_MAP[name.lower()]
    
    # 拆分驼峰
    words = split_camel_case(name)
    if not words:
        return name
    
    # 单词翻译
    translated = []
    for word in words:
        wl = word.lower()
        if wl in WORD_MAP:
            translated.append(WORD_MAP[wl])
        elif len(word) <= 2 and word.isupper():
            # 短缩写保留大写
            translated.append(word)
        else:
            translated.append(word)
    
    return ''.join(translated)

def translate_class_name(class_name):
    """翻译类名为中文描述"""
    # 检查后缀
    for suffix, cn in sorted(TYPE_SUFFIX_MAP.items(), key=lambda x: -len(x[0])):
        if class_name.endswith(suffix):
            base = class_name[:-len(suffix)]
            base_cn = translate_name(base) if base else ''
            if base_cn:
                return f'{base_cn}{cn}'
            return cn
    
    return translate_name(class_name)

def generate_class_javadoc(class_name, class_type, package, existing_content):
    """生成类级 Javadoc"""
    cn_desc = translate_class_name(class_name)
    
    # 根据类型调整描述
    if class_type == 'interface':
        desc = f'{cn_desc}接口'
    elif class_type == 'enum':
        desc = f'{cn_desc}枚举'
    else:
        desc = cn_desc
    
    # 从包名推断模块
    pkg_parts = package.split('.')
    module = pkg_parts[-2] if len(pkg_parts) >= 2 else ''
    
    javadoc = f'''/**
 * {desc}
 *
 * @author ydsz-team
 * @since 1.0.0
 */'''
    return javadoc

def generate_field_comment(field_name, field_type, annotations=''):
    """生成字段注释"""
    # 跳过 serialVersionUID
    if field_name == 'serialVersionUID':
        return None
    # 跳过 logger
    if field_name in ('log', 'logger', 'LOGGER'):
        return None
    
    cn_desc = translate_name(field_name)
    
    # 根据类型补充
    if 'List' in field_type or 'Set' in field_type:
        if not cn_desc.endswith('列表') and not cn_desc.endswith('集合'):
            cn_desc = cn_desc + '列表'
    elif 'Map' in field_type:
        if not cn_desc.endswith('映射'):
            cn_desc = cn_desc + '映射'
    elif field_type == 'boolean' or field_name.startswith('is'):
        if not cn_desc.startswith('是否'):
            cn_desc = f'是否{cn_desc}'
    
    return f'/** {cn_desc} */'

def generate_method_javadoc(method_name, params, return_type, is_void):
    """生成方法 Javadoc"""
    # 跳过简单方法
    skip_names = {'toString', 'equals', 'hashCode', 'values', 'valueOf',
                  'clone', 'finalize', 'getClass', 'notify', 'notifyAll', 'wait'}
    if method_name in skip_names:
        return None
    
    # getter/setter 跳过
    if re.match(r'^(get|set|is)[A-Z]', method_name):
        return None
    
    cn_desc = translate_name(method_name)
    
    # 构造 Javadoc
    lines = ['/**', f' * {cn_desc}']
    
    # 参数
    for param_name, param_type in params:
        param_cn = translate_name(param_name)
        lines.append(f' * @param {param_name} {param_cn}')
    
    # 返回值
    if not is_void and return_type != 'void':
        if return_type == 'boolean':
            lines.append(f' * @return true={cn_desc}成功；false=失败')
        else:
            ret_cn = translate_name(return_type)
            lines.append(f' * @return {ret_cn}')
    
    lines.append(' */')
    return '\n'.join(lines)

def generate_enum_const_comment(const_name):
    """生成枚举常量注释"""
    cn_desc = translate_name(const_name)
    return f'/** {cn_desc} */'

def has_javadoc_before(content, pos):
    """检查 pos 位置之前是否有 Javadoc 注释"""
    before = content[:pos].rstrip()
    return before.endswith('*/')

def has_inline_comment_before(content, pos):
    """检查 pos 位置之前是否有行内注释 // 或 /** */"""
    before = content[:pos].rstrip()
    return before.endswith('*/') or before.endswith('//')

def process_file(filepath, dry_run=False):
    """处理单个 Java 文件，添加缺失的注释"""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except:
        return 0
    
    original = content
    changes = 0
    
    # 1. 添加类级 Javadoc
    # 匹配 class/interface/enum 声明
    class_pattern = re.compile(
        r'^(?P<indent>\s*)(?P<modifiers>(?:public\s+|protected\s+|private\s+|abstract\s+|final\s+|static\s+)*)(?P<type>class|interface|enum)\s+(?P<name>\w+)',
        re.MULTILINE
    )
    
    for m in list(class_pattern.finditer(content)):
        pos = m.start()
        if has_javadoc_before(content, pos):
            continue
        
        # 提取 package
        pkg_match = re.search(r'package\s+([\w.]+);', content)
        package = pkg_match.group(1) if pkg_match else ''
        
        javadoc = generate_class_javadoc(m.group('name'), m.group('type'), package, content)
        indent = m.group('indent')
        
        # 缩进 Javadoc
        javadoc_indented = '\n'.join(indent + line if line else line for line in javadoc.split('\n'))
        
        content = content[:pos] + javadoc_indented + '\n' + content[pos:]
        changes += 1
    
    # 2. 添加字段注释
    # 匹配 private/protected 字段声明（不含方法）
    field_pattern = re.compile(
        r'^(?P<indent>\s+)(?P<modifiers>(?:private|protected)\s+(?:final\s+)?(?:static\s+)?)(?P<type>[\w<>,\s\[\]]+?)\s+(?P<name>\w+)\s*[;=]',
        re.MULTILINE
    )
    
    for m in list(field_pattern.finditer(content)):
        pos = m.start()
        if has_inline_comment_before(content, pos):
            continue
        
        field_name = m.group('name')
        field_type = m.group('type').strip()
        
        comment = generate_field_comment(field_name, field_type)
        if comment is None:
            continue
        
        indent = m.group('indent')
        comment_indented = indent + comment
        
        content = content[:pos] + comment_indented + '\n' + content[pos:]
        changes += 1
    
    # 3. 添加枚举常量注释
    # 匹配枚举常量（大写字母 + 下划线）
    enum_const_pattern = re.compile(
        r'^(?P<indent>\s+)(?P<name>[A-Z][A-Z_0-9]+)\s*[,\(;]',
        re.MULTILINE
    )
    
    for m in list(enum_const_pattern.finditer(content)):
        pos = m.start()
        if has_inline_comment_before(content, pos):
            continue
        
        const_name = m.group('name')
        # 跳过注解
        if const_name in ('POST', 'GET', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS',
                          'PUBLIC', 'PRIVATE', 'PROTECTED', 'STATIC', 'FINAL', 'ABSTRACT',
                          'CLASS', 'INTERFACE', 'ENUM', 'VOID', 'TRUE', 'FALSE', 'NULL',
                          'NEW', 'RETURN', 'IF', 'ELSE', 'FOR', 'WHILE', 'SWITCH', 'CASE',
                          'DEFAULT', 'BREAK', 'CONTINUE', 'TRY', 'CATCH', 'FINALLY',
                          'THROW', 'THROWS', 'IMPORT', 'PACKAGE', 'THIS', 'SUPER',
                          'EXTENDS', 'IMPLEMENTS', 'INSTANCEOF', 'SYNCHRONIZED',
                          'VOLATILE', 'TRANSIENT', 'NATIVE', 'STRICTFP', 'ASSERT',
                          'SERIAL', 'RECORD', 'SEALED', 'PERMITS', 'VAR', 'YIELD',
                          'OPEN', 'MODULE', 'REQUIRES', 'EXPORTS', 'OPENS', 'USES',
                          'PROVIDES', 'WITH', 'TO', 'FROM', 'AS', 'IN', 'OF'):
            continue
        
        comment = generate_enum_const_comment(const_name)
        if comment is None:
            continue
        
        indent = m.group('indent')
        comment_indented = indent + comment
        
        content = content[:pos] + comment_indented + '\n' + content[pos:]
        changes += 1
    
    # 4. 添加 public 方法 Javadoc
    method_pattern = re.compile(
        r'^(?P<indent>\s+)(?P<modifiers>public\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?)(?P<return_type>[\w<>,\s\[\]]+?)\s+(?P<name>\w+)\s*\((?P<params>[^)]*)\)',
        re.MULTILINE
    )
    
    for m in list(method_pattern.finditer(content)):
        pos = m.start()
        if has_javadoc_before(content, pos):
            continue
        
        method_name = m.group('name')
        return_type = m.group('return_type').strip()
        params_str = m.group('params').strip()
        
        # 跳过简单方法
        if method_name in ('toString', 'equals', 'hashCode', 'values', 'valueOf',
                          'clone', 'finalize', 'getClass', 'notify', 'notifyAll', 'wait'):
            continue
        if re.match(r'^(get|set|is)[A-Z]', method_name):
            continue
        
        # 解析参数
        params = []
        if params_str:
            for param in params_str.split(','):
                param = param.strip()
                if not param:
                    continue
                parts = param.rsplit(None, 1)
                if len(parts) == 2:
                    ptype, pname = parts
                    # 去掉注解
                    ptype = re.sub(r'@\w+(\([^)]*\))?\s*', '', ptype).strip()
                    params.append((pname, ptype))
        
        is_void = return_type == 'void'
        javadoc = generate_method_javadoc(method_name, params, return_type, is_void)
        if javadoc is None:
            continue
        
        indent = m.group('indent')
        javadoc_indented = '\n'.join(indent + line if line else line for line in javadoc.split('\n'))
        
        content = content[:pos] + javadoc_indented + '\n' + content[pos:]
        changes += 1
    
    if changes > 0 and not dry_run and content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
    
    return changes

def process_directory(directory, dry_run=False, verbose=False):
    """处理目录下所有 Java 文件"""
    total_changes = 0
    total_files = 0
    modified_files = 0
    
    for root, dirs, files in os.walk(directory):
        # 跳过 target 目录
        if 'target' in dirs:
            dirs.remove('target')
        if '.git' in dirs:
            dirs.remove('.git')
        
        for fname in files:
            if not fname.endswith('.java'):
                continue
            
            filepath = os.path.join(root, fname)
            total_files += 1
            
            changes = process_file(filepath, dry_run)
            if changes > 0:
                modified_files += 1
                total_changes += changes
                if verbose:
                    print(f'  [+] {filepath}: +{changes} comments')
    
    return total_files, modified_files, total_changes

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Java 注释自动增强脚本')
    parser.add_argument('path', help='要处理的文件或目录路径')
    parser.add_argument('--dry-run', action='store_true', help='只分析不修改')
    parser.add_argument('--verbose', '-v', action='store_true', help='显示详细输出')
    args = parser.parse_args()
    
    path = args.path
    
    if os.path.isfile(path):
        changes = process_file(path, args.dry_run)
        print(f'文件: {path}')
        print(f'  新增注释: {changes}')
    elif os.path.isdir(path):
        print(f'处理目录: {path}')
        total, modified, changes = process_directory(path, args.dry_run, args.verbose)
        print(f'  总文件数: {total}')
        print(f'  修改文件数: {modified}')
        print(f'  新增注释总数: {changes}')
    else:
        print(f'路径不存在: {path}')
        sys.exit(1)
