# ydsz-common-excel 全面分析报告 —— 对标 FastExcel/EasyExcel 与大厂研发规范

> 分析日期：2026-09-01 ｜ 口径：源码逐行核验 + 命令实跑（离线编译 BUILD SUCCESS，10s，fresh-maven + Corretto JDK21）
> 模块规模：78 源文件 / 17,768 行 / **0 测试**（无 src/test 目录）
> 结论前置：**默认开启的"零 POI 快速路径"存在 5 个 P0 级正确性缺陷（含 1 个必然 NPE），最快的止血手段是把 `use-fast-reader`/`use-fast-writer` 默认值翻转为 `false` 回退 POI 兼容路径。**

---

## 一、对标竞品基线（2026-09 事实核查）

| 竞品 | 状态 | 关键事实 |
|---|---|---|
| EasyExcel (com.alibaba) | **已归档** | 2024-11 官宣停更，2025-09-04 仓库归档只读 |
| FastExcel (cn.idev.excel) | 活跃，1.3.0（2025-08） | 原作者续作，API 与 EasyExcel 全兼容；1.0.0 起新增"读取指定行数"与 Excel→PDF |
| Apache Fesod (org.apache.fesod) | 孵化中 | FastExcel 2025-09-17 捐入 Apache，POI 官方维护者参与协作 |
| pig4cloud excel-spring-boot-starter | 活跃 | `@RequestExcel`/`@ResponseExcel` 注解式导入导出，注解驱动 DX 领先 |
| 本模块 ydsz-common-excel | 自研 1.0.0 | 双引擎（零 POI + POI 兼容）、模板填充、公式注入防护、Micrometer、Spring Boot 装配 |

对标判断：本模块的**设计理念**（流式读写、监听器模型、注解驱动、SPI 转换器、防护意识、可观测性）与 EasyExcel 系概念对齐且有局部超前（公式注入防护在 FastExcel 中反而无内置）；但**工程质量**与 FastExcel（继承 EasyExcel 数百个单测 + 6 年 issue 沉淀）差距是数量级的——全部 5 个 P0 都落在自研"零 POI 快速路径"上。

---

## 二、P0：默认快速路径整体不可用（5 项，均已逐行核验）

### P0-1 InputStream 读取必然 NPE（最常见 Web 上传路径被击穿）
- 证据：`ExcelReader.java:477` 判定 `fileSizeMB >= thresholdMB || config.isUseFastReader()` —— 默认 `useFastReader=true` 使**类型化 XLSX 读取永远走快速路径**；`:479-481` 执行 `new FileInputStream(filePath != null ? filePath : metadata.getFile().getAbsolutePath())`。
- 当调用方以 InputStream 传入（`filePath==null` 且 `file==null`）时，`metadata.getFile().getAbsolutePath()` **必然 NPE**。
- 业务影响：`UserExcelServiceImpl.java:201` 正是 `ExcelFacade.read(inputStream, UserImportDTO.class)` —— 用户导入功能在默认配置下不可用。这也解释了为何零测试之下该缺陷能存活至今。

### P0-2 快速读取路径列映射失效（对象全空/字段错位）
- 证据：`ExcelReader.java:483` 传入的 `columnMetadataArray` 仅在 POI 路径 `parseSheet`（`:667`）构建，快速路径从未初始化 → `SuperFastExcelReader.setColumnMetadataArray(null)`；`SuperFastExcelReader` 内部无 HeaderAnalyzer 回退（全文件 grep 零引用）。
- 后果：即使绕过 P0-1（传 File 而非 InputStream），`@ExcelProperty(index/order)` 映射不生效，映射退化为顺序猜测，字段错位或全空。

### P0-3 快速写入路径小数全部截断为整数
- 证据：`SuperFastExcelWriter.java:418`：`writeNumberCell(col, ((Number) value).longValue())` —— 对 **所有** Number 类型（Double/Float/BigDecimal）强转 long。
- 后果：默认 `useFastWriter=true` 下导出任何含小数的数据（价格、评分、金额）全部丢精度。对财务/规则决策表场景（literule `DecisionTableExcelExporter`）是数据损坏级缺陷。

