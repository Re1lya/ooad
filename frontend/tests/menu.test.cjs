/** FR-28：权限系统控制台的菜单与按钮按后端授权动态显示，授权变化后重新登录或刷新即生效。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

test('FR-28 菜单根据后端授权动态显示', { skip: s.skip }, async () => {
  const admin = await s.token();
  const { perms } = await s.ensureConsole(admin);
  const suffix = Date.now().toString(36);
  const role = await s.api(admin, '/api/roles', 'POST', { code: 'auditor_' + suffix, name: '审计员' + suffix, description: null });
  await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.audit'].id}`, 'PUT');
  const pass = 'Auditor-2026!Demo';
  const user = await s.api(admin, '/api/users', 'POST', { username: 'aud-' + suffix, displayName: '审计员' + suffix, password: pass, departmentId: null, personType: 'EMPLOYEE' });
  await s.api(admin, `/api/users/${user.id}/roles/${role.id}`, 'PUT');

  const browser = await s.launch();
  const checks = [];
  try {
    const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
    const links = async () => (await page.getByRole('navigation', { name: '主导航' }).getByRole('link').allInnerTexts()).map((t) => t.replace(/^\d+\s*/, '').trim());
    await s.login(page, user.username, pass);
    await page.getByRole('heading', { level: 1, name: '审计日志' }).waitFor();
    assert.deepEqual(await links(), ['审计日志', '我的权限', '修改密码']);
    checks.push('只授权审计日志时，导航只有审计日志与个人页面，且直接进入第一个可用页面');

    await page.goto(`${s.base}/ui/index.html#/roles`);
    await page.getByRole('heading', { level: 1, name: '审计日志' }).waitFor();
    checks.push('直接输入未授权页面的地址会回到可用页面');

    await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.users'].id}`, 'PUT');
    await page.reload();
    await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: /用户管理/ }).waitFor();
    assert.deepEqual(await links(), ['用户管理', '审计日志', '我的权限', '修改密码']);
    await s.nav(page, '用户管理');
    assert.equal(await page.getByRole('button', { name: '新增用户', exact: true }).count(), 0, '没有按钮权限时不显示新增');
    checks.push('授予用户管理菜单后出现该菜单，未授予按钮时不显示写操作');

    await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.users.write'].id}`, 'PUT');
    await page.reload();
    await page.getByRole('button', { name: '新增用户', exact: true }).waitFor();
    checks.push('授予按钮权限后显示对应写操作');

    await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.users'].id}`, 'DELETE');
    await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.users.write'].id}`, 'DELETE');
    await page.reload();
    await page.getByRole('heading', { level: 1, name: '审计日志' }).waitFor();
    assert.deepEqual(await links(), ['审计日志', '我的权限', '修改密码']);
    checks.push('撤销授权后菜单消失，停留的页面回到可用页面');
    await s.shot(page, 'menu-auditor');
    await s.record('menu-verification.json', { checks, errors: [] });
  } finally {
    await browser.close();
  }
});
