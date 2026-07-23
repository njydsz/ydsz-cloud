#!/usr/bin/env python3
"""Remove deprecated methods/fields from source files."""
import os
import re

ROOT = r'd:\Code\ydsz\ydsz-pmis\ydsz-backend'

def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(path, content):
    with open(path, 'w', encoding='utf-8', newline='') as f:
        f.write(content)

# ============================================================
# PageResponse.java - remove deprecated methods
# ============================================================
page_response_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-core\src\main\java\com\njydsz\common\core\response\PageResponse.java')
content = read_file(page_response_path)

# Remove the deprecated success(T data) method
pattern = r'''
    /\*\*
     \* 创建成功分页响应（无分页信息）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    public static <T> PageResponse<T> success\(T data\) \{
        return success\(0L, 1L, 10L, data\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove the deprecated of(List, ...) method
pattern2 = r'''
    /\*\*
     \* 从列表构建分页响应（向后兼容）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    public static <T> PageResponse<T> of\(List<T> list, long total, long pageNum, long pageSize\) \{
        return success\(total, pageNum, pageSize, \(T\) list\);
    \}

'''
content = re.sub(pattern2, '', content, flags=re.DOTALL)

# Remove the deprecated getList() method
pattern3 = r'''
    /\*\*
     \* 获取数据列表（向后兼容）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    public List<T> getList\(\) \{
        Object data = getData\(\);
        return data instanceof List \? \(List<T>\) data : List\.of\(\);
    \}

