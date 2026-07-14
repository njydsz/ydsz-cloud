# ydsz-pmis-common-config

PMIS 公共配置模块 — 敏感配置加密与动态配置管理。

## 核心能力

- **AES-256-GCM 加密**：对敏感配置（密码、密钥、令牌）进行透明加解密
- **ENC() 格式识别**：配置文件中使用 `ENC(Base64密文)` 标识加密值
- **环境变量密钥**：密钥通过 `PMIS_CONFIG_ENCRYPT_KEY` 环境变量注入
- **早期解密**：在 ApplicationContextInitialized 阶段解密，确保 Bean 注入明文

## 使用方式

### 1. 设置密钥环境变量

```bash
export PMIS_CONFIG_ENCRYPT_KEY="your-secret-key"
```

### 2. 加密敏感配置

```java
ConfigEncryptor encryptor = new ConfigEncryptor("your-secret-key");
String encrypted = encryptor.encrypt("my-db-password");
// 输出: ENC(xJ8kL2mN3pQ5rS7tU9vWxYz...)
```

### 3. 在配置文件中使用

```yaml
spring:
  datasource:
    password: ENC(xJ8kL2mN3pQ5rS7tU9vWxYz...)
    username: ENC(aB3cD4eF5gH6iJ7kL8mN9pQ...)
```

### 4. 配置项

```yaml
pmis:
  config:
    encrypt:
      enabled: true
      secret-key-env: PMIS_CONFIG_ENCRYPT_KEY
```
