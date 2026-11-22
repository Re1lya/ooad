/** FR-13/FR-38、NFR-11：权限台账本身也由统一授权管理——侧栏菜单、写操作按钮按本人在 rbac 应用中的授权显示；内置对象不可停用或注销。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

test('FR-13 FR-38 NFR-11 侧栏菜单与写操作按钮随本人在 rbac 应用中的授权显示，请求带当前菜单', { skip: s.skip }, async () => {
  const admin = await s.token();
  const perms = await s.consolePermissions(admin);
  const tag = s.suffix();
  const role = await s.api(admin, '/api/roles', 'POST', { code: 'auditor-' + tag, name: '审计员' + tag, description: null });
  for (const code of ['rbac.users', 'rbac.effective', 'rbac.audit'])
    await s.api(admin, `/api/roles/${role.id}/permissions/${perms[code].id}`, 'PUT');
  const password = 'Auditor-2026!Demo';
  const user = await s.api(admin, '/api/users', 'POST', {
    username: 'ar-' + tag,
    displayName: '审计员' + tag,
    password,
    departmentId: null,
    personType: 'EMPLOYEE',
  });
  await s.api(admin, `/api/users/${user.id}/roles/${role.id}`, 'PUT');

  const browser = await s.launch();
  try {
    const { page, errors, traffic } = await s.open(browser);
    await s.login(page, user.username, password);
    assert.deepEqual(await s.navItems(page), ['用户', '权限来源', '审计日志'], '侧栏只列本人有权进入的三个菜单');
    assert.match(page.url(), /#\/users/, '登录后进入第一个有权限的页面');
    await page.locator('.ledger tbody tr').first().waitFor();
    assert.equal(await page.getByRole('button', { name: '登记用户' }).count(), 0, '没有“维护用户”时不显示登记');
    assert.equal(await page.getByRole('button', { name: '改', exact: true }).count(), 0, '没有“维护用户”时不显示修改');

    // 直接输入没有权限的地址：回到第一个有权限的页面
    await page.goto(s.ui + '#/roles');
    await page.settle();
    assert.match(page.url(), /#\/users/, '无权页面被转回用户页');

    // 每个接口请求都带当前路由名，供后端 logback 按菜单标记
    await s.nav(page, '审计日志');
    const auditCalls = traffic.requests.filter((r) => r.url.includes('/api/audit-logs'));
    assert.ok(auditCalls.length > 0 && auditCalls.every((r) => r.menu === 'audit'), 'X-Client-Menu 为当前菜单的路由名');

    // 补授“维护用户”按钮后重新登录：出现登记与修改
    await s.api(admin, `/api/roles/${role.id}/permissions/${perms['rbac.users.write'].id}`, 'PUT');
    await page.getByRole('button', { name: '退出登录' }).click();
    await s.login(page, user.username, password);
    await page.getByRole('button', { name: '登记用户' }).waitFor();
    assert.ok((await page.getByRole('button', { name: '改', exact: true }).count()) > 0, '有“维护用户”后可以修改');
    // 自己那一行没有停用与注销
    const self = s.row(page, user.username);
    await page.getByPlaceholder('搜账号或姓名…').fill(user.username);
    await page.getByRole('button', { name: '查', exact: true }).click();
    await page.settle();
    assert.equal(await self.getByRole('switch').count(), 0, '不能停用自己');
    assert.equal(await self.getByRole('button', { name: '销' }).count(), 0, '不能注销自己');
    await s.shot(page, 'registry-console-limited');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});

test('FR-38 内置应用、内置管理员角色、内置菜单按钮与管理接口只显示“内置”，不提供停用和注销', { skip: s.skip }, async () => {
  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);

    await s.nav(page, '接入应用');
    const app = s.row(page, '权限管理系统');
    await app.getByText('内置', { exact: true }).waitFor();
    assert.equal(await app.getByRole('switch').count(), 0, '内置应用不能停用');
    assert.equal(await app.getByRole('button', { name: '销' }).count(), 0, '内置应用不能注销');

    await s.nav(page, '角色');
    const adminRole = s.row(page, 'rbac-admin');
    await adminRole.getByText('内置', { exact: true }).waitFor();
    assert.equal(await adminRole.getByRole('switch').count(), 0, '内置管理员角色不能停用');

    await s.nav(page, '前端权限');
    const menu = page.locator('.perm-row', { hasText: 'rbac.overview' });
    await menu.getByText('内置', { exact: true }).waitFor();
    assert.equal(await menu.getByRole('switch').count(), 0, '内置菜单不能停用');
    const button = page.locator('.perm-row', { hasText: 'rbac.users.write' });
    assert.equal(await button.getByRole('button', { name: '下设' }).count(), 0, '按钮下不能再挂权限');

    await s.nav(page, 'API 资源');
    const resource = s.row(page, '/api/system-info');
    await resource.getByText('内置', { exact: true }).waitFor();
    assert.equal(await resource.getByRole('button', { name: '销' }).count(), 0, '内置管理接口不能注销');
    await s.shot(page, 'registry-built-in');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});
