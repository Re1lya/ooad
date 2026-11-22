/** FR-10/FR-17/FR-19：在“用户授权”给人盖角色，到“权限来源”按人员看到直接与继承权限及继承路径。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

test('FR-10 FR-17 FR-19 用户授权盖印后，按人员查看有效权限的来源与继承路径', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix();
  const app = await s.api(admin, '/api/systems', 'POST', { code: 'eff-' + tag, name: '来源验收应用' + tag, baseUrl: null });
  const menu = await s.api(admin, '/api/permissions', 'POST', {
    systemId: app.id, code: 'em' + tag, name: '业务台' + tag, type: 'MENU', menuPath: '/work', parentId: null,
  });
  const button = await s.api(admin, '/api/permissions', 'POST', {
    systemId: app.id, code: 'eb' + tag, name: '查看清单' + tag, type: 'BUTTON', menuPath: null, parentId: menu.id,
  });
  const specialist = await s.api(admin, '/api/roles', 'POST', { code: 'spec-' + tag, name: '专员' + tag, description: null });
  const manager = await s.api(admin, '/api/roles', 'POST', { code: 'mgr-' + tag, name: '负责人' + tag, description: null });
  await s.api(admin, `/api/roles/${specialist.id}/permissions/${button.id}`, 'PUT');
  await s.api(admin, `/api/roles/${manager.id}/permissions/${menu.id}`, 'PUT');
  await s.api(admin, `/api/roles/${manager.id}/inherits/${specialist.id}`, 'PUT');
  const member = await s.api(admin, '/api/users', 'POST', {
    username: 'mem-' + tag, displayName: '成员' + tag, password: 'Member-2026!Demo', departmentId: null, personType: 'EMPLOYEE',
  });

  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, '用户授权');
    await page.getByPlaceholder('搜账号或姓名…').fill(member.username);
    await page.getByPlaceholder('搜账号或姓名…').press('Enter');
    await page.settle();
    await page.locator('.user-item', { hasText: member.username }).click();
    await page.settle();
    const seal = page.locator('.role-seal', { hasText: '负责人' + tag });
    await seal.click();
    await page.settle();
    await page.locator('.role-seal--on', { hasText: '负责人' + tag }).waitFor();
    const roles = await s.api(admin, `/api/users/${member.id}/roles`);
    assert.deepEqual(roles.map((r) => r.id), [manager.id], '盖印即授予');

    await s.nav(page, '权限来源');
    await page.getByPlaceholder('搜账号或姓名…').fill(member.username);
    await page.getByPlaceholder('搜账号或姓名…').press('Enter');
    await page.settle();
    await page.locator('.user-item', { hasText: member.username }).click();
    await page.settle();
    const direct = page.locator('.trace', { hasText: '业务台' + tag });
    const inherited = page.locator('.trace', { hasText: '查看清单' + tag });
    await inherited.waitFor();
    assert.match(await direct.innerText(), /直接/);
    assert.match(await inherited.innerText(), /继承/);
    assert.match(
      await inherited.locator('.trace__path').innerText(),
      new RegExp(`负责人${tag}\\s*→\\s*专员${tag}`),
      '继承路径写角色名',
    );
    assert.match(await inherited.innerText(), new RegExp(app.name), '标出所属应用');
    await s.shot(page, 'registry-effective-person');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});
