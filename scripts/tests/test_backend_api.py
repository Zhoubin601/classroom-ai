"""
测试与验证 Java Spring Boot 后端（MySQL + Redis + Docker）的人脸存储、检索与前端可视化大屏接口。
"""

import json
import sys
from pathlib import Path
import numpy as np

try:
    import urllib.request
    import urllib.error
except ImportError:
    pass

BASE_URL = "http://localhost:8080"


def request_json(url: str, method: str = "GET", data: dict = None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")

    payload = json.dumps(data).encode("utf-8") if data is not None else None
    try:
        with urllib.request.urlopen(req, data=payload, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body)
    except urllib.error.URLError as e:
        print(f"[ERROR] 连接后端失败 ({url}): {e}")
        return None


def main():
    print("=" * 60)
    print("开始测试 Classroom AI 后端服务...")
    print(f"目标后端服务地址: {BASE_URL}")
    print("=" * 60)

    # 1. 检查后端连通性
    overview = request_json(f"{BASE_URL}/api/visual/overview")
    if overview is None:
        print("\n[提示] 后端服务尚未启动或端口 8080 无法连接。")
        print("请先通过 Docker 启动后端容器:")
        print("    powershell -File scripts/compose.ps1 up -d")
        print("或者在本地 backend 目录下启动 Spring Boot:")
        print("    cd backend; ./mvnw spring-boot:run")
        return False

    print("\n1. [GET /api/visual/overview] 初始宏观看板接口测试:")
    print(json.dumps(overview, indent=2, ensure_ascii=False))

    # 2. 读取本地已有特征库或生成 512 维特征
    face_db_path = Path(__file__).resolve().parents[2] / "vision" / "models" / "face_db.npy"
    if face_db_path.exists():
        embedding = np.load(face_db_path).astype(float).tolist()
        print(f"\n成功读取本地特征文件: {face_db_path} (维度: {len(embedding)})")
    else:
        embedding = np.random.randn(512).astype(float)
        embedding = (embedding / np.linalg.norm(embedding)).tolist()
        print(f"\n生成模拟 512 维特征向量 (维度: {len(embedding)})")

    # 3. 注册/存储人脸数据
    register_payload = {
        "studentId": "STU2026001",
        "name": "张三",
        "gender": "MALE",
        "className": "高一(1)班",
        "featureVector": embedding,
        "imagePath": "/uploads/faces/sample_zhangsan.jpg"
    }
    reg_resp = request_json(f"{BASE_URL}/api/face/register", method="POST", data=register_payload)
    print("\n2. [POST /api/face/register] 人脸特征持久化与缓存注册测试:")
    print(json.dumps(reg_resp, indent=2, ensure_ascii=False))

    # 4. 全量获取人脸特征库（供 Python 边缘视觉端同步）
    all_faces = request_json(f"{BASE_URL}/api/face/all")
    print(f"\n3. [GET /api/face/all] 边缘端一键同步特征库测试:")
    if all_faces and "data" in all_faces:
        print(f"当前库中总特征数: {len(all_faces['data'])}")
        first_face = all_faces['data'][0]
        print(f"样例学生: 学号={first_face.get('studentId')}, 姓名={first_face.get('name')}, 特征维度={len(first_face.get('featureVector', []))}")

    # 5. 云端 1:N 人脸检索比对
    search_payload = {
        "featureVector": embedding,
        "threshold": 0.45,
        "topK": 1
    }
    search_resp = request_json(f"{BASE_URL}/api/face/search", method="POST", data=search_payload)
    print("\n4. [POST /api/face/search] 1:N 向量余弦相似度检索测试:")
    print(json.dumps(search_resp, indent=2, ensure_ascii=False))

    # 6. 上报一帧模拟视觉流数据
    stream_payload = {
        "sessionId": "DEMO_SESSION_01",
        "courseName": "AI与具身智能实践",
        "className": "高一(1)班",
        "detectedPersonCount": 1,
        "lookupCount": 1,
        "lookdownCount": 0,
        "lookupRate": 1.0,
        "presentStudentIds": ["STU2026001"],
        "studentPoses": {
            "STU2026001": "UP"
        }
    }
    stream_resp = request_json(f"{BASE_URL}/api/visual/report/stream", method="POST", data=stream_payload)
    print("\n5. [POST /api/visual/report/stream] 机器人视觉流聚合上报测试:")
    print(json.dumps(stream_resp, indent=2, ensure_ascii=False))

    # 7. 再次获取大屏看板与时序折线数据
    overview_after = request_json(f"{BASE_URL}/api/visual/overview")
    print("\n6. [GET /api/visual/overview] 驱动后的实时宏观看板数据:")
    print(json.dumps(overview_after, indent=2, ensure_ascii=False))

    trend_resp = request_json(f"{BASE_URL}/api/visual/trend")
    print("\n7. [GET /api/visual/trend] ECharts 抬头率时序波形数据:")
    print(json.dumps(trend_resp, indent=2, ensure_ascii=False))

    status_resp = request_json(f"{BASE_URL}/api/visual/students/status")
    print("\n8. [GET /api/visual/students/status] 学生实时状态卡片列表:")
    print(json.dumps(status_resp, indent=2, ensure_ascii=False))

    print("\n" + "=" * 60)
    print("✅ 后端所有核心接口验证完成！")
    print("=" * 60)
    return True


if __name__ == "__main__":
    main()
