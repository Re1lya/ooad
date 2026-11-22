#!/usr/bin/env python3
"""在统一权限管理系统中登记课程项目管理系统（应用编码 course-project-management）。

只调用权限系统的管理 API，与管理员在控制台逐项操作等价；可以重复执行：已存在的应用、
菜单与按钮、API 资源、角色和各种关系会被复用，不会重复创建。

登记内容与老师课程系统 master 的源码对应（mingqcn/course-project-mgt）：
  * 菜单与按钮编码：web/src/constants/menu-permissions.ts
  * 后端权限码：backend/.../infrastructure/security/AuthorityCodes.java
  * operationId：api/openapi.yaml
  * 登录入口角色：课程后端要求 /auth/me 的 roles 含 teacher（教师入口）或 student（学生入口）

执行步骤：
  1. 应用、菜单与按钮、API 资源（含 operationId），把每个菜单或按钮绑定到它调用的接口；
  2. 角色 teacher、student、course-observer（继承 teacher）、course-admin（继承 course-observer）；
  3. 设置了 EMPLOYEE_XLSX 时，同步员工信息表：总公司员工补授 teacher，分公司员工补授 student；
  4. 设置了 DEMO_PASSWORD 时，按老师的约定把员工信息表的一万名员工都作为测试账号：为尚未启用的
     员工设置这个测试密码并启用，已启用的账号不动，所以可以重复执行；四个演示工号另外分配
     课程督导、课程管理员角色。TEST_ACCOUNTS=demo 时只处理四个演示工号。

用法：
  RBAC_URL=http://<权限系统地址> RBAC_ADMIN=<管理员> \\
  EMPLOYEE_XLSX=<市政公司员工信息表.xlsx> DEMO_PASSWORD=<测试密码> python3 scripts/register-course-app.py
  管理员密码从环境变量 RBAC_ADMIN_PASSWORD 读取，未设置时交互输入，不回显。
  每个账号的密码在权限系统中做一次 BCrypt，一万个账号在 2 核节点上约需 12 分钟；WORKERS 设置并发数（默认 4）。
"""

import getpass
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor

APP = {"code": "course-project-management", "name": "课程项目管理系统"}

# (编码, 名称, 类型, 上级菜单, 菜单地址)
FRONTEND = [
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
    ("MTEL", "从组织架构选人", "BUTTON", "MTE", None),
    ("MLG", "操作日志", "MENU", None, "/admin/audit"),
]

# (名称, 方法, 路径, 后端权限码, operationId)。
# 前半部分是课程后端自己的接口，权限码随 /auth/me 下发给课程后端做强校验；
# 后三项是权限系统提供给课程系统的组织架构接口（/api/apps/<应用>/auth/...），按绑定授权。
RESOURCES = [
    ("管理员课程列表", "GET", "/admin/courses", "CV", "listAdminCourses"),
    ("审核课程", "POST", "/courses/{courseId}/review", "CR", "reviewCourse"),
    ("修改课程", "PATCH", "/courses/{courseId}", "CE", "updateCourse"),
    ("删除课程", "DELETE", "/courses/{courseId}", "CD", "deleteCourse"),
    ("拷贝课程", "POST", "/courses/copy", "CC", "copyCourse"),
    ("维护课程组长", "PUT", "/courses/{courseId}/leaders", "CTM", "setCourseLeaders"),
    ("维护任课教师", "PUT", "/classes/{classId}/teachers", "CTM", "setClassTeachers"),
    ("新建学期", "POST", "/terms", "TC", "createTerm"),
    ("修改学期", "PATCH", "/terms/{termId}", "TE", "updateTerm"),
    ("删除学期", "DELETE", "/terms/{termId}", "TD", "deleteTerm"),
    ("设置当前学期", "PUT", "/current-term", "TCM", "replaceCurrentTerm"),
    ("取消当前学期", "DELETE", "/current-term", "TCM", "clearCurrentTerm"),
    ("教师列表", "GET", "/teachers", "TV", "listTeachers"),
    ("添加教师", "POST", "/teachers", "TEC", "createTeacher"),
    ("修改教师", "PATCH", "/teachers/{teacherId}", "TEE", "updateTeacher"),
    ("删除教师", "DELETE", "/teachers/{teacherId}", "TED", "deleteTeacher"),
    ("查看全部操作日志", "GET", "/audit-logs", "AV", "listAuditLogs"),
    ("查询统一身份用户", "GET", "/auth/users/{userId}", "UL", "lookupIdentityUser"),
    ("组织架构部门", "GET", "/auth/departments", None, "listIdentityDepartments"),
    ("部门下的人员", "GET", "/auth/departments/{departmentId}/users", None, "listIdentityDepartmentUsers"),
]

