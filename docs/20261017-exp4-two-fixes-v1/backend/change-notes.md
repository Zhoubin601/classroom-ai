# US-07 后端上传策略修复

新增已登录读取接口 `GET /api/v1/resources/upload-policy`，返回：

```json
{"code":200,"data":{"maxFileBytes":104857600,"allowedExtensions":["ppt","pptx","doc","docx","pdf"]}}
```

`ResourceUploadPolicy` 统一读取资源配置以及 Spring `MultipartProperties`。有效文件字节上限取资源配置、非负 multipart 单文件上限、非负 multipart 请求总量上限的最小值。请求总量还包含 multipart 头与其他字段，仍由 Servlet 最终校验。默认配置的总量 220 MiB 大于单文件 200 MiB；资源默认 100 MiB，以及 150 MiB、1024 字节配置均可精确表达。

允许扩展名统一去首尾空格、转小写、移除一个可选前导点并去重；接口、上传控制器与业务服务使用同一策略。原资源上传及同系列新版上传共用验证路径。业务服务从实际存储文件 URL 的扩展名校验类型，避免 DOC/PPT 持久化类型归一化后误拒绝合法原扩展名；已有版本的元数据编辑继续使用不可变文件元信息。

策略接口先调用 `requireCurrentUser()`，不要求具体课程写权限，沿用现有 JWT/SecurityConfig：匿名 401、教师/主任/督导可读、ADMIN 通用业务入口仍为 403。控制器与 service 使用非 final 可选注入字段，保留原构造签名和直接实例化测试的默认策略。

定向验证共 4 类 30 项，失败 0、错误 0、跳过 0。新增策略测试 8 项、HTTP 测试 2 项，覆盖默认值、150 MiB 调大、1024 字节调小、扩展名规范化、multipart 单文件及请求上限、无上限标识、业务服务与主/新版上传的一致验证、鉴权。另执行已有资源 service 和 Regression 测试以验证构造兼容。

测试日志：`targeted-tests.log`；XML：`targeted-xml/`；汇总：`test-results.json`。日志与 XML 已脱敏。这里只执行 compile/test，JAR 由主代理在前后端修改完成后统一生成。
