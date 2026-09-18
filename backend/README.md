# 具身机器人智能课堂分析系统 - Java 后端服务 (Classroom Backend)

本项目为智能课堂视觉分析系统的云端业务中台，基于 **Spring Boot 3 (Java 21) + Redis 7 + MySQL 8 + Docker** 构建，提供人脸高维特征持久化、毫秒级特征缓存比对、学生档案管理，并**专为前端 ECharts 可视化大屏与教师管理控制台预留全套 RESTful API 与跨域支持**。

---

## 一、 系统技术架构与存储设计

```
 ┌────────────────────────────────────────────────────────┐
 │ 1. 具身机器人 / 视觉感知边缘端 (Python + InsightFace)     │
 │  - 提取 512 维人脸特征向量                                │
 │  - 解算 3D 头部姿态 (Pitch/Yaw/Roll) 与抬头率            │
 └────────────────────────┬───────────────────────────────┘
                          │ RESTful API (HTTP)
                          ▼
 ┌────────────────────────────────────────────────────────┐
 │ 2. Java 后端中台 (Spring Boot 3.3.x + Java 21)          │
 │  - 人脸特征服务 (FaceService): 双写持久化与 1:N 检索      │
 │  - 可视化大屏服务 (VisualDashboardService): 时序聚合与看板│
 │  - 学生档案管理 (StudentService): 档案维护与级联清除      │
 │  - 全局跨域过滤器 (CorsConfig): 允许前端无感访问         │
 └──────────────┬───────────────────────────┬─────────────┘
                │                           │
                ▼                           ▼
 ┌───────────────────────────┐ ┌──────────────────────────┐
 │ 3. MySQL 8.0 关系数据库   │ │ 4. Redis 7.x 内存高速缓存 │
 │  - student: 学生基本档案   │ │  - face:feature:{id}: 向量 │
 │  - face_feature: 512维向量 │ │  - face:all_ids: 特征索引集│
 │  - classroom_record: 宏观 │ │  - classroom:trend: 时序点 │
 └───────────────────────────┘ └──────────────────────────┘
                ▲
                │ RESTful API (ECharts JSON)
 ┌──────────────┴─────────────────────────────────────────┐
 │ 5. 前端可视化大屏与控制台 (Vue 3 + TypeScript + ECharts) │
 │  - 实时大屏宏观看板 (/api/visual/overview)              │
 │  - 抬头率波形时序折线图 (/api/visual/trend)              │
 │  - 学生状态卡片列表 (/api/visual/students/status)        │
 └────────────────────────────────────────────────────────┘
```

---

## 二、 数据库与缓存设计规范

### 1. MySQL 核心数据表 (`classroom_ai`)
* **`student`（学生档案表）**：
  * `student_id` (VARCHAR(64), UNIQUE): 学号
  * `name` (VARCHAR(64)): 姓名
  * `gender` (VARCHAR(16)): 性别
  * `class_name` (VARCHAR(64)): 班级
  * `avatar_url` (VARCHAR(255)): 头像/人脸快照路径
* **`face_feature`（人脸特征向量表）**：
  * `student_id` (VARCHAR(64), INDEX): 关联学号
  * `feature_dim` (INT): 特征维度（固定为 512）
  * `feature_vector` (MEDIUMTEXT): 512 维浮点数 JSON 数组 `[0.0123, -0.0456, ...]`
  * `image_path` (VARCHAR(255)): 人脸底库原始图片路径
* **`classroom_record`（课堂时序宏观记录表）**：
  * `session_id`, `course_name`, `class_name`, `total_expected`, `actual_present`, `attendance_rate`, `lookup_rate`, `look_down_count`, `record_time`

### 2. Redis 缓存键设计
| Redis Key | 数据类型 | 作用与生命周期 |
| :--- | :--- | :--- |
| `face:feature:{studentId}` | String (JSON) | 缓存单个学生的特征向量及简要信息，支持毫秒级读取与更新 |
| `face:all_ids` | Set | 所有已录人人脸的学生学号集合，支持快速批量遍历 |
| `classroom:realtime:overview` | String (JSON) | 课堂实时大盘指标缓存（应到、实到、抬头率、预警数） |
| `classroom:realtime:present_ids` | Set | 当前课堂实到学生的学号集合 |
| `classroom:realtime:poses` | Hash | 当前学生姿态字典：`学号 -> "UP" / "DOWN"` |
| `classroom:trend:history` | List (FIFO) | 抬头率时序波形列表（最多保留最近 60 个周期点），供 ECharts 直连 |