# 菜单或按钮 → 它在页面上调用的接口（方法, 路径）
BINDINGS = {
    "MCO": [("GET", "/admin/courses")],
    "MCOR": [("POST", "/courses/{courseId}/review")],
    "MCOE": [("PATCH", "/courses/{courseId}")],
    "MCOD": [("DELETE", "/courses/{courseId}")],
    "MCOT": [("PUT", "/courses/{courseId}/leaders"), ("PUT", "/classes/{classId}/teachers")],
    "MTRC": [("POST", "/terms")],
    "MTRE": [("PATCH", "/terms/{termId}")],
    "MTRD": [("DELETE", "/terms/{termId}")],
    "MTRM": [("PUT", "/current-term"), ("DELETE", "/current-term")],
    "MTE": [("GET", "/teachers")],
    "MTEC": [("POST", "/teachers"), ("GET", "/auth/users/{userId}")],
    "MTEE": [("PATCH", "/teachers/{teacherId}")],
    "MTED": [("DELETE", "/teachers/{teacherId}")],
    "MTEL": [("GET", "/auth/departments"), ("GET", "/auth/departments/{departmentId}/users")],
    "MLG": [("GET", "/audit-logs")],
}

MENUS = ["MCO", "MTR", "MTE", "MLG"]

# (编码, 名称, 说明, 直接授予的菜单或按钮)
ROLES = [
    ("teacher", "教师", "课程系统教师入口；教学工作按课程组长和任课关系授权", []),
    ("student", "学生", "课程系统学生入口；学习功能按选课关系授权", []),
    ("course-observer", "课程督导", "继承教师；只读查看课程、学期、教师和操作日志", MENUS),
    (
        "course-admin",
        "课程管理员",
        "继承课程督导；审核和维护课程、学期、教师",
        [code for code, _, kind, *_ in FRONTEND if kind == "BUTTON"],
    ),
]

# RBAC1：上级角色 → 被继承角色
INHERITANCE = [("course-observer", "teacher"), ("course-admin", "course-observer")]

