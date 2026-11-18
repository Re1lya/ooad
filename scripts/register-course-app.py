#!/usr/bin/env python3
"""在 Cdwy 统一权限管理系统中登记“课程项目管理系统”（应用编码 cpm）的全部权限。

只调用权限系统公开的管理 API（与管理员在界面上逐项操作等价），不使用任何初始化接口；
可重复执行：已存在的应用、菜单、按钮、API 资源、角色和关系会被复用，不会重复创建。

登记内容与课程系统源码一一对应（老师仓库 mingqcn/course-project-mgt）：
  * 前端菜单/按钮码：web/src/constants/menu-permissions.ts（MCO、MCOR …）
  * 后端 API 权限码：backend/.../infrastructure/security/AuthorityCodes.java（TC、CV …）
  * 角色编码 teacher：课程后端按 roles 中的 "teacher" 判断教师身份（审计可见范围）

RBAC1 演示：course-admin（课程管理员）继承 teacher（教师），因此同时拥有教师工作台权限。

用法：
  RBAC_URL=http://<权限系统前端地址> RBAC_ADMIN=admin python3 scripts/register-course-app.py
  （管理员密码从环境变量 RBAC_ADMIN_PASSWORD 读取，未设置时交互输入，不回显）
  可选：DEMO_PASSWORD=<演示账号密码> 同时创建 t1001（教师）、cadmin（课程管理员）、
        observer（课程督导）、s2026001（学生）四个演示账号并分配角色。
"""

import getpass
import json
import os
import sys
import urllib.error
import urllib.request

APP = {"code": "cpm", "name": "课程项目管理系统"}

# (编码, 名称, 类型, 父菜单编码, 菜单地址)
FRONTEND = [
    ("TCO", "教师工作台", "MENU", None, "/teacher/courses"),
    ("MCO", "课程管理", "MENU", None, "/admin/courses"),
    ("MCOR", "审核课程", "BUTTON", "MCO", None),
    ("MCOE", "修改课程", "BUTTON", "MCO", None),
    ("MCOD", "删除课程", "BUTTON", "MCO", None),
    ("MCOT", "维护课程团队", "BUTTON", "MCO", None),
    ("MTR", "学期管理", "MENU", None, "/admin/terms"),
    ("MTRC", "新建学期", "BUTTON", "MTR", None),
    ("MTRE", "修改学期", "BUTTON", "MTR", None),
    ("MTRD", "删除学期", "BUTTON", "MTR", None),
    ("MTRM", "设置当前学期", "BUTTON", "MTR", None),
    ("MTE", "教师管理", "MENU", None, "/admin/teachers"),
    ("MTEC", "添加教师", "BUTTON", "MTE", None),
    ("MTEE", "修改教师", "BUTTON", "MTE", None),
    ("MTED", "删除教师", "BUTTON", "MTE", None),
    ("MTEL", "查询统一身份", "BUTTON", "MTE", None),
    ("MLG", "操作日志", "MENU", None, "/admin/audit"),
]

# (名称, 方法, 路径, 后端权限码)；路径是课程后端自身的路由（不含前端 /api 前缀）
RESOURCES = [
    ("拷贝课程", "POST", "/courses/copy", "CC"),
    ("管理员课程列表", "GET", "/admin/courses", "CV"),
    ("审核课程", "POST", "/courses/{courseId}/review", "CR"),
    ("修改课程", "PATCH", "/courses/{courseId}", "CE"),
    ("删除课程", "DELETE", "/courses/{courseId}", "CD"),
    ("维护课程组长", "PUT", "/courses/{courseId}/leaders", "CTM"),
    ("新建学期", "POST", "/terms", "TC"),
    ("修改学期", "PATCH", "/terms/{termId}", "TE"),
    ("删除学期", "DELETE", "/terms/{termId}", "TD"),
    ("设置当前学期", "PUT", "/current-term", "TCM"),
    ("取消当前学期", "DELETE", "/current-term", "TCM"),
    ("教师列表", "GET", "/teachers", "TV"),
    ("添加教师", "POST", "/teachers", "TEC"),
    ("修改教师", "PATCH", "/teachers/{teacherId}", "TEE"),
    ("删除教师", "DELETE", "/teachers/{teacherId}", "TED"),
    ("查询统一身份用户", "GET", "/identity/users/{userId}", "UL"),
    ("查看操作日志", "GET", "/audit-logs", "AV"),
]

# 前端权限 → 它调用的后端 API（方法, 路径）
BINDINGS = {
    "TCO": [("POST", "/courses/copy")],
    "MCO": [("GET", "/admin/courses")],
    "MCOR": [("POST", "/courses/{courseId}/review")],
    "MCOE": [("PATCH", "/courses/{courseId}")],
    "MCOD": [("DELETE", "/courses/{courseId}")],
    "MCOT": [("PUT", "/courses/{courseId}/leaders")],
    "MTRC": [("POST", "/terms")],
    "MTRE": [("PATCH", "/terms/{termId}")],
    "MTRD": [("DELETE", "/terms/{termId}")],
    "MTRM": [("PUT", "/current-term"), ("DELETE", "/current-term")],
    "MTE": [("GET", "/teachers")],
    "MTEC": [("POST", "/teachers")],
    "MTEE": [("PATCH", "/teachers/{teacherId}")],
    "MTED": [("DELETE", "/teachers/{teacherId}")],
    "MTEL": [("GET", "/identity/users/{userId}")],
    "MLG": [("GET", "/audit-logs")],
}

