# 前端交互重构实现与验收索引

日期：2026-10-12。方向与最小只读接口补充已获用户授权。使用 `ui-ux-pro-max` 本地 Skill；未引入外部素材。

## 公共交互契约

- `navigation.mjs`：统一串行离开检查，支持多编辑器、保存结果校验、保存锁、认证代次失效和用户/角色/页面内存偏好。
- `context.ts`：提供编辑快照、稳定键序比较、保存基线、偏好、通知、确认及没有草稿接口的取消/放弃保护。
- `FeatureSelector`：内容容器查询，88px最小高度，手动激活的tab/tabpanel、实际列数方向键、折叠不切内容；请求期间禁用入口。
- `UiModal`：Teleport、视口内滚动、初始焦点、Tab循环、Esc、背景inert与关闭后焦点恢复。
- `useRadar`：等待挂载与有效容器宽度，容器尺寸监听，卸载释放，提供图形文字说明；两个页面分别保持25/100分口径。
- 认证失败统一撤销视图、清空偏好、取消旧请求。编辑与偏好不写入持久化存储。

## 证据接口边界

原接口可读列表与可引用证据不是完全等价：微格列表可能包含未验证资产，资源检索仅列当前版本，反馈引用可能只有不可访问占位。补充：

`GET /api/v1/supervisions/{evaluationId}/evidence-candidates?type=EVALUATION|RESOURCE_VERSION|MICRO_SLICE&keyword=...`

返回合法候选的`type/id/label`及必要微格区间。先检查父评价可见性与反馈开放规则，再限定同课程，逐项复用已有引用校验。历史资源保留固定ID；不可访问占位不返回原ID，不增加写权限或修改数据库结构。

选择器区分空结果与失败，失败保留选择；失效占位必须明确移除/替换后保存。请求体仍为既有`evidenceRefs`类型、ID与可选区间。

## 本次验收

- 教师试点先通过，再推广到主任、督导与统计/支持页面。
- `interaction/results.json`：15组模拟响应的真实浏览器交互验收，不能替代真实业务回归。覆盖四角色、四断点、横屏、200%缩放、并发编辑、两个雷达图、失效引用及认证撤销。
- `business/browser-results.json`：12组真实Chromium→Vite代理→Spring→独立MySQL/Redis流程，使用合成账号、课程、专业、评价、PDF与视频；当前版本重新验证，未使用历史结果替代。
- 前端18项测试与构建通过。后端常规登记284项：206通过、78条件跳过；专项47项（16边界＋31真实MySQL）全部通过，与常规有重合，不相加宣称总数。
- 仍有47条其他条件用例未在本轮专项执行；本次不宣称全项目所有条件测试已运行。真实屏幕阅读器、真人摄像头识别与真实教学内容不属于自动验收结论。

证据目录：`docs/20261012-ui-redesign-evidence-v1/`。早期失败日志为调试过程；以final日志及JSON结果为验收依据。正式交付仅在output中生成验收Markdown，不放测试文件。

## 重跑方式

```powershell
npm.cmd --prefix frontend test
npm.cmd --prefix frontend run build
# 与本次相同的合成数据、独立临时MySQL/Redis和浏览器流程
.\scripts\run-ui-redesign-tests.ps1
```

交互浏览器脚本需要一个可访问的前端地址，使用`UI_URL`指定；Playwright和Chromium复用本机既有安装。测试runner默认清理自己创建的服务和容器，`-KeepServices`仅供明确选择保留隔离环境时使用。

未提交、推送或部署。原有stop_project.ps1、数据治理文件、历史证据与交付物保持原状。