### P0-4 @ExcelIgnore 无法标注到字段（注解目标错误）
- 证据：`ExcelIgnore.java:49` `@Target(ElementType.TYPE)` —— 只能标在类上，与其文档语义"忽略字段（不参与映射）"（README §4）直接矛盾；EasyExcel 同名注解为 FIELD 目标。
- 后果：字段级忽略能力实际不存在；若类上误标则整类被忽略。

### P0-5 两条读引擎 headRowNumber 语义错位（off-by-one）
- 证据：快速路径 `SheetXmlReader.java:380` 按 1-based 行号处理（`currentRow == reader.headRowNumber`，默认 1 = 第一行表头，正确）；POI 兼容路径 `ExcelReader.parseSheet:657` `sheet.getRow(headRowNumber)` 按 **0-based 索引**取行（默认 1 = 第二行）。
- 后果：同一配置在两引擎下表头行错位一行；POI 路径（xls / clazz=null / 关闭 fast）默认会把第二行当表头、数据从第三行起读，静默丢首行数据。

### P0 根因与止血
- 根因：**零测试**。8-29 全仓审计已列为 P0-2 待办（"ydsz-common 自研引擎补测试"），本次坐实其紧迫性——5 个 P0 全部属于"写第一个 round-trip 单测就会爆炸"的类别。
- 止血方案（一行改动）：`ExcelProperties` 中 `useFastReader`/`useFastWriter` 默认值改 `false`，全部流量回退到成熟的 POI 兼容路径（XSSF/HSSF/SXSSF），立即消除 P0-1/2/3 暴露面；性能回退换来正确性。

---

## 三、P1：安全、配置接线与功能缺口（8 项）

| # | 问题 | 证据 | 建议 |
|---|---|---|---|
| P1-1 | 公式注入防护默认前缀仅 `=`,`+`，低于 OWASP/业界标准 `=,+,-,@,\t,\r`；README 宣称覆盖 `-`/`@` 与代码不符 | `FormulaInjectionGuard.java:65` | 默认对齐四字符+控制字符；`\t`/`\r` 开头也拦截 |
| P1-2 | XLSX 路径用加 `'` 前缀转义——OOXML 中字符串单元格（`t="s"/inlineStr`）本就不会被求值，加 `'` 反而**字面显示撇号**污染数据（`'` 前缀是 CSV 注入的正确做法，非 XLSX） | `FormulaInjectionGuard.java:105-113` | XLSX 路径改为正确类型写入（字符串单元格天然安全）或 quotePrefix 样式；CSV 路径保留 `'` |
| P1-3 | Spring 配置接线断裂：`ExcelTemplate` 持有 config 但从不传给 Facade（`:54-81`）；`ExcelExportHelper` 根本无 config 字段——仅 `ExcelWebSupport` 真正生效，`ydsz.excel.*` 对官方推荐入口大半无效 | `ExcelTemplate.java`、`ExcelAutoConfiguration.java:64-68` | 三个入口统一注入并传递 ExcelConfig |
| P1-4 | `ExcelHealthIndicator` **从未注册为 Bean**（无任何 @Bean/引用），README 的健康检查章节描述的是不存在的功能；且 health() 仅回显配置，无 README 宣称的 tempDirWritable 等真实检查 | 全仓 grep 仅类自身引用 | AutoConfiguration 增加 @Bean 注册 + 实做临时目录探测 |
| P1-5 | `DataValidator` 手搓 JSR-303 子集（仅 NotNull/Size/Min/Max/Pattern 5 种），未委托标准 `jakarta.validation.Validator`——`@NotBlank/@Email/@Length/自定义约束` **静默放行** | `DataValidator.java:7-11` | 改为委托 Validator（API 已在 pom optional），保留行号上下文包装即可 |
| P1-6 | 快速读引擎无 Sheet 选择能力（全文件无 sheetName/sheetNo 逻辑），`ExcelFacade.read(...).sheet("名")` 在 fast 路径被静默忽略，永远读第一个 sheet | `SuperFastExcelReader.java` grep 为空 | fast 路径先读 workbook.xml 的 sheet 顺序映射再选目标 |
| P1-7 | 零 POI 读路径无 zip bomb 防护：`maxReadFileSizeMB` 只查物理文件大小，压缩条目膨胀比无上限（POI 有 ZipSecureFile 默认压缩比阈值可对标） | 读链路核验 | 参照 POI `ZipSecureFile.setMinInflateRatio` 增加膨胀比上限 |
| P1-8 | XLSX 规范覆盖缺口：rich text (`<r>` 多 run) 仅取首段、SST phonetic 计数干扰、1904 日期窗口字段存在但配置不绑定（README 已自曝）、写路径 XML 转义在部分 `<v>` 路径不完整 | 读/写链路代理逐行核验 | 以 EasyExcel 兼容用例矩阵为清单逐项补齐 |