# (编码, 名称, 说明, 直接授予的前端权限)
ROLES = [
    ("teacher", "教师", "课程系统教师：教师工作台与课程拷贝", ["TCO"]),
    ("student", "学生", "课程系统学生：学生端功能不需要管理权限码", []),
    (
        "course-admin",
        "课程管理员",
        "继承教师；维护课程、学期、教师并查看操作日志",
        [code for code, *_ in FRONTEND if code != "TCO"],
    ),
    ("course-observer", "课程督导", "只读：查看课程列表与操作日志", ["MCO", "MLG"]),
]

# RBAC1：高级角色 → 被继承角色
INHERITANCE = [("course-admin", "teacher")]

DEMO_USERS = [
    ("t1001", "演示教师", "teacher"),
    ("cadmin", "演示课程管理员", "course-admin"),
    ("observer", "演示课程督导", "course-observer"),
    ("s2026001", "演示学生", "student"),
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method, path, body=None, ok=(200, 201, 204)):
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        request.add_header("X-Client-Menu", "register-course-app")
        if self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")
            if error.code in ok:
                return json.loads(detail) if detail else None
            raise SystemExit(f"{method} {path} 失败：HTTP {error.code} {detail}")


def main():
    base = os.environ.get("RBAC_URL", "http://127.0.0.1:18081")
    admin = os.environ.get("RBAC_ADMIN", "admin")
    password = os.environ.get("RBAC_ADMIN_PASSWORD") or getpass.getpass(f"{admin} 的密码：")
    api = Api(base)
    api.token = api.call("POST", "/api/auth/login", {"username": admin, "password": password})[
        "accessToken"
    ]

    systems = {s["code"]: s for s in api.call("GET", "/api/systems")}
    app = systems.get(APP["code"]) or api.call("POST", "/api/systems", APP)
    print(f"应用 {app['code']}（id={app['id']}）")

    existing = {
        p["code"]: p for p in api.call("GET", "/api/permissions") if p["systemId"] == app["id"]
    }
    permissions = {}
    for code, name, kind, parent, path in FRONTEND:
        if code in existing:
            permissions[code] = existing[code]
            continue
        body = {"systemId": app["id"], "code": code, "name": name, "type": kind}
        if path:
            body["menuPath"] = path
        if parent:
            body["parentId"] = permissions[parent]["id"]
        permissions[code] = api.call("POST", "/api/permissions", body)
    print(f"菜单与按钮 {len(permissions)} 项")

    current = {
        (r["method"], r["path"]): r
        for r in api.call("GET", "/api/resources")
        if r["systemId"] == app["id"]
    }
    resources = {}
    for name, method, path, code in RESOURCES:
        key = (method, path)
        resources[key] = current.get(key) or api.call(
            "POST",
            "/api/resources",
            {"systemId": app["id"], "name": name, "method": method, "path": path, "code": code},
        )
    print(f"后端 API 资源 {len(resources)} 项")

    for code, routes in BINDINGS.items():
        for route in routes:
            api.call(
                "PUT",
                f"/api/permissions/{permissions[code]['id']}/resources/{resources[route]['id']}",
            )
    print("前端权限与 API 绑定完成")

    known = {r["code"]: r for r in api.call("GET", "/api/roles")}
    roles = {}
    for code, name, description, granted in ROLES:
        roles[code] = known.get(code) or api.call(
            "POST", "/api/roles", {"code": code, "name": name, "description": description}
        )
        for permission in granted:
            api.call("PUT", f"/api/roles/{roles[code]['id']}/permissions/{permissions[permission]['id']}")
    for senior, inherited in INHERITANCE:
        api.call("PUT", f"/api/roles/{roles[senior]['id']}/inherits/{roles[inherited]['id']}")
    print("角色、角色授权与继承（course-admin → teacher）完成")

    demo_password = os.environ.get("DEMO_PASSWORD")
    if demo_password:
        users = {u["username"]: u for u in api.call("GET", "/api/users?page=0&size=100")["items"]}
        for username, display, role in DEMO_USERS:
            user = users.get(username) or api.call(
                "POST",
                "/api/users",
                {"username": username, "displayName": display, "password": demo_password},
            )
            api.call("PUT", f"/api/users/{user['id']}/roles/{roles[role]['id']}")
        print("演示账号：" + "、".join(f"{u}（{r}）" for u, _, r in DEMO_USERS))
    print("完成。课程后端配置 APP_IDENTITY_BASE_URL=<权限系统后端>/api/apps/cpm 即可接入。")


if __name__ == "__main__":
    sys.exit(main())
