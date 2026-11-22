'use strict';

/**
 * 课程项目管理系统接入后的浏览器走查：用真实浏览器操作老师的课程前端和本系统控制台，逐步截图。
 *
 * 前提：两个系统已按 scripts/register-course-app.py 登记、同步员工信息表并启用演示工号。
 * 走查会添加再删除一名教师，并临时撤销再恢复“课程督导”的“课程管理”菜单。
 *
 *   COURSE_WEB=http://<课程前端> RBAC_URL=http://<权限系统> RBAC_ADMIN=... RBAC_ADMIN_PASSWORD=... \
 *   DEMO_PASSWORD=... CHROME_PATH=<Chrome 或 Edge 可执行文件> OUTPUT=<截图目录> node tests/course-walkthrough.cjs
 *
 * 账号密码只从环境变量读取，不写入截图、日志或仓库。
 */

const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright-core');

const APP = 'course-project-management';
const web = (process.env.COURSE_WEB || 'http://127.0.0.1:4173').replace(/\/$/, '');
const rbac = (process.env.RBAC_URL || 'http://127.0.0.1:18081').replace(/\/$/, '');
const out = path.resolve(process.env.OUTPUT || 'course-walkthrough');
const demoPassword = required('DEMO_PASSWORD');
const admin = { username: required('RBAC_ADMIN'), password: required('RBAC_ADMIN_PASSWORD') };

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`缺少环境变量 ${name}`);
  return value;
}

async function api(token, route, method = 'GET', body) {
  const response = await fetch(rbac + route, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`${method} ${route} 返回 ${response.status}`);
  return response.status === 204 ? null : response.json();
}

async function shot(page, name, fullPage = false) {
  await page.waitForLoadState('networkidle');
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage });
  console.log('截图', name);
}

/** 课程前端的侧栏菜单：有权限时是链接，没有权限时是禁用按钮。 */
function adminMenu(page, label) {
  return page.locator('aside').getByRole('link', { name: label, exact: true });
}

/**
 * 等课程前端的教师列表请求返回。页面不会取消先发出的列表请求，
 * 晚到的整页结果会覆盖查询结果，所以每次查询前先等上一个列表请求结束。
 */
function teacherList(page, keyword) {
  return page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname.endsWith('/api/teachers') &&
      response.request().method() === 'GET' &&
      (keyword === undefined
        ? !url.searchParams.has('keyword')
        : url.searchParams.get('keyword') === keyword)
    );
  });
}

async function searchTeacher(page, no) {
  await page.getByPlaceholder('搜索姓名、工号或统一身份标识').fill(no);
  await Promise.all([
    teacherList(page, no),
    page.getByRole('button', { name: '查询', exact: true }).click(),
  ]);
  await page.getByText('共 1 位教师').waitFor();
}

