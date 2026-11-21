#!/usr/bin/env python3
"""端到端验收：课程项目管理系统的登录、菜单、按钮和接口权限都由统一权限管理系统决定。

前提：已运行 scripts/register-course-app.py，并设置 EMPLOYEE_XLSX 和 DEMO_PASSWORD
（同步员工信息表，启用四个演示工号）。

只调用两个系统的公开 HTTP 接口。运行中会临时撤销再恢复课程督导的“课程管理”菜单、
临时解除再恢复“课程管理员 → 课程督导”的继承、添加再删除一名教师、新建再删除一个学期，
并写入一条审计记录；最后让学生账号退出登录。

用法：
  RBAC_URL=<权限系统地址> COURSE_URL=<课程后端地址；经课程前端 nginx 时为 http://<课程前端>/api> \\
  COURSE_ORIGIN=<课程前端的源地址，须在权限系统的 CORS 白名单中> \\
  RBAC_ADMIN=<管理员> RBAC_ADMIN_PASSWORD=... DEMO_PASSWORD=... python3 scripts/verify-course-integration.py
"""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

APP = "course-project-management"
RBAC = os.environ.get("RBAC_URL", "http://127.0.0.1:18081").rstrip("/")
IDENTITY = f"{RBAC}/api/apps/{APP}"
COURSE = os.environ.get("COURSE_URL", "http://127.0.0.1:8080").rstrip("/")
ORIGIN = os.environ.get("COURSE_ORIGIN", "http://127.0.0.1:4173").rstrip("/")
HEAD_OFFICE = os.environ.get("HEAD_OFFICE", "总公司")
MENUS = {"MCO", "MTR", "MTE", "MLG"}
results = []


def call(base, method, path, token=None, body=None, headers=None):
    """返回 (状态码, 响应体, 响应头)。"""
    request = urllib.request.Request(
        base + path, data=None if body is None else json.dumps(body).encode(), method=method
    )
    if body is not None:
        request.add_header("Content-Type", "application/json")
    request.add_header("X-Client-Menu", "verify-course-integration")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None), response.headers
    except urllib.error.HTTPError as error:
        text = error.read().decode()
        try:
            return error.code, json.loads(text) if text else None, error.headers
        except ValueError:
            return error.code, text, error.headers


def check(name, ok, detail=""):
    results.append(bool(ok))
    print(("PASS " if ok else "FAIL ") + name + (f"  {detail}" if detail else ""))


def course_login(role, account, password):
    status, body, _ = call(COURSE, "POST", "/login", body={"role": role, "account": account, "password": password})
    return status, body or {}


def allowed(token, code):
    return call(IDENTITY, "GET", f"/auth/permissions/{code}", token)[1] == {"allowed": True}