---

## 四、性能维度

1. **README 性能数据不可信**（自认未做 JMH 基线）："100K 行 ~300ms / ~50MB" 为注释自述口径。修复 P0 后应建 JMH 基准（README 路线图已列，未落地），并与 FastExcel 同机对比——若零 POI 路径修完正确性后不再显著快于 FastExcel（其 SAX + POI 组件同样流式），自研引擎的存在价值需要重估。
2. `SheetXmlReader.readAllBytesDirect`（`:184`）整块读入内存的路径与"大文件不 OOM"承诺冲突（触发条件见代理报告），建议统一临时文件管道。
3. MethodHandle 字段访问（README 实测 6x 提升口径存疑）：MethodHandle 在首次链接后与反射差距有限（反射自 JDK17 起已高度优化），`ASMFieldAccessor` 命名与实现（纯 MethodHandle）名实不符，类名/README 需纠偏或删除。
4. 正向项：`ConverterChain`（CopyOnWrite + priority 排序）、`StyleManager` LRU 复用、`PrecomputedColumnProperties` 列属性预计算、DateTimeFormatter 缓存设计合理，POI 兼容路径（SXSSF 按量选型）无硬伤。

---

## 五、体验维度（开发者 DX）

1. **三套导出入口职责重叠**：`ExcelTemplate` / `ExcelWebSupport` / `ExcelExportHelper` 各一套 API，业务侧实际三者都在混用（system 用 Helper+Facade、literule-web 用 WebSupport、userinfo 用 Facade+Helper）。建议收敛为一个门面 + 一个 Web 下载方法。
2. 对标 pig4cloud starter 的 `@ResponseExcel`/`@RequestExcel` 注解式导入导出是当下大厂流行的 DX 形态，可作为 ydsz-common-web 层的增强方向（不强求）。
3. `doReadAll` 前无行数熔断：userinfo 导入限制 1000 行，但先全量 parse 再拒绝（`UserExcelServiceImpl:78-84`），恶意 10 万行文件会被完整解析。建议在读元数据暴露 `maxRows` 并在 fast 路径生效后，推动业务接入（字段已存在 `metadata.maxRows`，`ExcelReader:488-491`）。
4. i18n：`messages.properties` 40 键 vs zh_CN/en_US 38 键不一致；异常消息未统一走 MessageUtils（依赖 optional，断裂时回退逻辑需核验）。

---

## 六、"过度设计"维度（修订：工具库评判标准）

> **评判标准勘误（2026-09-01 修订）**：L1 工具模块的 API 面天然领先于消费方——扩展点、预留配置、路线图设施的存在本身就是库的形态，**"业务零引用"不构成过度设计的证据**（FastExcel 的 WriteHandler 生态同样只有极少数消费方使用）。工具库的正确评判维度是：① 功能正确性（实现是否有缺陷）② 库内一致性（同库对同一语义是否行为统一）③ 生态重复度（重复造成熟轮子且无增益）④ 完成度与文档诚实度（半成品是否如实标注）⑤ 维护成本。本节按此标准重新归因：以下各项的问题均**不是**"没人用"，而是各自存在正确性/一致性/命名层面的具体缺陷。

