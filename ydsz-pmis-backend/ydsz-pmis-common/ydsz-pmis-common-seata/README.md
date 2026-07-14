# ydsz-pmis-common-seata

PMIS 公共事务模块 — 分布式事务抽象（Seata/SAGA/TCC 统一接口）。

## 核心能力

- **统一接口**：`DistributedTransactionManager` 抽象三种分布式事务模式
- **TCC 模式**：Try-Confirm-Cancel 三阶段，业务层手动补偿
- **SAGA 模式**：长事务编排，正向操作 + 补偿操作
- **Local 降级**：单机模式下使用本地 `@Transactional`
- **Seata 预留**：Seata AT 模式集成接口预留

## 使用方式

### TCC 模式

```java
@Component
public class OrderTccAction implements TccAction<OrderResult> {
    @Override
    public OrderResult tryAction(TccContext ctx) throws Exception {
        inventoryMapper.freeze(ctx.get("skuId"), ctx.get("qty"));
        return new OrderResult(ctx.getXid());
    }

    @Override
    public void confirmAction(TccContext ctx) throws Exception {
        inventoryMapper.deductFrozen(ctx.get("skuId"), ctx.get("qty"));
    }

    @Override
    public void cancelAction(TccContext ctx) throws Exception {
        inventoryMapper.unfreeze(ctx.get("skuId"), ctx.get("qty"));
    }
}

// 调用
tccTransactionManager.executeTcc("createOrder", orderTccAction);
```

### SAGA 模式

```java
seataManager.executeWithCompensation("transfer", () -> {
    accountMapper.deduct(fromId, amount);
    accountMapper.add(toId, amount);
    return null;
}, () -> {
    // 补偿：反向操作
    accountMapper.add(fromId, amount);
    accountMapper.deduct(toId, amount);
});
```

### 配置

```yaml
pmis:
  seata:
    enabled: true
    default-type: LOCAL  # LOCAL / TCC / SEATA_AT / SAGA
    tcc-retry-count: 3
    saga-max-retries: 5
```
