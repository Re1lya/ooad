#!/usr/bin/env python3
"""跨系统联调验收：权限系统统一管理课程项目管理系统的权限。

前提：已运行 scripts/register-course-app.py（带 DEMO_PASSWORD 创建 4 个演示账号）。
只调用两个系统的公开 HTTP 接口；会临时撤回再恢复 course-admin 的“新建学期”按钮、
临时解除再恢复 course-admin → teacher 继承，并写入一条演示审计记录。

用法：
  RBAC_URL=http://<权限系统前端>  COURSE_URL=http://<课程系统前端>/api \
  RBAC_ADMIN=admin RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... python3 scripts/verify-course-integration.py
  （本地直连后端时：RBAC_URL=http://127.0.0.1:18081 COURSE_URL=http://127.0.0.1:8080）
"""

import json
import os
import sys
import urllib.error
import urllib.request
import uuid

RBAC = os.environ.get("RBAC_URL", "http://127.0.0.1:18081").rstrip("/")
COURSE = os.environ.get("COURSE_URL", "http://127.0.0.1:8080").rstrip("/")
results = []


def call(base, method, path, token=None, body=None, menu="verify-course-integration"):
    request = urllib.request.Request(
        base + path, data=None if body is None else json.dumps(body).encode(), method=method
    )
    request.add_header("Content-Type", "application/json")
    request.add_header("X-Client-Menu", menu)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        try:
            return error.code, json.loads(text)
        except ValueError:
            return error.code, text


def check(name, ok, detail=""):
    results.append(bool(ok))
    print(("PASS " if ok else "FAIL ") + name + (f"  {detail}" if detail else ""))


def login(username, password):
    status, body = call(RBAC, "POST", "/api/auth/login", body={"username": username, "password": password})
    if status != 200:
        raise SystemExit(f"{username} 登录失败：HTTP {status} {body}")
    return body["accessToken"]


def main():
    admin = login(os.environ.get("RBAC_ADMIN", "admin"), os.environ["RBAC_ADMIN_PASSWORD"])
    demo = os.environ["DEMO_PASSWORD"]
    cadmin, teacher, observer = (login(u, demo) for u in ("cadmin", "t1001", "observer"))

    status, me = call(RBAC, "GET", "/api/apps/cpm/auth/me", cadmin)
    check("cadmin 的 /auth/me 含继承角色 teacher", status == 200 and set(me["roles"]) >= {"course-admin", "teacher"}, f"roles={me.get('roles')}")
    status, me = call(RBAC, "GET", "/api/apps/cpm/auth/me", teacher)
    check("t1001 只有教师权限码", status == 200 and set(me["permissions"]) == {"TCO", "CC"}, f"permissions={me.get('permissions')}")

    for uid in ("cadmin", "t1001"):
        status, body = call(COURSE, "POST", "/teachers", cadmin, {"userId": uid, "title": "教授", "dept": "信息学院"})
        check(f"课程系统登记教师 {uid}（TEC，经统一身份查姓名）", status in (201, 409), f"HTTP {status}")
    status, _ = call(COURSE, "POST", "/teachers", teacher, {"userId": "observer", "title": "讲师", "dept": "信息学院"})
    check("t1001 无 TEC 时课程系统拒绝登记教师", status == 403, f"HTTP {status}")
    status, me = call(COURSE, "GET", "/me", cadmin)
    check("课程系统 /me 读到权限系统下发的角色", status == 200 and "teacher" in me.get("roles", []), f"HTTP {status}")

    term = {"name": "联调-" + uuid.uuid4().hex[:6], "startDate": "2026-09-01", "endDate": "2027-01-20"}
    status, _ = call(COURSE, "POST", "/terms", cadmin, term)
    check("cadmin 有 TC：课程系统放行新建学期", status in (201, 409), f"HTTP {status}（409 为课程库落库冲突，鉴权已通过）")
    status, _ = call(COURSE, "POST", "/terms", teacher, term)
    check("t1001 无 TC：课程系统拒绝新建学期", status == 403, f"HTTP {status}")
    check("observer 有 CV：可看管理员课程列表", call(COURSE, "GET", "/admin/courses", observer)[0] == 200)
    check("t1001 无 CV：不可看管理员课程列表", call(COURSE, "GET", "/admin/courses", teacher)[0] == 403)

    roles = {r["code"]: r["id"] for r in call(RBAC, "GET", "/api/roles", admin)[1]}
    permissions = {p["code"]: p["id"] for p in call(RBAC, "GET", "/api/permissions", admin)[1]}
    grant = f"/api/roles/{roles['course-admin']}/permissions/{permissions['MTRC']}"
    check("权限系统撤回 course-admin 的“新建学期”按钮", call(RBAC, "DELETE", grant, admin, menu="grants")[0] == 204)
    status, _ = call(COURSE, "POST", "/terms", cadmin, {**term, "name": term["name"] + "b"})
    check("同一 Token 下一次请求课程系统即拒绝", status == 403, f"HTTP {status}")
    check("权限系统恢复授权", call(RBAC, "PUT", grant, admin, menu="grants")[0] == 204)

    edge = f"/api/roles/{roles['course-admin']}/inherits/{roles['teacher']}"
    check("权限系统解除 course-admin → teacher 继承", call(RBAC, "DELETE", edge, admin, menu="hierarchy")[0] == 204)
    status, me = call(RBAC, "GET", "/api/apps/cpm/auth/me", cadmin)
    check("解除继承后不再带 teacher 角色与 TCO", "teacher" not in me["roles"] and "TCO" not in me["permissions"], f"roles={me['roles']}")
    check("权限系统恢复继承", call(RBAC, "PUT", edge, admin, menu="hierarchy")[0] == 204)

    status, _ = call(RBAC, "POST", "/api/apps/cpm/auth/audit-logs", teacher, {
        "requestId": str(uuid.uuid4()), "type": "ROSTER_CHANGED", "objectType": "class",
        "objectId": "cpm-class-1", "target": "联调：添加学生", "before": {"state": "absent"}, "after": {"state": "present"}})
    check("按课程系统审计契约写入", status == 201, f"HTTP {status}")
    status, logs = call(COURSE, "GET", "/audit-logs?page=1&pageSize=10", cadmin)
    check("课程系统经权限系统查询审计（AV）", status == 200, f"total={logs.get('total') if isinstance(logs, dict) else logs}")
    status, page = call(RBAC, "GET", "/api/audit-logs?system=rbac", admin, menu="hierarchy")
    check("权限系统记录了角色继承变更", status == 200 and page["total"] >= 2, f"total={page.get('total')}")

    print(f"\n{sum(results)}/{len(results)} 项通过")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
