import re, os

BASE = "d:/Code/ydsz/ydsz-pmis/ydsz-backend"

EXC_MAP = {
    "InfrastructureException": "SysException",
    "DuplicateException": "BusinessException",
    "RateLimitException": "BusinessException",
    "YdszSecurityException": "BusinessException",
    "ExternalException": "SysException",
    "ValidationException": "BusinessException",
    "YdszTimeoutException": "SysException",
    "ConcurrencyException": "BusinessException",
    "CircuitBreakerException": "SysException",
    "DegradeException": "SysException",
}

def fix_exception_refs(filepath):
    if not os.path.exists(filepath):
        print(f"  SKIP (not found): {filepath}")
        return
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()
    original = content
    for old_cls, new_cls in EXC_MAP.items():
        content = content.replace(
            f"import com.njydsz.common.exception.custom.{old_cls};",
            f"import com.njydsz.common.exception.custom.{new_cls};"
        )
        content = re.sub(r'\b' + old_cls + r'\b', new_cls, content)
    if content != original:
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  FIXED: {filepath}")
    else:
        print(f"  NO CHANGE: {filepath}")

def fix_trace_context_refs(filepath):
    if not os.path.exists(filepath):
        print(f"  SKIP (not found): {filepath}")
        return
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()
    original = content
    content = content.replace("TraceContext.getTraceId()", "MDC.get(TraceConstants.MDC_TRACE_ID_KEY)")
    content = content.replace("TraceContext.HEADER_TRACE_ID", "TraceConstants.TRACE_ID_HEADER")
    content = content.replace("TraceContext.HEADER_B3_TRACE_ID", '"X-B3-TraceId"')
    content = content.replace("TraceContext.clear()", "MDC.remove(TraceConstants.MDC_TRACE_ID_KEY)")
    content = content.replace(
        "import com.njydsz.common.exception.observability.TraceContext;",
        "import org.slf4j.MDC;\nimport com.njydsz.common.core.constant.TraceConstants;"
    )
    if content != original:
        lines = content.split("\n")
        seen = set()
        deduped = []
        for line in lines:
            s = line.strip()
            if s.startswith("import "):
                if s in seen:
                    continue
                seen.add(s)
            deduped.append(line)
        content = "\n".join(deduped)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  FIXED: {filepath}")
    else:
        print(f"  NO CHANGE: {filepath}")

print("=== Fixing deleted exception class references ===")
exc_files = [
    f"{BASE}/ydsz-cronjob/ydsz-cronjob-server/src/main/java/com/njydsz/cronjob/server/core/AlertSendException.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/service/IMessageSubscriber.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/active/ActiveMQPublisher.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/kafka/KafkaMessagePublisher.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/active/ActiveMQSubscriber.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/rabbit/RabbitMQPublisher.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/rocket/RocketMQSubscriber.java",
    f"{BASE}/ydsz-common/ydsz-common-queue/src/main/java/com/njydsz/common/queue/mq/rabbit/RabbitMQSubscriber.java",
    f"{BASE}/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe/csrf/impl/DefaultCsrfTokenGenerator.java",
    f"{BASE}/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe/csrf/impl/RedisCsrfTokenRepository.java",
    f"{BASE}/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe/csrf/impl/InMemoryCsrfTokenRepository.java",
    f"{BASE}/ydsz-common/ydsz-common-safe/src/main/java/com/njydsz/common/safe/captcha/generator/ImageCaptchaGenerator.java",
    f"{BASE}/ydsz-common/ydsz-common-lock/src/main/java/com/njydsz/common/lock/aspect/RepeatSubmitAspect.java",
    f"{BASE}/ydsz-common/ydsz-common-jdbc/src/main/java/com/njydsz/common/jdbc/exception/TenantIsolationException.java",
    f"{BASE}/ydsz-agent/ydsz-agent-server/src/main/java/com/njydsz/agent/server/chat/AgentRequestGuard.java",
    f"{BASE}/ydsz-agent/ydsz-agent-server/src/test/java/com/njydsz/agent/server/chat/AgentRequestGuardTest.java",
    f"{BASE}/ydsz-agent/ydsz-agent-domain/src/main/java/com/njydsz/agent/domain/gateway/LlmException.java",
    f"{BASE}/ydsz-literule/ydsz-literule-server/src/main/java/com/njydsz/literule/server/spi/FactCollectionException.java",
    f"{BASE}/ydsz-literule/ydsz-literule-domain/src/main/java/com/njydsz/literule/domain/model/ModelInvocationException.java",
]
for f in exc_files:
    fix_exception_refs(f)

print("\n=== Fixing TraceContext references ===")
trace_files = [
    f"{BASE}/ydsz-literule/ydsz-literule-server/src/main/java/com/njydsz/literule/server/core/DefaultRuleEngine.java",
    f"{BASE}/ydsz-literule/ydsz-literule-server/src/test/java/com/njydsz/literule/server/core/DefaultRuleEngineTest.java",
]
for f in trace_files:
    fix_trace_context_refs(f)

print("\n=== Done ===")