| 项 | 真实问题（按修订标准归因） | 处置建议 |
|---|---|---|
| `ReadHandler`/`WriteHandler`/`WriteLifecycleHandler` 回调 | 作为库扩展点保留是合理的；真实问题是 **WriteHandler.applyDataValidation 存在功能缺陷**（无视 validationType 一律 createFormulaListConstraint）与 **fast 写路径不触发任何回调**（库内两条路径行为不一致） | 保留接口；修复 applyDataValidation 缺陷；文档标注 fast 路径的回调限制直至补齐 |
| Tabular 统一 API | 接口与 DefaultAnnotationRowMapper 保留（路线图设施）；真实问题是 **DefaultAnnotationRowMapper 有正确性缺陷**（忽略 @ExcelProperty.dateFormat、不可解析值兜底为 epoch 毫秒静默产出错误数据）及 javadoc 示例引用不存在的构造器 | 保留并修复上述缺陷；未完成的 CSV Reader/Writer 维持路线图冻结状态，需求出现时优先评估成熟组件（Commons CSV）而非自研 |
| `ExcelWriter.writeBatch` | 公共 API 保留合理；真实问题是它**绕过 sanitize/样式/回调**，与主路径行为不一致 | 保留 API；对齐注入防护行为 |
| `ASMFieldAccessor` | 能力本身有价值；真实问题是**命名与实现不符**（实为 MethodHandle，无字节码生成），对库使用者构成误导 | 重命名或以 Javadoc 显著澄清；README 已部分承认但代码层未标注 |
| 三套缓存（ClassMetadataCache/ReflectCache/LRUCache） | L1 纯净性约束下自研合理；仅有职责重叠的合并空间 | 维持；可迭代合并重叠职责 |
| `use1904Windowing`/`validationMode`/`maxReadCacheSize` | 预留配置本身合理；原真实问题为**接线未完成**（ExcelProperties 未绑定，配置了不生效） | ✅ 已随 P2-12 补绑定（2026-09-01） |
| 双引擎架构本身 | 归因不变（与引用数无关）：正确性缺陷集中于零 POI 路径 + 与成熟生态的重复建设需以 JMH 数据论证价值 | 见架构决策建议 |

---

## 七、架构决策建议（战略层，最优先讨论项）

**决策约束（2026-09-01 更新）**：本项目为公司内网项目，云顶编码规范明确不允许引入竞品（EasyExcel/FastExcel/Fesod 均不可用），**只能自研**。原三档方案（适配层 / 双引擎 / 仅止血）中的方案 A（FastExcel 适配层路线）已被此约束否决，方案 C（仅止血）已随 P0/P1 落地自然超越。**路线锁定：方案 B——保留 SuperFastExcelReader/Writer 双引擎，以能力深度完善为目标持续演进。**

方案 B 的落地框架（按优先级）：

1. **兼容性水位自建**：以 EasyExcel 公开文档行为语义为**纸面基线**（不引入依赖，仅作规格参照），自建 round-trip 兼容用例矩阵——读写 × 格式（XLSX/CSV）× 类型 × 边界，预计 200+ 用例，作为 fast 引擎开启前置门禁。
2. **XLSX 规范覆盖缺口补齐**（P1-8 遗留）：rich text 多 run 解析（现仅取首 run）、SST phonetic 文本、fast 读引擎数值型日期单元格识别（`fastNumericDateCellIsKnownLimitation` 存档的已知限制）、1904 窗口（接线已完成，需用例覆盖）。
3. **fast 写路径能力对齐**：`SuperFastExcelWriter` 当前不触发 WriteHandler/WriteLifecycleHandler、不应用样式注解——需实做回调链或以门面层显式声明不支持项的编译期断言，消除静默能力差异。
4. **性能论证**：JMH 自研双引擎（fast vs POI 路径）同机基准，替代原"与 FastExcel 同机对比"口径；数据用于回答"零 POI 路径何时值得开启"。
5. **演进纪律**：fast 默认值保持 `false`，直至兼容用例矩阵全绿 + JMH 数据支撑，再评估翻转默认值——与开源生态（EasyExcel 6 年、900+ 单测的兼容性水位）对标的是**用例覆盖度**而非引入其代码。

---

## 八、落地路线图（P0→P2）