# 演示工号来自员工信息表：总公司员工同步后是教师，分公司员工同步后是学生
DEMO_USERS = [
    ("SG000001", "course-admin"),
    ("SG000002", "course-observer"),
    ("SG000003", None),
    ("SG000191", None),
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None
        self.credentials = None

    def login(self, username, password):
        """管理员登录并记住凭据：令牌默认 30 分钟过期，启用一万个测试账号时会用到重新登录。"""
        self.credentials = (username, password)
        self.token = self.call(
            "POST", "/api/auth/login", {"username": username, "password": password}, auth=False
        )["accessToken"]

    def call(self, method, path, body=None, form=None, auth=True, retry=True):
        headers = {"X-Client-Menu": "register-course-app"}
        data = None
        if form is not None:
            boundary = uuid.uuid4().hex
            data = multipart(boundary, form)
            headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if auth and self.token:
            headers["Authorization"] = "Bearer " + self.token
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                text = response.read().decode("utf-8")
                return json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            if error.code == 401 and auth and retry and self.credentials:
                self.login(*self.credentials)
                return self.call(method, path, body, form, auth, retry=False)
            detail = error.read().decode("utf-8", "replace")
            raise SystemExit(f"{method} {path} 失败：HTTP {error.code} {detail}")


def multipart(boundary, form):
    """form 的值是字符串，或 (文件名, 字节) 表示文件。"""
    parts = []
    for name, value in form.items():
        if isinstance(value, tuple):
            filename, content = value
            head = (
                f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n"
            )
            parts.append(head.encode("utf-8") + content + b"\r\n")
        else:
            head = f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'
            parts.append(head.encode("utf-8") + str(value).encode("utf-8") + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts)


def register(api):
    systems = {s["code"]: s for s in api.call("GET", "/api/systems")}
    app = systems.get(APP["code"]) or api.call("POST", "/api/systems", APP)
    print(f"应用 {app['name']}（{app['code']}，id={app['id']}）")

    existing = {p["code"]: p for p in api.call("GET", f"/api/permissions?systemId={app['id']}")}
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
    print(f"菜单 {len(MENUS)} 个、按钮 {len(permissions) - len(MENUS)} 个")

    current = {
        (r["method"], r["path"]): r for r in api.call("GET", "/api/resources") if r["systemId"] == app["id"]
    }
    resources = {}
    for name, method, path, code, operation in RESOURCES:
        resource = current.get((method, path))
        if resource is None:
            body = {"systemId": app["id"], "name": name, "method": method, "path": path,
                    "operationId": operation}
            if code:
                body["code"] = code
            resource = api.call("POST", "/api/resources", body)
        elif resource.get("operationId") != operation:
            print(f"注意：{method} {path} 已登记但 operationId 为 {resource.get('operationId')}，"
                  f"应为 {operation}；请在控制台删除后重跑本脚本")
        resources[(method, path)] = resource
    print(f"API 资源 {len(resources)} 个")

    for code, routes in BINDINGS.items():
        for route in routes:
            api.call("PUT", f"/api/permissions/{permissions[code]['id']}/resources/{resources[route]['id']}")
    print(f"菜单与按钮绑定接口 {sum(len(v) for v in BINDINGS.values())} 条")

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
    print("角色：" + "、".join(f"{name}（{code}）" for code, name, *_ in ROLES))
    print("继承：" + "；".join(f"{a} → {b}" for a, b in INHERITANCE))
    return roles


def sync_employees(api, workbook):
    with open(workbook, "rb") as handle:
        content = handle.read()
    rule = {
        "headOffice": os.environ.get("HEAD_OFFICE", "总公司"),
        "headOfficeRole": "teacher",
        "branchRole": "student",
    }
    preview = api.call("POST", "/api/directory/sync/preview",
                       form={"employees": ("employees.xlsx", content), **rule})
    result = api.call("POST", "/api/directory/sync/commit",
                      form={"employees": ("employees.xlsx", content), **rule, "digest": preview["digest"]})
    print(
        f"员工信息同步：{result['rows']} 行、{result['companies']} 个公司；"
        f"新建部门 {result['departmentsCreated']}、沿用 {result['departmentsKept']}；"
        f"新建人员 {result['usersCreated']}、更新 {result['usersUpdated']}、未变 {result['usersUnchanged']}；"
        f"补授角色 {result['roleGrants']} 条"
    )


def find_user(api, username):
    query = urllib.parse.urlencode({"keyword": username, "page": 1, "pageSize": 20})
    for user in api.call("GET", f"/api/users?{query}")["items"]:
        if user["username"] == username:
            return user
    raise SystemExit(f"权限系统中没有工号 {username}；请先设置 EMPLOYEE_XLSX 同步员工信息表")


def prepare_demo(api, roles, password):
    for username, role in DEMO_USERS:
        user = find_user(api, username)
        api.call("PUT", f"/api/users/{user['id']}/password", {"password": password})
        if not user.get("enabled"):
            api.call("PUT", f"/api/users/{user['id']}/status", {"enabled": True})
        if role:
            api.call("PUT", f"/api/users/{user['id']}/roles/{roles[role]['id']}")
        print(f"演示账号 {username} {user['displayName']}：已启用" + (f"，分配 {role}" if role else ""))


def enable_test_accounts(api, password):
    """老师的约定：员工信息表的一万名员工都是测试账号，总公司员工是教师，分公司员工是学生。

    只处理员工信息同步建立的人员（人员类型 EMPLOYEE、带公司名称、不是管理员）。同步时新人员是停用、
    无密码的；这里为其中尚未启用的设置统一的测试密码并启用，已启用的账号不动。
    """
    employees, page = [], 1
    while True:
        result = api.call("GET", f"/api/users?page={page}&pageSize=100")
        employees += [u for u in result["items"]
                      if not u["administrator"] and u["personType"] == "EMPLOYEE" and u.get("companyName")]
        if page * 100 >= result["total"]:
            break
        page += 1
    pending = [u for u in employees if not u["enabled"]]

    def provision(user):
        api.call("PUT", f"/api/users/{user['id']}/password", {"password": password})
        api.call("PUT", f"/api/users/{user['id']}/status", {"enabled": True})

    with ThreadPoolExecutor(max_workers=int(os.environ.get("WORKERS", "4"))) as pool:
        for done, _ in enumerate(pool.map(provision, pending), 1):
            if done % 1000 == 0:
                print(f"测试账号：已启用 {done}/{len(pending)}", flush=True)
    print(f"测试账号：同步员工 {len(employees)} 人，本次启用 {len(pending)} 个，"
          f"此前已启用的 {len(employees) - len(pending)} 个保持不变")


def main():
    api = Api(os.environ.get("RBAC_URL", "http://127.0.0.1:18081"))
    admin = os.environ.get("RBAC_ADMIN", "admin")
    api.login(admin, os.environ.get("RBAC_ADMIN_PASSWORD") or getpass.getpass(f"{admin} 的密码："))

    roles = register(api)
    workbook = os.environ.get("EMPLOYEE_XLSX")
    if workbook:
        sync_employees(api, workbook)
    demo_password = os.environ.get("DEMO_PASSWORD")
    if demo_password:
        prepare_demo(api, roles, demo_password)
        if os.environ.get("TEST_ACCOUNTS", "all") != "demo":
            enable_test_accounts(api, demo_password)
    print(f"完成。课程系统配置 IDENTITY_BASE_URL=<权限系统地址>/api/apps/{APP['code']}")


if __name__ == "__main__":
    sys.exit(main())