def main():
    demo = os.environ["DEMO_PASSWORD"]
    status, body, _ = call(RBAC, "POST", "/api/auth/login", body={
        "username": os.environ.get("RBAC_ADMIN", "admin"), "password": os.environ["RBAC_ADMIN_PASSWORD"]})
    if status != 200:
        raise SystemExit(f"权限系统管理员登录失败：HTTP {status}")
    console = body["accessToken"]

    print("== 课程系统登录（课程后端转调 /auth/login 与 /auth/me）")
    status, body = course_login("teacher", "SG000001", demo)
    user = body.get("user") or {}
    check("课程管理员从教师入口登录", status == 200
          and {"course-admin", "course-observer", "teacher"} <= set(user.get("roles", [])), f"roles={user.get('roles')}")
    check("登录结果带回菜单码和后端权限码", {"MCO", "MTE", "MTEC", "CV", "TEC"} <= set(user.get("permissions", [])))
    admin = body.get("accessToken")
    status, body = course_login("teacher", "SG000003", demo)
    teacher = body.get("accessToken")
    check("普通教师登录后没有管理菜单", status == 200 and not MENUS & set(body["user"]["permissions"]),
          f"permissions={body.get('user', {}).get('permissions')}")
    status, body = course_login("student", "SG000191", demo)
    student = body.get("accessToken")
    check("学生从学生入口登录", status == 200 and "student" in body["user"]["roles"], f"HTTP {status}")
    check("密码错误时登录失败", course_login("teacher", "SG000003", demo + "x")[0] == 401)
    check("同步后尚未启用的员工不能登录", course_login("teacher", "SG000004", demo)[0] == 401)

    print("== 令牌只对目标系统有效")
    status, me, _ = call(IDENTITY, "GET", "/auth/me", admin)
    check("/auth/me 的 subject 与 no 都是工号，并带姓名",
          status == 200 and me["subject"] == me["no"] == "SG000001" and me.get("name"), f"name={me.get('name')}")
    check("课程系统令牌不能调用权限系统控制台", call(RBAC, "GET", "/api/me", admin)[0] == 401)
    check("控制台令牌不能当作课程系统身份", call(IDENTITY, "GET", "/auth/me", console)[0] == 401)

    print("== 浏览器直连的接口与 CORS")
    preflight = {"Origin": ORIGIN, "Access-Control-Request-Method": "GET",
                 "Access-Control-Request-Headers": "authorization"}
    status, _, headers = call(IDENTITY, "OPTIONS", "/auth/permissions/MCO", headers=preflight)
    check("课程前端源地址的预检通过", status == 200 and headers.get("Access-Control-Allow-Origin") == ORIGIN,
          f"HTTP {status}")
    status, _, headers = call(IDENTITY, "OPTIONS", "/auth/permissions/MCO",
                              headers={**preflight, "Origin": "http://unknown.example"})
    check("未登记的源地址预检被拒绝", status == 403 and not headers.get("Access-Control-Allow-Origin"), f"HTTP {status}")
    status, body, headers = call(IDENTITY, "GET", "/auth/permissions/MCO", admin, headers={"Origin": ORIGIN})
    check("课程管理员有“课程管理”菜单", body == {"allowed": True}
          and headers.get("Access-Control-Allow-Origin") == ORIGIN)
    check("普通教师没有“课程管理”菜单", not allowed(teacher, "MCO"))
    status, body, _ = call(IDENTITY, "GET", "/auth/api-permissions/createTeacher", admin)
    check("按 operationId 判断：课程管理员可以添加教师", body == {"operationId": "createTeacher", "allowed": True})
    status, body, _ = call(IDENTITY, "GET", "/auth/api-permissions/createTeacher", teacher)
    check("按 operationId 判断：普通教师不能添加教师", body == {"operationId": "createTeacher", "allowed": False})
    status, body, _ = call(IDENTITY, "GET", "/auth/api-permissions/noSuchOperation", admin)
    check("未登记的 operationId 返回 404", status == 404 and body.get("code") == "API_OPERATION_NOT_FOUND")

    print("== 两级部门选择器")
    status, page, _ = call(IDENTITY, "GET", "/auth/departments?page=1&pageSize=100", admin, headers={"Origin": ORIGIN})
    companies = page["items"] if status == 200 else []
    check("第一级是公司", companies and all(c["parentId"] is None and c["hasChildren"] for c in companies)
          and any(c["name"] == HEAD_OFFICE for c in companies), f"{len(companies)} 个")
    branch = [c for c in companies if c["name"] != HEAD_OFFICE][-1]
    status, page, _ = call(IDENTITY, "GET", f"/auth/departments?parentId={branch['id']}&page=1&pageSize=100", admin)
    departments = page["items"] if status == 200 else []
    check(f"第二级是 {branch['name']} 的部门", departments
          and all(d["parentId"] == branch["id"] and not d["hasChildren"] for d in departments), f"{len(departments)} 个")
    department = departments[-1]
    status, page, _ = call(IDENTITY, "GET", f"/auth/departments/{department['id']}/users?page=1&pageSize=100", admin)
    people = page["items"] if status == 200 else []
    check(f"{department['name']} 下列出人员，userId 即工号", people and all(
        p["userId"] == p["no"] and p["departmentId"] == department["id"] for p in people), f"共 {page.get('total')} 人")
    check("没有“从组织架构选人”按钮时不能读组织架构",
          call(IDENTITY, "GET", "/auth/departments?page=1&pageSize=20", teacher)[0] == 403)

    print("== 添加教师（课程后端用当前令牌查 /auth/users/{userId}）")
    candidate = created = None
    status = 0
    for person in reversed(people):
        query = urllib.parse.urlencode({"keyword": person["no"], "page": 1, "pageSize": 20})
        if call(COURSE, "GET", f"/teachers?{query}", admin)[1]["total"]:
            continue
        form = {"userId": person["userId"], "title": "讲师", "dept": department["name"]}
        status, body, _ = call(COURSE, "POST", "/teachers", admin, form)
        if status == 409:
            continue  # 课程系统删除教师是软删除，删过的工号不能再添加，换下一位
        candidate, created = person, body
        break
    check(f"课程管理员从组织架构选人添加为教师：{candidate['name'] if candidate else '无可用人选'}",
          candidate and status in (200, 201)
          and created["no"] == candidate["no"] and created["name"] == candidate["name"],
          f"HTTP {status}，工号 {candidate['no'] if candidate else '-'}")
    form = {"userId": people[0]["userId"], "title": "讲师", "dept": department["name"]}
    check("普通教师添加教师被课程系统拒绝", call(COURSE, "POST", "/teachers", teacher, form)[0] == 403)
    if candidate and status in (200, 201):
        check("删除刚添加的教师", call(COURSE, "DELETE", f"/teachers/{created['id']}", admin)[0] == 204)

    print("== 按钮权限码直达课程后端")
    term = {"name": "联调-" + uuid.uuid4().hex[:6], "startDate": "2031-09-01", "endDate": "2032-01-20"}
    status, created, _ = call(COURSE, "POST", "/terms", admin, term)
    check("课程管理员有“新建学期”按钮：课程系统放行", status in (200, 201), f"HTTP {status}")
    check("普通教师没有该按钮：课程系统拒绝",
          call(COURSE, "POST", "/terms", teacher, {**term, "name": term["name"] + "b"})[0] == 403)
    if status in (200, 201):
        check("删除刚建的学期", call(COURSE, "DELETE", f"/terms/{created['id']}", admin)[0] == 204)

    print("== 撤销立即生效，恢复后回来")
    roles = {r["code"]: r["id"] for r in call(RBAC, "GET", "/api/roles", console)[1]}
    systems = {s["code"]: s["id"] for s in call(RBAC, "GET", "/api/systems", console)[1]}
    codes = {p["code"]: p["id"] for p in call(RBAC, "GET", f"/api/permissions?systemId={systems[APP]}", console)[1]}
    grant = f"/api/roles/{roles['course-observer']}/permissions/{codes['MCO']}"
    check("撤销课程督导的“课程管理”菜单", call(RBAC, "DELETE", grant, console)[0] == 204)
    check("课程管理员经继承得到的该菜单随之消失", not allowed(admin, "MCO"))
    check("同一令牌访问管理员课程列表被拒绝", call(COURSE, "GET", "/admin/courses", admin)[0] == 403)
    check("恢复授权", call(RBAC, "PUT", grant, console)[0] == 204)
    check("菜单和课程列表恢复", allowed(admin, "MCO") and call(COURSE, "GET", "/admin/courses", admin)[0] == 200)

    edge = f"/api/roles/{roles['course-admin']}/inherits/{roles['course-observer']}"
    check("解除“课程管理员 → 课程督导”继承", call(RBAC, "DELETE", edge, console)[0] == 204)
    me = call(IDENTITY, "GET", "/auth/me", admin)[1]
    check("解除后失去课程督导角色和四个菜单，自己的按钮仍在",
          "course-observer" not in me["roles"] and not MENUS & set(me["permissions"]) and "MTEC" in me["permissions"],
          f"roles={me['roles']}")
    check("教师列表随菜单收回", call(COURSE, "GET", "/teachers?page=1&pageSize=20", admin)[0] == 403)
    check("恢复继承", call(RBAC, "PUT", edge, console)[0] == 204)
    me = call(IDENTITY, "GET", "/auth/me", admin)[1]
    check("恢复后角色与菜单回来", "course-observer" in me["roles"] and MENUS <= set(me["permissions"]))

    print("== 审计")
    target = "联调审计-" + uuid.uuid4().hex[:6]
    entry = {"requestId": str(uuid.uuid4()), "type": "ROSTER_CHANGED", "objectType": "class",
             "objectId": "class-1", "target": target, "before": {"state": "absent"}, "after": {"state": "present"}}
    check("按课程系统的审计契约写入", call(IDENTITY, "POST", "/auth/audit-logs", admin, entry)[0] == 201)
    query = urllib.parse.urlencode({"target": target, "page": 1, "pageSize": 20})
    status, logs, _ = call(COURSE, "GET", f"/audit-logs?{query}", admin)
    check("课程系统的操作日志页读到这条记录", status == 200 and any(i["target"] == target for i in logs["items"]),
          f"HTTP {status}")
    status, logs, _ = call(RBAC, "GET", f"/api/audit-logs?system={APP}&target={urllib.parse.quote(target)}", console)
    check("权限系统控制台也能查到", status == 200 and logs["total"] == 1)
    status, logs, _ = call(RBAC, "GET", "/api/audit-logs?system=rbac&page=1&pageSize=20", console)
    latest = logs["items"][:2] if status == 200 else []
    check("权限系统审计记下了这次解除和恢复继承",
          {i["type"] for i in latest} == {"ROLE_INHERITANCE_REMOVED", "ROLE_INHERITANCE_ADDED"}
          and all("课程督导" in i["target"] for i in latest), "；".join(i["target"] for i in latest))

    print("== 退出登录")
    check("学生退出登录", call(IDENTITY, "POST", "/auth/logout", student)[0] == 204)
    check("退出后同一令牌在课程系统失效", call(COURSE, "GET", "/me", student)[0] == 401)

    print(f"\n{sum(results)}/{len(results)} 项通过")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
