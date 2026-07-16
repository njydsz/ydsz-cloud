# ydsz-common-util

PMIS 通用工具类库 — 99 个 Java 源文件覆盖 ID 生成、加密、HTTP、字符串、日期、文件、集合、Bean 拷贝、JSON、Spring 等领域。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 99 |
| **零依赖原则** | 核心工具不依赖 Spring（可选集成） |

## 核心能力

### ID 生成

| 类 | 说明 |
|---|---|
| `SnowflakeUtils` | 雪花算法 ID 生成器（workerId 自动分配） |
| `WorkerIdRegistry` | WorkerId 注册中心（Redis 协调） |
| `UUIDUtils` | UUID 工具（带连字符 / 不带连字符） |
| `SequenceUtils` | 有界序列号生成 |
| `RandomUtils` | 安全随机数工具 |

### 加密与安全

| 类 | 说明 |
|---|---|
| `AesUtils` / `AesGcmCrypto` | AES-CBC / AES-256-GCM 加解密 |
| `Rsa2Utils` | RSA2 非对称加解密 + 签名验签 |
| `CryptoUtil` / `CryptoSignUtil` | 通用加解密 + 签名工具 |
| `DigestUtils` | SHA-256 / MD5 / HMAC 摘要工具 |
| `PwdUtils` | BCrypt / PBKDF2 密码哈希 |
| `PasswordEncoder` / `Pbkdf2PasswordEncoder` | 密码编码器接口与实现 |
| `PasswordStrengthEvaluator` | 密码强度评估器 |

### HTTP 工具

| 类 | 说明 |
|---|---|
| `OkHttpUtils` / `HttpClientFactory` | OkHttp 封装 + 连接池工厂 |
| `ServletUtils` / `WebFluxUtils` | Servlet / WebFlux 请求工具 |
| `CookieUtils` | Cookie 读写工具 |
| `UrlUtils` / `UrlPathUtils` | URL 解析与路径处理 |
| `ResponseUtils` | HTTP 响应写入工具 |

### 字符串与文本

| 类 | 说明 |
|---|---|
| `StringUtils` | 字符串判空 / 分割 / 连接 / 驼峰转换 |
| `StringConvertUtils` | 类型安全转换 |
| `StringFormatterUtils` | 字符串格式化（占位符替换） |
| `CharsetUtils` | 字符集工具 |
| `RegexUtils` | 正则验证（手机 / 邮箱 / 身份证 / IP） |

### 日期与数字

| 类 | 说明 |
|---|---|
| `LocalDateTimeUtils` | 日期时间格式化 / 计算 / 比较 |
| `NumberUtils` / `BigDecimalUtils` | 数字工具（精度计算 / 百分比 / 格式化） |
| `MoneyUtils` | 金额计算（分 ↔ 元 / 加减乘除 / 四舍五入） |

### 文件与 IO

| 类 | 说明 |
|---|---|
| `FileUtils` | 文件读写 / 复制 / 删除 / 目录遍历 |
| `FileTypeUtils` | 文件类型检测（扩展名 + Magic Number） |
| `FileValidator` | 文件校验（大小 / 类型 / 文件名安全） |
| `ImageUtils` | 图片处理（缩放 / 水印 / 格式转换） |
| `IOUtils` | 流读写 / 关闭 / Base64 转换 |
| `CompressUtils` | ZIP / GZIP 压缩解压 |
| `FtpUtils` / `FtpConfig` | FTP 上传下载 |

### 集合与对象

| 类 | 说明 |
|---|---|
| `CollectionUtils` / `ListUtils` / `SetUtils` / `MapUtils` | 集合工具（判空 / 分片 / 去重 / 排序） |
| `ArrayUtils` / `SortUtils` | 数组工具 |
| `ObjectUtils` | 对象工具（默认值 / 深拷贝 / 比较） |
| `BeanCopyUtils` | Bean 属性拷贝（支持 Map ↔ Bean / 忽略字段 / 嵌套拷贝） |
| `BeanCopyOptions` / `Converters` | 拷贝选项与类型转换器 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `SpringContextHolder` | ApplicationContext 持有者（静态获取 Bean） |
| `SpringBeanUtils` | Bean 获取 / 注册 / 注入工具 |
| `SpringPropertyUtils` | 配置属性读取工具 |

### 其他工具

| 类 | 说明 |
|---|---|
| `JsonUtils` | JSON 序列化 / 反序列化（支持 Jackson / Gson / Fastjson 自动探测） |
| `YamlUtils` | YAML 解析工具 |
| `TraceIdUtil` | TraceId 生成 / 传递 / MDC 设置 |
| `CursorHelper` | 游标分页编码 / 解码 |
| `HashUtils` | 一致性哈希环 |
| `ReflectUtils` | 反射工具（字段获取 / 方法调用 / 注解扫描） |
| `ClassUtils` | 类加载器工具 |
| `ValidateUtils` | 参数校验工具（非空 / 正则 / 范围） |
| `AssertUtils` | 断言工具（非空 / 状态 / 表达式） |
| `ExceptionUtils` | 异常堆栈转字符串 |
| `CodeUtils` | 编码工具（Base32 / Hex / URL 编码） |
| `SystemUtils` | 系统信息（OS / JVM / CPU 核数） |
| `IpAddrUtils` / `IpInfoUtils` / `MacAddressUtils` | IP / MAC 地址工具 |
| `SAMLUtils` / `DOMUtils` | SAML / XML 工具 |
| `MessageUtils` | 国际化消息工具 |

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `UtilAutoConfiguration` | 总是激活 |
| `SnowflakeAutoConfiguration` | Redis 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-util</artifactId>
</dependency>
```
