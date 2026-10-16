# 软件验收就绪核对

日期：2026-10-16（Asia/Shanghai）。检查对象：本地 exp4，HEAD f5a8bccf6fe19155263bef87599419a1f5658020。

结论：原规划 US-01～US-21 具备开展软件功能验收的条件。当前机器未运行演示服务，需恢复环境并确认运行版本后进行现场操作；不能表述为自然人已经正式签收。

## 本次直接核验

- 逐文件重算既有 baseline.json 的 298 个 SHA256，全部一致，包括业务源码、测试和受保护原始文件。当前业务目录无 Git 未提交差异。
- 重新汇总既有 backend-full-xml 中 39 份 JUnit XML：284 项，失败 0、错误 0、跳过 0。
- 读取同日实测日志：前端 23 项通过及生产构建成功；Sprint 2 正式入口七套通过；Sprint 1 真实登录三角色五故事通过；Sprint 3 真实业务八组通过，页面错误为空。
- Docker 查询失败，提示 dockerDesktopLinuxEngine 管道不存在；5173、8080、8088 未检测到监听。不能据此确定容器目前的具体状态。
- 本次未重新执行上述完整测试，结论基于同日已有实测证据及本次源码指纹核验；未启动、部署、修复或写入业务数据。

## 验收时需要复核

1. 恢复 Docker 与前后端，确认运行产物对应本次验证的源码；完成登录及关键流程现场检查。
2. BOPPPS 徽标与四维评分的展示歧义仍在 TeacherDeskView.vue 第 345、397 行；页面固定 100MB 提示与服务端可配置策略也需说明。现有证据未显示核心业务因此阻断，可修正文案或在验收中记录接受。
3. 自然人正式批准、真实评审/回顾资料未提供，沿用已确认后续安排，不冒称完成。
4. 真人摄像头、多人识别、GPU/目标设备效果与生产容量不在这 21 条软件故事的已验证结论内；如承诺交付这些能力，应另做实测。

## 资料与文件

原始规划来源采用同日已只读摘录的《第二组_项目规划方案.docx》表 5/6/7 及完成定义，路径见 source-notes.md；本次未重新打开 DOCX。项目 raw/ 仅 README.md，其他业务原始资料未提供。测试使用已有合成数据，不代表真实教学结论。未使用外部联网资料。

直接证据：docs/20261016-exp1-exp4-trace-evidence-v1/ 的 baseline.json、backend-full-xml/、frontend-tests.log、frontend-build.log、legacy-browser/us0203-real-browser.log、sprint2-browser/results.json、browser-final/sprint3-browser/browser-results.json。

本次新增本检查记录，并追加 docs/source-notes.md、docs/analysis-notes.md、docs/questions.md。没有修改 raw/、业务源码、memory-bank/ 或 output/；未生成正式签收文件。
