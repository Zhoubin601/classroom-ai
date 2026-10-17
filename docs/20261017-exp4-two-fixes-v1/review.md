# 两项缺陷修复独立审查

日期：2026-10-17（Asia/Shanghai）。审查范围：US-07 上传策略同步及 BOPPPS／四维评分展示口径；沿用同日已经只读核验的实验一正式原件摘录，无需重新打开 DOCX。

## 本代理完成的修改

仅修改 `frontend/src/views/SupervisorDeskView.vue`：

- 顶部说明“BOPPPS随堂打分”改为“教学评价四维打分”。
- 评分标题改为“教学评价四维打分项（配置权重合计100分）”。
- 通用意见字段改为“教学改进建议”，保留下一行关于导入、目标、前测、参与式学习、后测、总结的可选建议说明。
- 修正对应注释，去除“每项0～25分”的固定假设，说明按所选模板配置权重。

没有修改评分数据、真正的 BOPPPS 环节／视频标签、布局或其他共享文件。使用 `ui-ux-pro-max` 本地技能的现有界面缺陷审查方式，聚焦标签准确性、可恢复错误及已有交互契约，不引入新视觉方向。

## 实现前发现的回归注意事项

1. `scripts/tests/resource-versions-browser.cjs` 当前兜底返回 `data=[]`；该脚本实际选择并上传新版文件。新增 `/api/v1/resources/upload-policy` 后必须提供合法策略夹具，否则严格策略校验会阻止其原有上传流程。不能为了兼容旧夹具而在产品代码默默回退固定100MiB。
2. `scripts/tests/ui-interaction-browser.cjs`、`us01-us02-browser.cjs`、`login-start-browser.cjs`、`login-probe-browser.cjs` 同样拦截全部业务API且对未知路由返回空数组／对象。按新增请求的触发时机，需补齐相应策略夹具；不是业务接口真实失败。
3. 当前教师隐藏 file input 的旧 `accept` 字符串和现有定位器一致。策略改为动态后，新增真实浏览器应使用稳定的模态框／aria标签定位，避免再次硬编码旧扩展名顺序。
4. 策略失败应有明确重试入口，阻止上传且保留用户已填章节、资源名与历史上下文；只读历史、切换当前版本和预览不应因此整体不可用。
5. 选择文件与最终提交均需校验当前生效的策略，避免旧文件在策略刷新后仍超限或类型已被禁用。失败重试、慢返回、卸载后返回不得覆盖新组件状态。
6. 主上传与版本上传应共用允许类型／字节上限／文案；空白、前导点、大小写和重复扩展名的规范化须与后端一致。服务端仍应进行最终鉴权与校验。

## 最终源码审查（实现落盘后）

审查对象：`ResourceUploadPolicy.java`、`CourseResourceController.java`、`CourseResourceServiceImpl.java`、两个新增后端测试类；`resourceUploadPolicy.mjs`、`useResourceUploadPolicy.ts`、API／类型定义、`TeacherDeskView.vue`、`ResourceVersionsPanel.vue` 及新增前端规则测试。并对照本代理文案 diff 复核范围。`git diff --check` 通过，仅现有共享 Markdown 文件显示 CRLF 转换提示。

### 已解决的实现前注意事项

- 服务端 `GET /api/v1/resources/upload-policy` 只读返回 `{maxFileBytes, allowedExtensions}`，要求真实认证；未要求无关的课程写权限，未扩大课程／文件权限。原有 `JwtAuthenticationFilter` 仍禁止 ADMIN 进入普通资源业务路由。
- API 宣告、Controller 实际文件上传、Service 新资源／系列新版保存共用 `ResourceUploadPolicy`；有效字节上限取资源设置、Servlet 单文件上限、Servlet 请求体上限中非负限制的最小值。请求体头部与其他字段占用仍由 Servlet 最终校验，不能把策略上限解释成整个 multipart 请求必然成功的保证。
- 扩展名配置与前端返回值解析共同执行 trim、大小写归一、单个前导点和去重；文件名扩展名校验已根据审查建议补 trim；选取时与最终提交时都校验同一策略。没有产品代码中的固定100MiB兜底。
- 两个前端入口共用 composable 与纯校验 helper，动态显示允许类型、精确字节／MiB／KiB上限及 `accept`。教师初次上传与系列新版上传均受策略门禁，服务端仍是最终校验者。
- `resource-versions-browser.cjs` 已补新只读接口的合法策略夹具，保留原有7组交互断言。其他全API模拟脚本的未知路由不会访问业务服务；若执行策略专项，需要使用新契约夹具，不能把旧空响应当成服务端策略失败。

### 异步、重试与上下文检查

- 策略加载使用 `AbortController`、递增 request 及 disposed 防护。重载先失效旧请求；旧响应、旧错误和卸载后的响应不能恢复过时策略。失败清空策略且提供“重新加载上传规则”，不允许带固定上限继续上传。
- 两个入口的 `watch(policy)` 默认是 Vue 的 pre-flush。`reload()` 同一异步继续段内先赋 policy、再在 finally 清除 loading；批处理后的 watcher 看到 ready 为真，不会因为瞬时 loading 而误清有效文件。reload 开始置空 policy 时 watcher 不清理已选文件，加载失败也不会主动重置章节／课程／系列上下文。成功返回更严格策略时才清理不合规文件。
- 主入口对无效选文件清空文件引用和关联的文件名称／大小，不改当前课程；提交前捕获课程与已选文件，现有页面过期上下文保护仍在。系列入口沿用当前已加载历史的课程、系列与 revision，不以策略请求覆盖历史上下文。
- 策略失败只禁止上传控件；版本历史读取、水印预览与设为当前版仍由原 `contextReady` 控制，不被上传策略整体阻断。
- 主上传超时从默认值提升到120秒，与已有系列新版上传一致，适用于已获授权的大文件上传；未改评分、历史版本、共享授权或视频行为。

源码终审结论：未发现需要阻断本轮构建／实测的重要问题。BOPPPS评分文案修正没有改变真正六环节标签或视频字段，上传策略改变没有绕过服务端授权与大小／类型校验。后端策略配置非法时前端失败关闭；不能把配置为空／非法自动视作开放任意类型。

## 验证证据的边界

本代理执行的是源码／diff审查，没有自行运行Java或浏览器。已读取后端执行代理生成的 `backend/test-results.json`：4类30项、失败／错误／跳过均0，包含新增策略配置和真实安全链下的 MockMvc 合同验证；它不是实际MySQL或完整Chrome上传证明。最终真实浏览器、失败重试、慢请求和150MiB／1024字节场景，以主代理及独立浏览器代理的本轮结果为准。
