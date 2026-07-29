import os, glob

BASE = "d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-exception"

# All files listed as "deleted" in git status
deleted_files = [
    "src/main/java/com/njydsz/common/exception/alert/ExceptionAlertEvent.java",
    "src/main/java/com/njydsz/common/exception/alert/ExceptionAlertListener.java",
    "src/main/java/com/njydsz/common/exception/alert/ExceptionAlertPublisher.java",
    "src/main/java/com/njydsz/common/exception/code/ErrorCodeDecoder.java",
    "src/main/java/com/njydsz/common/exception/code/ErrorCodeDocGenerator.java",
    "src/main/java/com/njydsz/common/exception/code/ErrorCodeEncoder.java",
    "src/main/java/com/njydsz/common/exception/code/ErrorCodeFactory.java",
    "src/main/java/com/njydsz/common/exception/code/ExternalExceptionCode.java",
    "src/main/java/com/njydsz/common/exception/code/RateLimitExceptionCode.java",
    "src/main/java/com/njydsz/common/exception/config/ExceptionAlertAutoConfiguration.java",
    "src/main/java/com/njydsz/common/exception/config/TraceFilterAutoConfiguration.java",
    "src/main/java/com/njydsz/common/exception/custom/CircuitBreakerException.java",
    "src/main/java/com/njydsz/common/exception/custom/ConcurrencyException.java",
    "src/main/java/com/njydsz/common/exception/custom/DegradeException.java",
    "src/main/java/com/njydsz/common/exception/custom/ExternalException.java",
    "src/main/java/com/njydsz/common/exception/custom/ValidationException.java",
    "src/main/java/com/njydsz/common/exception/custom/YdszExceptionBuilder.java",
    "src/main/java/com/njydsz/common/exception/custom/YdszTimeoutException.java",
    "src/main/java/com/njydsz/common/exception/enums/SubErrorCode.java",
    "src/main/java/com/njydsz/common/exception/handler/GrpcExceptionTranslator.java",
    "src/main/java/com/njydsz/common/exception/observability/TraceContext.java",
    "src/main/java/com/njydsz/common/exception/observability/TraceContextFilter.java",
    "src/main/java/com/njydsz/common/exception/sanitize/StackTraceSanitizer.java",
]

print("=== Checking deleted files ===")
for f in deleted_files:
    full = os.path.join(BASE, f)
    name = os.path.basename(f)
    status = "EXISTS" if os.path.exists(full) else "DELETED"
    print(f"  {status}: {name}")

# Check for additional-spring-configuration-metadata.json
print("\n=== Checking metadata files ===")
for root, dirs, files in os.walk(BASE):
    for f in files:
        if "metadata" in f and f.endswith(".json"):
            print(f"  Found: {os.path.join(root, f)}")
