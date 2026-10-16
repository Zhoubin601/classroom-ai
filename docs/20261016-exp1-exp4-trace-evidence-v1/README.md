# 2026-10-16 功能追踪验证证据

适用源码：exp4 / f5a8bcc。日期：2026-10-16，Asia/Shanghai。

- `executed-junit-tests.json`：284项后端测试的方法级执行登记。
- `verified-catalog.json`：21条故事、源码定位与关联测试。
- `original-table5.txt`、`original-table6.txt`、`original-table7.txt`、`original-table16.txt`：既有正式规划只读摘录。
- `sprint2-browser/results.json`：Sprint 2正式入口七套结果；子目录保留相应专项证据。
- `browser-final/sprint3-browser/browser-results.json`：Sprint 3真实业务八组结果。
- `browser-final/supplement/results.json`：补充页面流程与统计追溯。
- `report-qa.json`、`export-structure-check.json`：图文报告及导出检查记录。

源码基线及文件指纹见`baseline.json`；对应图文总览位于`output/功能汇总/`。报告和旧清单中的绝对路径表示当时的本机位置。

发布范围：运行日志、原始JUnit XML和早期重复/候选目录保留本地，不纳入本次Git提交。原始XML含运行环境与框架生成的临时认证输出；公开登记保留测试类、方法、耗时及结果。报告中指向`backend-full-xml/`及运行日志的原始证据路径需要在原验证机器查阅，不能假定克隆后具备这些文件。

本次发布没有重新执行功能测试，不把报告的页面QA等同逐项人工签收；当前图文汇总的Markdown图片路径问题见`docs/20261016-feature-summary-review-v1.md`。