**P0（本周，1 个迭代内）** — ✅ **已于 2026-09-01 全部完成并测试验证（17/17 用例通过，BUILD SUCCESS）**
1. ✅ `useFastReader`/`useFastWriter` 默认值 → `false`（ExcelConfig/ExcelProperties/README/metadata JSON 四处同步）
2. ✅ 修 P0-1（fast 分支增加 hasFileSource 守卫，InputStream 回退 POI 路径）与 P0-3（Double/Float/BigDecimal 走 double 分支，含 NaN/Infinity 降级）
3. ✅ 修 P0-4（@Target 加 FIELD）、P0-5（读侧 headerRowIndex + fast 侧 headRowNumber-1；**写侧补全**：POI 写引擎 ExcelWriter.doWrite 原把 headRowNumber 当 0-based 索引致默认导出首行空白、写读 round-trip 断裂，已统一为 1-based 并修正两处 javadoc）
4. ✅ 修 P0-2（SuperFastExcelReader 新增 headerNames 收集 + metadataFactory 惰性构建；HeaderAnalyzer 新增 analyzeClassMetadataFromNames 镜像方法；SheetXmlReader 表头行收集 SST 解析列名、parseDataCell 改用 resolveMetadata）
5. ✅ 建最小测试集 `ExcelRoundTripTest`（17 用例）：POI 读 7 + fast 读 6 + 双引擎写 round-trip 3 + fast 引擎数值日期已知限制存档 1；pom 补 spring-boot-starter-test（与兄弟模块惯例一致）

**测试实证暴露的新事实**（对照组用例发现）：POI 写引擎与读引擎的 headRowNumber 语义错位为**双向断裂**——旧写引擎默认导出首行空白，且自有文件读回必抛"Excel文件为空或没有表头行"；修复后写读 round-trip 全通。唯一业务写调用方 literule `DecisionTableExcelExporter.headRowNumber(0)` 经 clamp 兼容，行为不变。

**P1（1-2 个迭代）** — ✅ 全部 4 项落地任务（第 5-8 项）已于 2026-09-01 完成（模块测试 42/42 通过，BUILD SUCCESS）
5. ✅ 公式注入防护对齐 OWASP 前缀集 + XLSX/CSV 分路径策略（`FormulaInjectionGuard` 重写：六前缀 `= + - @ \t \r`；XLSX 用前导空格阻断"另存 CSV 二次求值"链、CSV 保留撇号转义；`ValueFormatter`/`SuperFastExcelWriter` 共 4 处调用点切换；`FormulaInjectionGuardTest` 6 用例）
6. ✅ Spring 三入口配置接线统一 + ExcelHealthIndicator 注册（健康检查真实探测已随 P2-12 完成：`ExcelHealthIndicator` 以 `File.createTempFile` 写探针内容 + 长度校验 + `Files.deleteIfExists` 真实探测临时目录可写性，`tempDirWritable`/`tempDir` 详情随 Health 上报，探测失败报 `Health.down`）
   - 接线修复：`ExcelTemplate` read/write 三方法、`ExcelExportHelper`（新增构造注入）均接线 ExcelConfig；`ExcelAutoConfiguration` 为 ExportHelper 传入 config；新增 `ExcelReader.config()` 流式方法（与 `ExcelWriter.config()` 对称，读路径此前无配置入口）；ExcelHealthIndicator 经嵌套配置类注册（隔离 spring-boot-health optional 依赖的类引用，规避 NoClassDefFoundError）
   - 修复中暴露的三个**更深断点**（均已修复）：① `ExcelWriter` 的 `ValueFormatter` 构造时固化 defaults，链式 `config()` 变更后不重建 → 统一 `rebuildValueFormatter()`；② `UltraFastCellWriter`（typed POI 写**主路径**）完全不接收 ExcelConfig，公式注入消毒在该路径失效（P1-1 修复时漏网）→ 接入 config；③ `finish()` 非幂等，`doWrite`（单 Sheet 自动 finish）后显式 `finish()` 触发已关闭 workbook 二次写入（POI: Cannot write data, document seems to have been closed already）→ 以既有 `writeCompleted` 标志幂等化
   - 新增 `ExcelSpringWiringTest` 5 用例：以"配置属性 → 输出行为差异"为证（default-date-format 改变日期列输出、formula-injection-protection=false 关闭消毒、动态导出表头仍在首行、HealthIndicator Bean 注册且 UP）
