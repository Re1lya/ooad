/** FR-23/26：角色层级树加载、新增与解除继承、循环继承提示、直接与继承权限区分，继承变化即时影响已登录用户。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

// 测试类型：等价类、异常
test('FR-16 FR-17 FR-18 FR-19 角色层级、循环继承与有效权限来源', { skip: s.skip }, async () => {
  const admin = await s.token();
  await s.ensureConsole(admin);
  const suffix = Date.now().toString(36);
  const name = (text) => `${text}${suffix}`;
  const app = await s.api(admin, '/api/systems', 'POST', { code: 'h' + suffix, name: name('层级验收应用'), baseUrl: null });
  const menu = await s.api(admin, '/api/permissions', 'POST', { systemId: app.id, code: 'hm' + suffix, name: name('业务工作台'), type: 'MENU', menuPath: '/work', parentId: null });
  const button = await s.api(admin, '/api/permissions', 'POST', { systemId: app.id, code: 'hb' + suffix, name: name('查看业务列表'), type: 'BUTTON', menuPath: null, parentId: menu.id });
  const reader = await s.api(admin, '/api/roles', 'POST', { code: 'reader_' + suffix, name: name('浏览者'), description: null });
  const specialist = await s.api(admin, '/api/roles', 'POST', { code: 'specialist_' + suffix, name: name('专员'), description: null });
  const manager = await s.api(admin, '/api/roles', 'POST', { code: 'manager_' + suffix, name: name('负责人'), description: null });
  await s.api(admin, `/api/roles/${reader.id}/permissions/${button.id}`, 'PUT');
  await s.api(admin, `/api/roles/${manager.id}/permissions/${menu.id}`, 'PUT');
  await s.api(admin, `/api/roles/${specialist.id}/inherits/${reader.id}`, 'PUT');
  const memberPass = 'Member-2026!Demo';
  const member = await s.api(admin, '/api/users', 'POST', { username: 'hm-' + suffix, displayName: name('层级成员'), password: memberPass, departmentId: null, personType: 'EMPLOYEE' });
  await s.api(admin, `/api/users/${member.id}/roles/${manager.id}`, 'PUT');

  const edgesOf = async (id) => (await s.api(admin, '/api/role-hierarchy')).filter((e) => e.seniorRoleId === id).length;
  const browser = await s.launch();
  const checks = [];
  const errors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('pageerror', (e) => errors.push(e.message));
    await s.login(page, s.admin.username, s.admin.password);
    await s.idle(page);
    await s.nav(page, '角色继承');
    const graph = page.getByRole('group', { name: '角色层级图' });
    await graph.getByRole('button', { name: name('专员'), exact: true }).waitFor();
    checks.push('层级图加载并包含已有继承');

    // 负责人尚未参与继承，在“未参与继承”中选中它。
    await page.getByLabel('未参与继承的角色').getByRole('button', { name: name('负责人'), exact: true }).click();
    await page.locator('.role-name').filter({ hasText: name('负责人') }).waitFor();
    await page.getByLabel('被继承角色', { exact: true }).selectOption(String(specialist.id));
    await page.getByRole('button', { name: '建立继承', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(await edgesOf(manager.id), 0, '取消不应写入');
    await page.getByRole('button', { name: '建立继承', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认建立', exact: true }).click();
    await page.locator('.inherited-list').filter({ hasText: name('专员') }).waitFor();
    await s.idle(page);
    assert.equal(await edgesOf(manager.id), 1);
    const row = page.locator('.origin-row').filter({ hasText: name('查看业务列表') });
    await row.waitFor();
    assert.match(await row.innerText(), /继承/, '继承得来的权限应标为继承');
    assert.match(await page.locator('.origin-row').filter({ hasText: name('业务工作台') }).innerText(), /直接/);
    checks.push('建立继承前确认、取消不写入、直接与继承权限分别标注');
    await s.shot(page, 'hierarchy');

    // 浏览者 ← 专员 ← 负责人：再让浏览者继承负责人会成环。
    await graph.getByRole('button', { name: name('浏览者'), exact: true }).click();
    await page.locator('.role-name').filter({ hasText: name('浏览者') }).waitFor();
    await page.getByLabel('被继承角色', { exact: true }).selectOption(String(manager.id));
    await page.getByRole('button', { name: '建立继承', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: '确认建立', exact: true }).click();
    const alert = page.getByRole('alert').filter({ hasText: '循环继承' });
    await alert.waitFor();
    const text = await alert.innerText();
    for (const part of ['浏览者', '负责人', '专员']) assert.ok(text.includes(name(part)), '环路应列出 ' + part);
    assert.equal(await edgesOf(reader.id), 0, '成环的继承不能写入');
    checks.push('循环继承给出明确提示并列出完整环路，且不产生关系');
    await s.shot(page, 'hierarchy-cycle');

    await page.goto(`${s.base}/ui/index.html#/effective?user=${member.id}`);
    await page.getByRole('heading', { level: 1, name: '有效权限' }).waitFor();
    await page.locator('.origin-row').filter({ hasText: name('查看业务列表') }).waitFor();
    assert.match(await page.locator('.origin-row').filter({ hasText: name('查看业务列表') }).innerText(), new RegExp(`继承[\\s\\S]*${name('负责人')}[\\s\\S]*${name('专员')}[\\s\\S]*${name('浏览者')}`));
    checks.push('用户有效权限列出经过的继承路径');
    await s.shot(page, 'effective');

    // 成员自己登录：只看到个人页面，权限来源可见；解除继承后刷新立即失去权限。
    const memberContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const mine = await memberContext.newPage();
    mine.on('pageerror', (e) => errors.push(e.message));
    await s.login(mine, member.username, memberPass);
    await mine.getByRole('heading', { level: 1, name: '我的权限' }).waitFor();
    await s.idle(mine);
    await mine.locator('.origin-row').filter({ hasText: name('查看业务列表') }).waitFor();
    await s.call(admin, `/api/roles/${manager.id}/inherits/${specialist.id}`, 'DELETE');
    await mine.getByRole('button', { name: '刷新权限' }).click();
    await s.idle(mine);
    await mine.locator('.origin-row').filter({ hasText: name('查看业务列表') }).waitFor({ state: 'detached' });
    checks.push('解除继承后同一已登录用户刷新即失去继承权限');
    await memberContext.close();
    assert.deepEqual(errors, []);
    await s.record('hierarchy-verification.json', { checks, errors });
  } finally {
    await browser.close();
  }
});
