# 跨模块联调测试（scripts/tests/）

- `test_http_smoke.py`：需要已启动的 Java 后端；创建独立合成记录并清理。
- `browser_smoke.cjs`：需要前后端、Playwright 和浏览器；检查页面与摄像头接口参数校验。
- `test_backend_api.py`：保留的旧手动调试脚本，会读取已有特征库并写入固定测试学号，**不纳入自动回归**。

各模块自己的测试分别放在：

- `frontend/tests/`：Node 单元测试。
- `backend/src/test/`：Java 测试。
- `vision/tests/`：Python 无硬件测试。
- `vision/diagnostics/`：手动硬件实验。

统一自动化入口为根目录下的 `scripts/run-tests.ps1`。