7. ✅ DataValidator 委托标准 Validator（双路径：classpath 存在 Jakarta Bean Validation 实现时委托标准 `Validator`——全约束覆盖含 `@NotBlank`/`@Email`/自定义约束，此前静默放行；实现缺席时回退内置五规则，保持 L1 零强制依赖。错误提示字段名优先映射 `@ExcelProperty.value()`。测试域引入 spring-boot-starter-validation，`DataValidatorTest` 7 用例——核心证据为 @NotBlank 空串与 @Email 格式两类"旧路径必然漏放"的约束被实际拦截）
8. ✅ fast 路径 Sheet 选择 + zip bomb 膨胀比防护（`SuperFastExcelReader` 重写 + `ExcelReader` fast 分支接线）
   - **Sheet 选择**：`read(Path)` 改经 `ZipFile` 随机访问，解析 `xl/workbook.xml`（sheet 声明顺序）与 `xl/_rels/workbook.xml.rels`（rId→Target 映射）定位目标 sheet。sheetName 精确匹配（未命中抛"Sheet不存在"，与 POI 路径语义对齐）；sheetIndex 0-based 按声明顺序（越界回落第一个，对齐 POI getSheet）；workbook.xml/rels 缺失时回落第一个 sheet entry（兼容非标准生成器）。此前 fast 引擎固定读 zip 第一个 sheet entry，`ExcelFacade.read(...).sheet("名")` 在 fast 路径被静默忽略
   - **zip bomb 防护**：所有解压读取（sheet XML / sharedStrings / InputStream 落盘）经 `BoundedInputStream` 限流——解压后累计超过 `maxReadFileSizeMB` 即中断抛异常。以"解压后绝对量上限"阻断，与 POI ZipSecureFile 膨胀比（MIN_INFLATE_RATIO）防护等价且更直观；此前依赖 `ZipEntry.getSize()`（zip 头可伪造、常为 -1）事后检查，临时文件分支先写满磁盘再校验，防护形同虚设
   - **config 接线**：`ExcelReader` fast 分支传递 `ExcelConfig` / `sheetName` / `sheetIndex` / `skipEmptyRows`（默认值同步对齐 POI 路径语义），`maxReadFileSizeMB` 等配置此前对 fast 引擎无效（内部恒用 defaults()）
   - `read(InputStream)` 改为 bounded 落临时文件后委托 `read(Path)`（InputStream 无法随机访问）
   - 新增 `FastReaderSheetSelectionAndZipBombTest` 7 用例：双 Sheet 文件按 sheetName/sheetIndex 选择、默认第一个、按名未命中即失败、越界回落；zip bomb 双入口（sheet entry / SST entry 解压后 50MB + maxReadFileSizeMB=1）均被 BoundedInputStream 拦截——炸弹文件压缩后 KB 级，绕过基于压缩体积的预检查，验证的是解压阶段限流
9. ✅ 架构决策已锁定（2026-09-01）：内网项目禁引竞品，方案 B（自研双引擎深度完善）为唯一路线——见第七节落地框架

