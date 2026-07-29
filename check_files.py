import os
files = [
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/custom/YdszExceptionBuilder.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/custom/CircuitBreakerException.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/custom/InfrastructureException.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/custom/DuplicateException.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/sanitize/StackTraceSanitizer.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/observability/TraceContext.java",
    "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception/src/main/java/com/njydsz/common/exception/alert/ExceptionAlertPublisher.java",
]
for f in files:
    name = os.path.basename(f)
    status = "EXISTS" if os.path.exists(f) else "DELETED"
    print(f"  {status}: {name}")