async function courseLogin(page, account) {
  await page.goto(web + '/');
  await page.getByRole('tab', { name: '教师登录' }).click();
  await page.locator('input[name=username]').fill(account);
  await page.locator('input[name=password]').fill(demoPassword);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL(/\/(teacher|admin)\//);
}

async function main() {
  await fs.mkdir(out, { recursive: true });
  const browser = await chromium.launch({
    executablePath: required('CHROME_PATH'),
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'zh-CN',
  });
  const page = await context.newPage();
  try {
    // 1. 课程管理员（薛峰，SG000001）从教师入口登录：菜单来自权限系统 /auth/me
    await courseLogin(page, 'SG000001');
    await adminMenu(page, '教师').waitFor();
    await shot(page, '01-course-admin-menu');

    // 2. 教师管理：路由守卫由浏览器直连权限系统 /auth/permissions/MTE
    await adminMenu(page, '教师').click();
    await page.getByRole('heading', { level: 1, name: '教师' }).waitFor();
    await shot(page, '02-course-teachers');

    // 3. 添加教师：组织架构两级选择（公司 → 部门）后选人，姓名和工号由权限系统提供
    await page.getByRole('button', { name: '添加教师' }).click();
    const tree = page.getByRole('tree', { name: '统一身份部门树' });
    await tree.waitFor();
    await shot(page, '03-picker-companies');
    const expanders = tree.getByRole('button', { name: /^展开/ });
    const companies = await expanders.count();
    const branch = (await expanders.nth(companies - 1).getAttribute('aria-label')).replace(
      /^展开/,
      '',
    );
    await expanders.nth(companies - 1).click();
    await tree.getByRole('button', { name: '收起' + branch }).waitFor();
    const rows = tree.getByRole('treeitem');
    const department = rows.last();
    await department.scrollIntoViewIfNeeded();
    await shot(page, '04-picker-departments');
    const departmentName = (await department.getByRole('button').last().innerText()).trim();
    await department.getByRole('button').last().click();
    const people = page
      .locator('section')
      .filter({ hasText: `${departmentName} · 用户` })
      .getByRole('button')
      .filter({ hasText: /SG\d{6}/ });
    await people.first().waitFor();
    await shot(page, '05-picker-people');
    // 课程系统删除教师是软删除，删过的工号不能再添加（409），依次换下一位
    let no = null;
    for (let i = 0, total = await people.count(); i < total && !no; i++) {
      const label = (await people.nth(i).innerText()).replace(/\s+/g, ' ');
      await people.nth(i).click();
      await page.getByText(/已选择：/).waitFor();
      await shot(page, '06-picker-selected');
      const reloaded = teacherList(page).catch(() => null);
      const [response] = await Promise.all([
        page.waitForResponse(
          (r) => r.url().endsWith('/api/teachers') && r.request().method() === 'POST',
        ),
        page.getByRole('button', { name: '确认添加' }).click(),
      ]);
      if (response.ok()) {
        no = label.match(/SG\d{6}/)[0];
        await reloaded;
      } else if (response.status() !== 409) throw new Error(`添加教师返回 ${response.status()}`);
    }
    if (!no) throw new Error(`${departmentName} 第一页没有可添加的人员`);
    await page.getByText('教师已添加。').waitFor();
    await searchTeacher(page, no);
    await shot(page, '07-teacher-added');

    // 4. 操作日志：课程后端用当前令牌读权限系统的审计接口
    await adminMenu(page, '日志').click();
    await page.getByText('正在加载日志…').waitFor({ state: 'detached' });
    await page.getByText(/共 [1-9]\d* 条日志/).waitFor();
    await shot(page, '08-course-audit-log');

    // 5. 在权限系统撤销课程督导的“课程管理”菜单：课程管理员经继承得到的菜单随之消失，恢复后回来
    const consoleToken = (await api(null, '/api/auth/login', 'POST', admin)).accessToken;
    const roles = Object.fromEntries(
      (await api(consoleToken, '/api/roles')).map((r) => [r.code, r.id]),
    );
    const system = (await api(consoleToken, '/api/systems')).find((s) => s.code === APP);
    const codes = Object.fromEntries(
      (await api(consoleToken, `/api/permissions?systemId=${system.id}`)).map((p) => [
        p.code,
        p.id,
      ]),
    );
    const grant = `/api/roles/${roles['course-observer']}/permissions/${codes.MCO}`;
    await api(consoleToken, grant, 'DELETE');
    try {
      await page.goto(web + '/teacher/courses');
      await adminMenu(page, '教师').waitFor();
      if (await adminMenu(page, '课程').count()) throw new Error('撤销后“课程”菜单仍可点击');
      await shot(page, '09-menu-revoked');
    } finally {
      await api(consoleToken, grant, 'PUT');
    }
    await page.reload();
    await adminMenu(page, '课程').waitFor();
    await shot(page, '10-menu-restored');

    // 6. 清理：删除刚添加的教师（课程系统做软删除）
    await Promise.all([teacherList(page), adminMenu(page, '教师').click()]);
    await searchTeacher(page, no);
    const added = page
      .locator('article.list-row')
      .filter({ has: page.locator('.pill', { hasText: no }) });
    await added.getByRole('button', { name: '删除', exact: true }).click();
    const dialog = page
      .locator('div')
      .filter({ has: page.getByRole('heading', { level: 2, name: '删除教师' }) })
      .last();
    await dialog.getByText(`确定删除「`).waitFor();
    await dialog.getByRole('button', { name: '删除', exact: true }).click();
    await page.getByRole('heading', { level: 2, name: '删除教师' }).waitFor({ state: 'detached' });

    // 7. 权限系统控制台：薛峰的有效权限及来源（课程管理员 → 课程督导 → 教师）
    const consolePage = await context.newPage();
    await consolePage.goto(rbac + '/ui/index.html');
    await consolePage.getByRole('heading', { name: '登录工作区' }).waitFor();
    await consolePage.getByLabel('登录名', { exact: true }).fill(admin.username);
    await consolePage.getByLabel('密码', { exact: true }).fill(admin.password);
    await consolePage.getByRole('button', { name: '登录', exact: true }).click();
    await consolePage
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '有效权限' })
      .click();
    await consolePage.getByLabel('搜索当前列表').fill('SG000001');
    await consolePage.getByRole('list', { name: '查找结果' }).getByRole('button').first().click();
    await consolePage.getByText('课程管理员 → 课程督导').first().waitFor();
    await shot(consolePage, '11-rbac-effective-permissions', true);

    // 8. 审计日志：权限系统自己的继承变更，以及课程系统写入的记录（按应用分开查看）
    await consolePage
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '审计日志' })
      .click();
    await consolePage.getByText(/共 [1-9]\d* 条/).waitFor();
    await shot(consolePage, '12-rbac-audit-log');
    await consolePage
      .getByRole('group', { name: '应用' })
      .getByRole('button', { name: '课程项目管理系统' })
      .click();
    await consolePage
      .getByText(/联调审计-/)
      .first()
      .waitFor();
    await shot(consolePage, '13-rbac-audit-course-app');
    console.log(`完成：截图在 ${out}`);
  } catch (error) {
    for (const [index, opened] of context.pages().entries())
      await opened
        .screenshot({ path: path.join(out, `failure-${index + 1}.png`), fullPage: true })
        .catch(() => {});
    throw error;
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
