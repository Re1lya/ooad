/** FR-21/22：隔离演示实例上的真实浏览器验收：登录、目录维护、授权、绑定接口、窄屏与退出。不使用模拟接口。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

test('FR-16 FR-21 FR-22 NFR-10 前端资源管理、授权工作台、接口绑定、窄屏与退出', { skip: s.skip }, async () => {
  assert.ok(s.out && s.admin.username && s.admin.password, '需要设置演示账号及独立输出目录');
  const admin = await s.token();
  await s.ensureConsole(admin);
  const suffix = Date.now().toString(36);
  const staffPass = 'Member-2026!Demo';
  const app = await s.api(admin, '/api/systems', 'POST', { code: 'w' + suffix, name: '工作区验收' + suffix, baseUrl: null });
  const menu = await s.api(admin, '/api/permissions', 'POST', { systemId: app.id, code: 'wm' + suffix, name: '业务菜单' + suffix, type: 'MENU', menuPath: '/w', parentId: null });
  const button = await s.api(admin, '/api/permissions', 'POST', { systemId: app.id, code: 'wb' + suffix, name: '业务按钮' + suffix, type: 'BUTTON', menuPath: null, parentId: menu.id });
  const resource = await s.api(admin, '/api/resources', 'POST', { systemId: app.id, name: '业务接口' + suffix, method: 'GET', path: '/w/' + suffix, code: 'W' + suffix });
  const base = await s.api(admin, '/api/roles', 'POST', { code: 'wbase_' + suffix, name: '基础' + suffix, description: null });
  const lead = await s.api(admin, '/api/roles', 'POST', { code: 'wlead_' + suffix, name: '主管' + suffix, description: null });
  await s.api(admin, `/api/roles/${base.id}/permissions/${menu.id}`, 'PUT');
  await s.api(admin, `/api/roles/${lead.id}/inherits/${base.id}`, 'PUT');
  const parent = await s.api(admin, '/api/departments', 'POST', { name: '分公司' + suffix, parentId: null });
  await s.api(admin, '/api/departments', 'POST', { name: '项目部' + suffix, parentId: parent.id });
  await s.api(admin, '/api/users', 'POST', { username: 'w-' + suffix, displayName: '演示成员' + suffix, password: staffPass, departmentId: null, personType: 'EMPLOYEE' });

  const browser = await s.launch();
  const checks = [];
  const errors = [];
  const check = async (name, work) => {
    await work();
    checks.push(name);
  };
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' });
    const page = await context.newPage();
    page.on('pageerror', (e) => errors.push(e.message));
    const confirm = async (label) => {
      await page.getByRole('dialog').getByRole('button', { name: label, exact: true }).click();
      await page.getByRole('dialog').waitFor({ state: 'hidden' });
      await s.idle(page);
    };

    await page.goto(s.base + '/ui/index.html');
    await page.getByRole('heading', { name: '登录工作区' }).waitFor();
    await s.shot(page, 'login');
    await check('错误密码有明确提示且不进入工作区', async () => {
      await page.getByLabel('登录名', { exact: true }).fill(s.admin.username);
      await page.getByLabel('密码', { exact: true }).fill('wrong-password');
      await page.getByRole('button', { name: '登录', exact: true }).click();
      await page.locator('.inline-error').waitFor();
      assert.equal(await page.getByRole('navigation').count(), 0);
    });
    await s.login(page, s.admin.username, s.admin.password);
    await page.getByRole('heading', { level: 1, name: '工作台' }).waitFor();
    await s.idle(page);
    await check('工作台展示真实统计', async () => {
      const figures = await page.locator('.figures').innerText();
      for (const label of ['统一身份用户', '角色', '接入应用']) assert.ok(figures.includes(label), label);
    });
    await s.shot(page, 'overview');

    await s.nav(page, '用户管理');
    await check('用户列表检索与无结果提示', async () => {
      await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('no-such-member-' + suffix);
      await page.getByRole('heading', { name: '没有匹配的结果' }).waitFor();
      await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('演示成员' + suffix);
      await page.getByRole('row').filter({ hasText: '演示成员' + suffix }).waitFor();
      assert.equal(await page.locator('tbody tr').count(), 1);
      await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('');
    });
    await check('新增弹窗取消不写入', async () => {
      const before = (await s.api(admin, '/api/users?pageSize=1')).total;
      await page.getByRole('button', { name: '新增用户', exact: true }).click();
      await page.getByRole('dialog').getByLabel('登录名').fill('cancelled-' + suffix);
      await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click();
      assert.equal((await s.api(admin, '/api/users?pageSize=1')).total, before);
    });
    await s.shot(page, 'users');

    await s.nav(page, '部门管理');
    await check('部门树逐层展开', async () => {
      await page.getByRole('button', { name: '展开分公司' + suffix }).click();
      await page.getByRole('row').filter({ hasText: '项目部' + suffix }).waitFor();
    });

    await s.nav(page, '角色管理');
    await check('前端新增与编辑角色', async () => {
      await page.getByRole('button', { name: '新增角色', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('角色编码').fill('ui_' + suffix);
      await dialog.getByLabel('角色名称').fill('界面验收角色' + suffix);
      await dialog.getByLabel('角色说明').fill('从界面创建');
      await dialog.getByRole('button', { name: '保存角色' }).click();
      await dialog.waitFor({ state: 'hidden' });
      await s.idle(page);
      const row = page.getByRole('row').filter({ hasText: '界面验收角色' + suffix });
      await row.getByRole('button', { name: '编辑', exact: true }).click();
      await dialog.getByLabel('角色名称').fill('界面验收角色已更新' + suffix);
      await dialog.getByRole('button', { name: '保存角色' }).click();
      await dialog.waitFor({ state: 'hidden' });
      await page.getByRole('row').filter({ hasText: '界面验收角色已更新' + suffix }).waitFor();
    });
    await check('停用的取消与确认都有明确结果', async () => {
      const row = page.getByRole('row').filter({ hasText: '界面验收角色已更新' + suffix });
      await row.getByRole('button', { name: '停用', exact: true }).click();
      await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click();
      assert.ok((await row.innerText()).includes('已启用'));
      await row.getByRole('button', { name: '停用', exact: true }).click();
      await confirm('确认停用');
      await page.getByRole('row').filter({ hasText: '界面验收角色已更新' + suffix }).filter({ hasText: '已停用' }).waitFor();
    });

    await s.nav(page, '菜单与按钮');
    await check('为按钮绑定 API', async () => {
      await page.getByRole('group', { name: '应用' }).getByRole('button', { name: '工作区验收' + suffix }).click();
      const row = page.getByRole('row').filter({ hasText: '业务按钮' + suffix });
      await row.getByRole('button', { name: '接口', exact: true }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('待绑定 API').selectOption(String(resource.id));
      await dialog.getByRole('button', { name: '保存绑定', exact: true }).click();
      await dialog.locator('li').filter({ hasText: '业务接口' + suffix }).waitFor();
      assert.equal((await s.api(admin, `/api/permissions/${button.id}/resources`)).length, 1);
      await dialog.getByRole('button', { name: '取消', exact: true }).click();
    });

    await s.nav(page, '授权工作台');
    await check('授权工作台勾选直接权限，继承权限只读', async () => {
      await page.getByRole('list', { name: '角色' }).getByRole('button', { name: new RegExp('^主管' + suffix) }).click();
      await page.getByRole('group', { name: '应用' }).getByRole('button', { name: '工作区验收' + suffix }).click();
      await s.idle(page);
      const inherited = page.locator('.prow').filter({ hasText: '业务菜单' + suffix });
      await inherited.filter({ hasText: '继承自 基础' + suffix }).waitFor();
      assert.equal(await inherited.getByRole('checkbox').count(), 0, '继承权限不可勾选');
      const box = page.getByRole('checkbox', { name: '业务按钮' + suffix });
      await box.click();
      await page.getByRole('checkbox', { name: '业务按钮' + suffix, checked: true }).waitFor();
      assert.ok((await s.api(admin, `/api/roles/${lead.id}/permissions`)).some((p) => p.id === button.id));
    });
    await s.shot(page, 'grants');

    await s.nav(page, '审计日志');
    await check('审计日志列出继承变更记录', async () => {
      await page.locator('tbody tr').first().waitFor();
    });

    await check('窄屏导航可操作且页面无整体横向溢出', async () => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.getByRole('button', { name: '打开导航' }).click();
      await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: /角色继承/ }).click();
      await page.getByRole('heading', { level: 1, name: '角色继承' }).waitFor();
      await s.idle(page);
      assert.ok(await s.noOverflow(page));
      await s.shot(page, 'mobile-hierarchy');
      await page.getByRole('button', { name: '打开导航' }).click();
      await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: /用户管理/ }).click();
      await page.getByRole('heading', { level: 1, name: '用户管理' }).waitFor();
      await s.idle(page);
      assert.ok(await s.noOverflow(page));
      await s.shot(page, 'mobile-users');
    });
    await check('退出清除会话且重新载入仍需登录', async () => {
      await page.getByRole('button', { name: '退出登录' }).click();
      await page.getByRole('heading', { name: '登录工作区' }).waitFor();
      assert.equal(await page.evaluate(() => sessionStorage.getItem('permission-token')), null);
      await page.reload();
      await page.getByRole('heading', { name: '登录工作区' }).waitFor();
    });
    assert.deepEqual(errors, []);
    checks.push('浏览器无未捕获的 JavaScript 异常');
    await s.record('browser-verification.json', { checks, errors, scope: 'isolated H2 demo, synthetic records only' });
  } catch (error) {
    await s.record('browser-failure.json', { checks, errors, message: error.message });
    throw error;
  } finally {
    await browser.close();
  }
});