---

## 三、 预留前端可视化大屏与后端 RESTful API 规范

所有接口响应体统一采用：
```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": 1726300000000
}
```

### 1. 大屏可视化接口 (`/api/visual`)
* **`GET /api/visual/overview`**：获取大屏实时统计卡片
  * 返回字段：`totalRegistered`（应到）、`currentPresent`（实到）、`currentAbsent`（缺勤）、`attendanceRate`（出勤率%）、`realtimeLookupRate`（实时抬头率%）、`lookdownCount`（低头预警数）、`focusLevel`（专注度等级）、`lastUpdateTime`。
* **`GET /api/visual/trend`**：获取 ECharts 抬头率与专注度折线图数据
  * 返回数组：`[ { "time": "14:20:05", "lookupRate": 92.5, "presentCount": 45 }, ... ]`
* **`GET /api/visual/students/status`**：获取班级学生实时状态网格/卡片
  * 返回数组：`[ { "studentId": "STU001", "name": "张三", "className": "高一(1)班", "present": true, "poseState": "UP", "lastSeenTime": "14:25:30" }, ... ]`
* **`POST /api/visual/report/stream`**：接收 Python 边缘视觉端上报的推断流数据
  * 传参格式：
    ```json
    {
      "sessionId": "SESSION_20260914",
      "courseName": "AI智能课堂",
      "className": "高一(1)班",
      "detectedPersonCount": 42,
      "lookupCount": 38,
      "lookdownCount": 4,
      "lookupRate": 0.905,
      "presentStudentIds": ["STU001", "STU002"],
      "studentPoses": { "STU001": "UP", "STU002": "DOWN" }
    }
    ```

### 2. 人脸特征数据存储接口 (`/api/face`)
* **`POST /api/face/register`**：注册/更新学生人脸数据
  * 接收学生学号、姓名、班级及 512 维特征向量，双写 MySQL 并刷新 Redis。
* **`GET /api/face/all`**：全量拉取特征库
  * 供 Python 视觉端启动时一次性载入本地内存，由本地 GPU 进行零网络开销的高速余弦比对。
* **`POST /api/face/search`**：云端 1:N 人脸检索匹配
  * 接收 512 维向量，在 Redis 内存库中计算余弦相似度并返回匹配度最高的学生档案。
* **`POST /api/face/upload`**：上传人脸原始底库照片
  * 接收 `MultipartFile`，保存至 `runtime/uploads/faces` 并返回静态图片相对访问路径。
* **`DELETE /api/face/{studentId}`**：删除学生人脸特征数据（同步清理 MySQL 与 Redis）。

### 3. 学生档案管理接口 (`/api/student`)
* **`GET /api/student/list`**：获取所有学生档案列表（供前端管理表格展示）。
* **`POST /api/student`**：新增或修改学生基础信息。
* **`DELETE /api/student/{studentId}`**：删除学生档案并级联清理人脸数据。

---

## 四、 快速部署与运行指南

### 1. Docker Compose 一键全量部署（推荐）
确保开发机已安装 Docker 及 Docker Compose，在根目录下执行：
```bash
# 1. 一键启动 MySQL 8.0、Redis 7.2 与 Spring Boot 后端
.\scripts\compose.ps1 up -d

# 2. 查看容器运行状态
.\scripts\compose.ps1 ps

# 3. 检查后端日志
.\scripts\compose.ps1 logs -f classroom-backend
```

### 2. 本地开发与联调运行
如需在本地 IDE（如 IntelliJ IDEA / VS Code）调试后端：
1. 启动本地 MySQL 与 Redis（或仅通过 `.\scripts\compose.ps1 up -d mysql redis` 启动依赖中间件）；
2. 运行 `scripts/deploy/mysql/init/01_schema.sql` 完成数据库与数据表初始化；
3. 进入 `backend` 目录，执行：
   ```bash
   mvn clean spring-boot:run
   ```

### 3. 执行端到端集成测试脚本
在根目录下直接调用现有的 Python 虚拟环境运行测试脚本：
```bash
.venv1\Scripts\python.exe scripts/tests/test_backend_api.py
```
该脚本将自动读取现有的 `models/face_db.npy`，完成人脸数据注册、全量同步、1:N 检索、视觉流上报与大屏接口验证。

## 2026-09-18 目录整理
Python 摄像头注册入口调整至 ../vision/face_register.py；Java 后端可从项目根目录或 backend/ 启动。项目目录和启动方式见 [项目说明](../README.md)。


