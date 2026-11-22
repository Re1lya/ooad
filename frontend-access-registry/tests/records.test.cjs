/** FR-03/04/05/09、FR-22：在册人员、部门编码、API 资源的登记与修改都经权限台账界面完成。 */
const { test } = require('node:test');
const s = require('./support.cjs');
const { assert } = s;

test('FR-03 FR-04 FR-22 登记外部人员须填有效期，部门显示完整路径；注销前二次确认，取消不写入', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix();
  const company = await s.api(admin, '/api/departments', 'POST', { name: '外协分公司' + tag, parentId: null, code: null });
  const office = await s.api(admin, '/api/departments', 'POST', { name: '综合办公室', parentId: company.id, code: null });
  const username = 'ext-' + tag;
  const due = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);

  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, '用户');
    await page.getByRole('button', { name: '登记用户' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByPlaceholder('工号或学号').fill(username);
    await dialog.getByPlaceholder('显示姓名').fill('外协' + tag);
    await dialog.getByPlaceholder('符合密码策略').fill('External-2026!Demo');
    await dialog.locator('select').first().selectOption('EXTERNAL');
    const save = dialog.getByRole('button', { name: '落账' });
    assert.ok(await save.isDisabled(), '外部人员未填有效期时不能落账');
    await dialog.locator('input[type=date]').fill(due);
    const departmentLabel = `外协分公司${tag} / 综合办公室`;
    await dialog.locator('select').nth(1).selectOption({ label: departmentLabel });
    await save.click();
    await dialog.waitFor({ state: 'detached' });
    await page.settle();

    await page.getByPlaceholder('搜账号或姓名…').fill(username);
    await page.getByRole('button', { name: '查', exact: true }).click();
    await page.settle();
    const created = s.row(page, username);
    assert.match(await created.innerText(), /外部人员/);
    assert.match(await created.innerText(), new RegExp(due));
    assert.match(await created.innerText(), new RegExp(departmentLabel.replace(/[/]/g, '\\/')));
    const stored = (await s.api(admin, `/api/users?keyword=${username}&page=1&pageSize=20`)).items[0];
    assert.equal(stored.personType, 'EXTERNAL');
    assert.equal(stored.departmentId, office.id);

    await created.getByRole('button', { name: '销' }).click();
    await s.answer(page, false);
    assert.equal((await s.api(admin, `/api/users?keyword=${username}&page=1&pageSize=20`)).total, 1, '取消不应注销');
    await created.getByRole('button', { name: '销' }).click();
    await s.answer(page, true, '注销');
    assert.equal((await s.api(admin, `/api/users?keyword=${username}&page=1&pageSize=20`)).total, 0, '确认后注销');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});

test('FR-05 部门编码：新建留空按编号生成，修改时可改编码', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix();
  const name = '测试分公司' + tag;
  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, '部门');
    await page.getByRole('button', { name: '建一级部门' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByPlaceholder('同一上级下不能重名').fill(name);
    await dialog.getByRole('button', { name: '落账' }).click();
    await dialog.waitFor({ state: 'detached' });
    await page.settle();
    const created = (await s.api(admin, '/api/departments')).find((d) => d.name === name);
    assert.match(created.code, /^D\d{4,}$/, '留空时按编号生成编码');
    const node = page.locator('.dept-row', { hasText: name });
    await node.getByText(created.code).waitFor();

    await node.getByRole('button', { name: '改' }).click();
    await dialog.getByPlaceholder('留空则保留原编码').fill('BR-' + tag.toUpperCase());
    await dialog.getByRole('button', { name: '落账' }).click();
    await dialog.waitFor({ state: 'detached' });
    await page.settle();
    await node.getByText('BR-' + tag.toUpperCase()).waitFor();
    assert.equal((await s.api(admin, '/api/departments')).find((d) => d.id === created.id).code, 'BR-' + tag.toUpperCase());
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});

test('FR-09 登记 API 资源时权限码与 operationId 可选，列表按 operationId 检索', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix();
  const app = await s.api(admin, '/api/systems', 'POST', { code: 'res-' + tag, name: '资源验收应用' + tag, baseUrl: null });
  const operationId = 'listOrders' + tag;
  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, 'API 资源');
    await page.getByRole('button', { name: '登记资源' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('select').first().selectOption({ label: app.name });
    await dialog.locator('select').nth(1).selectOption('PATCH');
    await dialog.getByPlaceholder('如 /teachers/{teacherId}，不含查询参数').fill('/orders/{orderId}');
    await dialog.getByPlaceholder('如 createTeacher，可留空').fill(operationId);
    await dialog.getByPlaceholder('这个接口干什么用').fill('修改订单');
    await dialog.getByRole('button', { name: '落账' }).click();
    await dialog.waitFor({ state: 'detached' });
    await page.settle();

    await page.getByPlaceholder('搜路径、权限码或 operationId…').fill(operationId);
    const found = s.row(page, '/orders/{orderId}');
    await found.getByText(operationId).waitFor();
    assert.equal(await page.locator('.ledger tbody tr').count(), 1, '按 operationId 检索只剩这一条');
    const stored = (await s.api(admin, '/api/resources')).find((r) => r.operationId === operationId);
    assert.equal(stored.method, 'PATCH');
    assert.equal(stored.code, null, '权限码留空');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});