'''
content = re.sub(pattern3, '', content, flags=re.DOTALL)

write_file(page_response_path, content)
print("PageResponse.java: deprecated methods removed")

# ============================================================
# RequestContext.java - remove deprecated methods
# ============================================================
rc_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-core\src\main\java\com\njydsz\common\core\context\RequestContext.java')
content = read_file(rc_path)

# Remove getOptional(String) method
pattern = r'''
    /\*\*
     \* 获取属性（Optional）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    public static <T> Optional<T> getOptional\(String key\) \{
        Object value = CONTEXT_HOLDER\.get\(\)\.get\(key\);
        Optional<T> result = Optional\.empty\(\);
        if \(value != null\) \{
            result = Optional\.of\(\(T\) value\);
        \}
        return result;
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove capture() method
pattern = r'''
    /\*\*
     \* 捕获当前线程的上下文为 Map.*
     \* @deprecated.*
     \*/
    @Deprecated
    public static Map<String, Object> capture\(\) \{
        Map<String, Object> current = CONTEXT_HOLDER\.get\(\);
        return current\.isEmpty\(\) \? new HashMap<>\(\) : new HashMap<>\(current\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove runWithContext(Map, Supplier) method
pattern = r'''
    /\*\*
     \* 在指定上下文中执行 Supplier.*
     \* @deprecated.*
     \*/
    @Deprecated
    public static <T> T runWithContext\(Map<String, Object> context, Supplier<T> supplier\) \{
        try \{
            restore\(context\);
            return supplier\.get\(\);
        \} finally \{
            clear\(\);
        \}
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove runWithContext(Map, Runnable) method
pattern = r'''
    /\*\*
     \* 在指定上下文中执行 Runnable.*
     \* @deprecated.*
     \*/
    @Deprecated
    public static void runWithContext\(Map<String, Object> context, Runnable runnable\) \{
        try \{
            restore\(context\);
            runnable\.run\(\);
        \} finally \{
            clear\(\);
        \}
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove wrapCallable method
pattern = r'''
    /\*\*
     \* 包装 Callable.*
     \* @deprecated.*
     \*/
    @Deprecated
    public static <T> Callable<T> wrapCallable\(Callable<T> callable, Map<String, Object> context\) \{
        return \(\) -> \{
            try \{
                restore\(context\);
                return callable\.call\(\);
            \} finally \{
                clear\(\);
            \}
        \};
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Now check if restore() is still used. If not, remove it too.
# restore is used by snapshot() -> actually no, snapshot just returns a copy
# Let's check if restore is referenced elsewhere
if 'restore(' not in content.replace('private static void restore(', ''):
    # restore is only defined, not called - remove it
    pattern = r'''
    /\*\*
     \* 恢复上下文到当前线程
    .*
     \*/
    private static void restore\(Map<String, Object> context\) \{
        CONTEXT_HOLDER\.remove\(\);
        if \(context != null && !context\.isEmpty\(\)\) \{
            CONTEXT_HOLDER\.set\(new HashMap<>\(context\)\);
        \}
    \}

'''
    content = re.sub(pattern, '', content, flags=re.DOTALL)

# Also remove the Callable import if no longer used
if 'Callable' not in content.replace('import java.util.concurrent.Callable;', ''):
    content = content.replace('import java.util.concurrent.Callable;\n', '')

write_file(rc_path, content)
print("RequestContext.java: deprecated methods removed")

# ============================================================
# CacheType.java - remove deprecated enum values
# ============================================================
ct_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-cache\src\main\java\com\njydsz\common\cache\builder\CacheType.java')
content = read_file(ct_path)

# Remove TTL, WEAK_KEY, WEAK_VALUE, SOFT_VALUE enum values
for enum_val in ['TTL', 'WEAK_KEY', 'WEAK_VALUE', 'SOFT_VALUE']:
    # Remove the enum value with its Javadoc
    pattern = rf'''
  /\*\*
   \* [^\n]*
  .*
   \* @deprecated.*
   \*/
  @Deprecated
  {enum_val},

'''
    content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(ct_path, content)
print("CacheType.java: deprecated enum values removed")

# ============================================================
# CacheBuilder.java - remove buildWTinyLFU, TTLCache refs, WTinyLFUCache refs
# ============================================================
cb_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-cache\src\main\java\com\njydsz\common\cache\builder\CacheBuilder.java')
content = read_file(cb_path)

# Remove buildWTinyLFU method
pattern = r'''
  /\*\*
   \* 构建 WTinyLFU 缓存实例
  .*
   \* @deprecated.*
   \*/
  @Deprecated
  public WTinyLFUCache<K, V> buildWTinyLFU\(\) \{
    int effectiveSize = maximumSize > 0 \? \(int\) maximumSize : 1000;
    WTinyLFUCache<K, V> cache = new WTinyLFUCache<>\(effectiveSize\);
    if \(removalListener != null\) \{
      cache\.addListener\(removalListener\);
    \}
    return cache;
  \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove TTLCache import
content = content.replace('import com.njydsz.common.cache.internal.ttl.TTLCache;\n', '')
# Remove WTinyLFUCache import
content = content.replace('import com.njydsz.common.cache.internal.tinylfu.WTinyLFUCache;\n', '')

# Fix the applyDecorators method - remove TTLCache instanceof check
content = content.replace(' && !(cache instanceof TTLCache)', '')

# Fix createBaseCache - remove TTL, WEAK_KEY, WEAK_VALUE, SOFT_VALUE cases
# Remove TTL case
pattern = r'''
      case TTL:
        // Deprecated:.*?return new ConcurrentCache<>\(initialCapacity\);

'''
content = re.sub(pattern, '\n      case TTL:\n        // Redirect to CONCURRENT + ExpirableCache decorator\n        if (expireAfterWriteDuration <= 0 && expireAfterAccessDuration <= 0) {\n          expireAfterWriteDuration = 5;\n          expireAfterWriteUnit = TimeUnit.MINUTES;\n        }\n        return new ConcurrentCache<>(initialCapacity);\n\n', content, flags=re.DOTALL)

# Actually, let's just remove the TTL case entirely and let it fall through to CONCURRENT
# Wait, that won't work with switch. Let me handle it differently.
# Actually the TTL case is still needed as a redirect. Let me keep it but remove the @Deprecated comment.
# The enum value itself is removed, so we need to remove the case too.

# Remove the TTL case entirely
pattern = r'      case TTL:.*?return new ConcurrentCache<>\(initialCapacity\);\n\n'
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove WEAK_KEY case
pattern = r'      case WEAK_KEY:.*?return new WeakKeyCache<>\(\);\n\n'
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove WEAK_VALUE case
pattern = r'      case WEAK_VALUE:.*?return new WeakValueCache<>\(\);\n\n'
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove SOFT_VALUE case
pattern = r'      case SOFT_VALUE:.*?return new SoftValueCache<>\(\);\n\n'
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove unused imports for reference caches if no longer used in switch
# Actually, weakKeys()/weakValues()/softValues() methods still use WeakKeyCache etc via flags
# So we keep those imports.

write_file(cb_path, content)
print("CacheBuilder.java: deprecated methods and cases removed")

# ============================================================
# ArrayUtils.java - remove deprecated toArray method
# ============================================================
au_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\array\ArrayUtils.java')
content = read_file(au_path)

pattern = r'''
    /\*\*
     \* 将 Collection 转换为数组
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public static <T> T\[\] toArray\(Collection<T> collection, Class<?> clazz\) \{
        Objects\.requireNonNull\(clazz, "clazz must not be null"\);
        if \(collection == null \|\| collection\.isEmpty\(\)\) \{
            return newArray\(clazz, 0\);
        \}
        return collection\.toArray\(newArray\(clazz, collection\.size\(\)\)\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(au_path, content)
print("ArrayUtils.java: deprecated toArray removed")

# ============================================================
# CollectionUtils.java - remove deprecated methods
# ============================================================
cu_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\collection\CollectionUtils.java')
content = read_file(cu_path)

# Remove isEmpty(Object[]) method
pattern = r'''
    /\*\*
     \* 判断数组是否为空
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public static boolean isEmpty\(Object\[\] array\) \{
        return array == null \|\| array\.length == 0;
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove isNotEmpty(Object[]) method
pattern = r'''
    /\*\*
     \* 判断数组是否不为空
    .*
     \* @see #isEmpty\(Object\[\]\)
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public static boolean isNotEmpty\(Object\[\] array\) \{
        return !isEmpty\(array\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove arrayToList method
pattern = r'''
    /\*\*
     \* 将数组转换为 List
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public static <T> List<T> arrayToList\(T\[\] array\) \{
        if \(isEmpty\(array\)\) \{
            return Collections\.emptyList\(\);
        \}
        return new ArrayList<>\(Arrays\.asList\(array\)\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(cu_path, content)
print("CollectionUtils.java: deprecated methods removed")

# ============================================================
# AesGcmCrypto.java - remove deprecated encrypt/decrypt with keyId
# ============================================================
agc_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\security\AesGcmCrypto.java')
content = read_file(agc_path)

# Remove encrypt(String, String) method
pattern = r'''
    /\*\*
     \* 加密并返回 Base64 字符串（带 keyId 参数，向后兼容）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public String encrypt\(String plaintext, String keyId\) \{
        return encrypt\(plaintext\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove decrypt(String, String) method
pattern = r'''
    /\*\*
     \* 解密 Base64 密文（带 keyId 参数，向后兼容）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public String decrypt\(String base64Ciphertext, String keyId\) \{
        return decrypt\(base64Ciphertext\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(agc_path, content)
print("AesGcmCrypto.java: deprecated methods removed")

# ============================================================
# PwdUtils.java - remove deprecated checkPasswordStrengthStr
# ============================================================
pu_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\security\PwdUtils.java')
content = read_file(pu_path)

pattern = r'''
    /\*\*
     \* 检查密码强度（返回字符串，向后兼容）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    public static String checkPasswordStrengthStr\(String password\) \{
        return checkPasswordStrength\(password\)\.name\(\);
    \}
'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(pu_path, content)
print("PwdUtils.java: deprecated method removed")

# ============================================================
# ContextPropagationUtils.java - remove deprecated registerContextProvider
# ============================================================
csp_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-util\src\main\java\com\njydsz\common\util\concurrent\ContextPropagationUtils.java')
content = read_file(csp_path)

pattern = r'''
    /\*\*
     \* 注册上下文提供者（仅 getter，setter 为空操作）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    public static void registerContextProvider\(String name, Supplier<String> getter\) \{
        registerContextProvider\(name, getter, \(n, v\) -> \{ \}\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(csp_path, content)
print("ContextPropagationUtils.java: deprecated method removed")

# ============================================================
# RedisRateLimiter.java - remove deprecated SLIDING_WINDOW_LUA and tryAcquireSlidingWindow
# ============================================================
rrl_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-redis\src\main\java\com\njydsz\common\redis\service\RedisRateLimiter.java')
content = read_file(rrl_path)

# Remove SLIDING_WINDOW_LUA field with its Javadoc
pattern = r'''
    /\*\*
     \* 滑动窗口限流 Lua 脚本
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    private static final String SLIDING_WINDOW_LUA =.*?end";\n
'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove tryAcquireSlidingWindow method
pattern = r'''
    /\*\*
     \* 滑动窗口限流
    .*
     \*/
    public boolean tryAcquireSlidingWindow\(String key, int limit, Duration window\) \{.*?return handleException\("滑动窗口限流", key, e\);
        \}
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(rrl_path, content)
print("RedisRateLimiter.java: deprecated field and method removed")

# ============================================================
# EnhancedLoadingCache.java - remove deprecated constructors
# ============================================================
elc_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-cache\src\main\java\com\njydsz\common\cache\internal\loading\EnhancedLoadingCache.java')
content = read_file(elc_path)

# Remove the first deprecated constructor
pattern = r'''
  /\*\*
   \* 默认构造函数（无自动刷新）
  .*
   \* @deprecated.*
   \*/
  @Deprecated
    public EnhancedLoadingCache\(Cache<K, V> cache, CacheLoader<K, V> loader\) \{
    this\(cache, loader, null, 0, TimeUnit\.NANOSECONDS, null, true, true\);
  \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove the second deprecated constructor
pattern = r'''
  /\*\*
   \* 完整构造函数
  .*
   \* @deprecated.*
   \*/
  @Deprecated
    public EnhancedLoadingCache\(
      Cache<K, V> cache,
      CacheLoader<K, V> loader,
      Executor executor,
      long refreshInterval,
      TimeUnit refreshUnit,
      Executor refreshExecutor,
      boolean recordStats\) \{
    this\(cache, loader, executor, refreshInterval, refreshUnit, refreshExecutor, recordStats, true\);
  \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(elc_path, content)
print("EnhancedLoadingCache.java: deprecated constructors removed")

# ============================================================
# TokenBlacklistService.java - remove deprecated sha256 method
# ============================================================
tbs_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-auth\src\main\java\com\njydsz\common\auth\service\TokenBlacklistService.java')
content = read_file(tbs_path)

pattern = r'''
    /\*\*
     \* @deprecated 使用 \{?@link AuthDigestUtils#sha256Hex\(String\)\}?
     \*/
    @Deprecated\(since = "1\.1\.0", forRemoval = true\)
    private static String sha256\(String input\) \{
        return AuthDigestUtils\.sha256Hex\(input\);
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove unused imports
if 'java.security.MessageDigest' in content and 'MessageDigest' not in content.replace('import java.security.MessageDigest;', '').replace('MessageDigest.isEqual', ''):
    pass  # MessageDigest.isEqual is still used
if 'java.security.NoSuchAlgorithmException' in content and 'NoSuchAlgorithmException' not in content.replace('import java.security.NoSuchAlgorithmException;', ''):
    content = content.replace('import java.security.NoSuchAlgorithmException;\n', '')
if 'java.util.HexFormat' in content and 'HexFormat' not in content.replace('import java.util.HexFormat;', '').replace('HexFormat', ''):
    content = content.replace('import java.util.HexFormat;\n', '')

write_file(tbs_path, content)
print("TokenBlacklistService.java: deprecated method removed")

# ============================================================
# DefaultCacheKeyStrategy.java - remove deprecated sha256 method
# ============================================================
dcks_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-auth\src\main\java\com\njydsz\common\auth\strategy\DefaultCacheKeyStrategy.java')
content = read_file(dcks_path)

pattern = r'''
    /\*\*
     \* 计算 SHA-256 摘要并转为十六进制字符串。
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.1\.0", forRemoval = true\)
    private static String sha256\(String input\) \{
        return AuthDigestUtils\.sha256Hex\(input\);
    \}
'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove unused imports
for imp in ['import java.nio.charset.StandardCharsets;', 'import java.security.MessageDigest;', 'import java.security.NoSuchAlgorithmException;', 'import java.util.HexFormat;']:
    # Check if the class is still referenced
    class_name = imp.split('.')[-1].rstrip(';\n')
    remaining = content.replace(imp, '')
    if class_name not in remaining:
        content = content.replace(imp + '\n', '')

write_file(dcks_path, content)
print("DefaultCacheKeyStrategy.java: deprecated method and unused imports removed")

# ============================================================
# AuditProperties.java - remove deprecated getQueueCapacity/setQueueCapacity
# ============================================================
ap_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-audit\src\main\java\com\njydsz\common\audit\config\AuditProperties.java')
content = read_file(ap_path)

pattern = r'''
    /\*\*
     \* @deprecated.*
     \*/
    @Deprecated
    public int getQueueCapacity\(\) \{
        return executorQueueCapacity;
    \}

    /\*\*
     \* @deprecated.*
     \*/
    @Deprecated
    public void setQueueCapacity\(int queueCapacity\) \{
        this\.executorQueueCapacity = queueCapacity;
    \}

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(ap_path, content)
print("AuditProperties.java: deprecated methods removed")

# ============================================================
# BaseQuery.java - remove deprecated fields and update hasTimeRange
# ============================================================
bq_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-domain\src\main\java\com\njydsz\common\domain\query\BaseQuery.java')
content = read_file(bq_path)

# Remove startTime field
pattern = r'''
    /\*\*
     \* 开始时间（字符串格式）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    private transient String startTime;

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove endTime field
pattern = r'''
    /\*\*
     \* 结束时间（字符串格式）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    private transient String endTime;

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Remove keyword field
pattern = r'''
    /\*\*
     \* 关键字（已废弃）
    .*
     \* @deprecated.*
     \*/
    @Deprecated\(since = "1\.0\.0", forRemoval = true\)
    private String keyword;

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

# Update hasTimeRange() to not reference startTime/endTime
content = content.replace(
    'return startTime != null || endTime != null\n                || startDateTime != null || endDateTime != null;',
    'return startDateTime != null || endDateTime != null;'
)

write_file(bq_path, content)
print("BaseQuery.java: deprecated fields removed and hasTimeRange updated")

# ============================================================
# DocProperties.java - remove deprecated basePackage field
# ============================================================
dp_path = os.path.join(ROOT, r'ydsz-common\ydsz-common-base\src\main\java\com\njydsz\common\base\config\DocProperties.java')
content = read_file(dp_path)

pattern = r'''
        /\*\*
         \* 基础包路径，用于扫描 Controller
        .*
         \* @deprecated.*
         \*/
        @Deprecated
        private String basePackage = "";

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(dp_path, content)
print("DocProperties.java: deprecated field removed")

# ============================================================
# LiteRuleProperties.java - remove deprecated evaluator field
# ============================================================
lrp_path = os.path.join(ROOT, r'ydsz-literule\ydsz-literule-server\src\main\java\com\njydsz\literule\server\config\LiteRuleProperties.java')
content = read_file(lrp_path)

pattern = r'''
    /\*\*
     \* 表达式引擎类型（2\.1\.0 起已废弃.*?）
    .*
     \* @deprecated.*
     \*/
    @Deprecated
    private String evaluator = "liteexpr";

'''
content = re.sub(pattern, '', content, flags=re.DOTALL)

write_file(lrp_path, content)
print("LiteRuleProperties.java: deprecated field removed")

print("\n=== All source file edits complete ===")