**P2（随迭代）**
10. 三套导出入口收敛；@ResponseExcel 式注解增强（可选）
11. JMH 自研双引擎（fast vs POI 路径）同机基准——为"零 POI 路径何时值得开启"提供数据（原"与 FastExcel 同机对比"口径因竞品禁令废止）
12. ✅ 库内一致性修复（2026-09-01 完成，模块测试 49/49 通过，BUILD SUCCESS）：
    - WriteHandler.applyDataValidation 重写：显式列表约束走 `createExplicitListConstraint`（原 `createNumericConstraint` 误用于 LIST 类型生成的约束无效）、数值约束走 `createNumericConstraint`、日期约束走 `createDateConstraint`（固定 `yyyy-MM-dd`）；error 提示经 `createErrorBox(title, text)` 正确接线（POI `DataValidation` 无 setErrorTitle/setErrorText 方法——此前调用的是不存在的方法，编译必败，证明该路径从未被编译验证过）；列表值支持逗号分隔自动拆分
    - writeBatch 防护对齐：`ensureInitializedForBatch` 惰性初始化（writeHead 只在 doWrite 内触发，writeBatch 单独调用时 sheet/ultraFastCellWriter 为 null 必 NPE——现初始化链路完整，表头 + 注入消毒防护生效）；javadoc 标注与 doWrite 的行为差异
    - DefaultAnnotationRowMapper 日期缺陷修复：dateFormats 并行数组 + FORMATTER_CACHE（ConcurrentHashMap）+ formatterFor 回退，读写双向尊重 `@ExcelProperty.dateFormat`；不可解析日期**抛 IllegalArgumentException**（原静默按 epoch 毫秒转换产生错值），纯数字值仍按 epoch 毫秒兼容
    - fast 写路径回调限制标注（SuperFastExcelWriter 类 javadoc：不触发 WriteHandler/WriteLifecycleHandler、不应用样式注解）
    - ASMFieldAccessor 命名澄清（类名 ASM 为历史遗留，实为 MethodHandle 访问器，从不生成字节码）
    - ExcelConfig 预留配置补绑定：`use1904Windowing`/`validationMode`/`maxReadCacheSize` 三字段经 ExcelProperties → ExcelAutoConfiguration.toExcelConfig 接线，配置即生效（原为"配置了不生效"的死配置）
    - 新增 `ExcelP2ConsistencyTest` 6 用例回归；`ExcelSpringWiringTest` 补 reservedPropertiesBindToExcelConfigBean（三新配置绑定实证）
13. README 纠偏（健康检查、全局单例、性能口径、防护前缀四处与事实不符处）——待做

**深度完善主线（方案 B 落地，2026-09-01 启动）**
14. ✅ XLSX 规范覆盖缺口（2026-09-01 完成，57/57 全绿）：新增 `StylesReader`（styles.xml numFmt 判定，内建 14-22/45-47 + 自定义 formatCode，对齐 POI isADateFormat）；数值型日期识别（`<c s="N">` 样式索引 → `DateUtil.getJavaDate(serial, use1904Windowing)`，`SimpleCell` 装载 dateValue，NUMERIC 分支短路）——`fastNumericDateCellIsKnownLimitation` 存档限制解除（存档用例升级正向断言）；SST rich text 多 run 拼接（此前只取首 run，POI RichTextString/分段着色丢内容）+ phonetic（`<rPh>`）过滤；inlineStr 富文本多 run 复用 `extractRunsText`；lazy 模式自闭合 `<si/>` 索引占位防错位。1904 窗口接入日期转换并实证（新增 `FastReaderXlsxSpecComplianceTest` 5 用例）
15. ✅ fast 写路径显式降级（2026-09-01 完成）：`ExcelWriter.doWrite` fast 分支检测到 `WriteLifecycleHandler` 回调或 `@ExcelStyle`/`@ContentStyle` 样式注解时自动回落 POI 路径——回调全触发、样式全生效，消除"配置了但静默失效"的能力差异（此前仅 javadoc 标注）；plain DTO 仍走 fast（`FastWriterGracefulFallbackTest` 3 用例：回调计数实证、POI 直读样式生效、fast 回归）
16. EasyExcel 兼容用例矩阵自建（纸面基线参照，round-trip 读写 × 格式 × 类型 × 边界，200+ 用例，fast 默认开启前置门禁）——待启动

---

## 附：核验方法与证据索引

- 编译验证：`mvn -o -pl ydsz-common/ydsz-common-excel compile` → BUILD SUCCESS
- 业务消费方（6 处）：common-docs `ExcelDocumentParser`、common-web `ExcelMvcExceptionHandler`、literule `DecisionTableExcelExporter` + `RuleDecisionTableController`、system `Config/DictItem/Variable ExcelServiceImpl`、userinfo `UserExcelServiceImpl`
- 关键证据行号：`ExcelReader.java:477/479-481/483/657/667`、`SuperFastExcelWriter.java:418`、`ExcelIgnore.java:49`、`SheetXmlReader.java:380`、`FormulaInjectionGuard.java:65/105-113`、`ExcelTemplate.java:54-81`、`DataValidator.java:7-11`、`ExcelAutoConfiguration.java`（无 HealthIndicator Bean）
- 竞品事实：EasyExcel 归档（2025-09-04）、FastExcel 1.3.0（2025-08-23）、Apache Fesod 孵化（2025-09-17）
