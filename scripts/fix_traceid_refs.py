import pathlib

base = pathlib.Path(r'd:\Code\ydsz\ydsz-pmis\ydsz-pmis-backend')

files_to_update = [
    # UserAccountServiceImpl.java
    r'ydsz-pmis-userinfo\ydsz-pmis-userinfo-server\src\main\java\com\njydsz\pmis\userinfo\server\service\impl\UserAccountServiceImpl.java',
    # FlowDelegateAuthServiceImpl.java
    r'ydsz-pmis-workflow\ydsz-pmis-workflow-server\src\main\java\com\njydsz\pmis\workflow\server\service\impl\FlowDelegateAuthServiceImpl.java',
    # FlowCcServiceImpl.java
    r'ydsz-pmis-workflow\ydsz-pmis-workflow-server\src\main\java\com\njydsz\pmis\workflow\server\service\impl\notification\FlowCcServiceImpl.java',
    # MessageTraceContext.java
    r'ydsz-pmis-message\ydsz-pmis-message-server\src\main\java\com\njydsz\pmis\message\server\tracing\MessageTraceContext.java',
    # MessageServiceImpl.java
    r'ydsz-pmis-message\ydsz-pmis-message-server\src\main\java\com\njydsz\pmis\message\server\service\impl\MessageServiceImpl.java',
    # MessageTraceServiceImpl.java
    r'ydsz-pmis-message\ydsz-pmis-message-server\src\main\java\com\njydsz\pmis\message\server\service\impl\MessageTraceServiceImpl.java',
    # InternalJobController.java
    r'ydsz-pmis-cronjob\ydsz-pmis-cronjob-web\src\main\java\com\njydsz\pmis\cronjob\web\controller\InternalJobController.java',
    # AlertScanner.java
    r'ydsz-pmis-cronjob\ydsz-pmis-cronjob-server\src\main\java\com\njydsz\pmis\cronjob\server\core\AlertScanner.java',
    # JobServiceImpl.java
    r'ydsz-pmis-cronjob\ydsz-pmis-cronjob-server\src\main\java\com\njydsz\pmis\cronjob\server\service\impl\JobServiceImpl.java',
]

for relpath in files_to_update:
    fpath = base / relpath
    if not fpath.exists():
        print(f'SKIP (not found): {relpath}')
        continue
    text = fpath.read_text(encoding='utf-8')
    
    # Replace import
    text = text.replace(
        'import com.njydsz.pmis.common.util.TraceIdUtil;',
        'import com.njydsz.pmis.common.util.id.TracerUtils;'
    )
    
    # Replace method calls
    text = text.replace('TraceIdUtil.getOrCreate()', 'TracerUtils.getOrCreateTraceId()')
    text = text.replace('TraceIdUtil.get()', 'TracerUtils.getTraceId()')
    text = text.replace('TraceIdUtil.generate()', 'TracerUtils.generateTraceId()')
    text = text.replace('TraceIdUtil.set(', 'TracerUtils.setTraceId(')
    text = text.replace('TraceIdUtil.clear()', 'TracerUtils.clear()')
    text = text.replace('TraceIdUtil.TRACE_ID_KEY', '"traceId"')
    
    fpath.write_text(text, encoding='utf-8')
    print(f'OK: {relpath}')
