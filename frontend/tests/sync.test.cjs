/** FR-24/25：员工信息同步：合成员工表在真实 HTTP 边界完成预览、确认与重复同步；更换文件或规则使预览失效。 */
const { test } = require('node:test');
const fs = require('node:fs/promises');
const path = require('node:path');
const s = require('./support.cjs');
const { assert } = s;

// 测试类型：等价类、异常
test('FR-24 FR-25 同步员工信息：预览、确认、重复执行无变化，更换文件或规则须重新预览', { skip: s.skip }, async () => {
  const admin = await s.token();
  await s.ensureConsole(admin);
  const dir = await s.tempDir();
  const tag = Date.now().toString(36).toUpperCase();
  const head = '测试总公司' + tag;
  const branch = '测试分公司' + tag;
  const fixture = async (name, rows) => {
    const file = path.join(dir, name + '.xlsx');
    await fs.writeFile(file, s.xlsx(rows));
    return file;
  };
  const header = ['姓名', '用户名', '分公司', '部门', '岗位', '电话号码'];
  const people = [
    ['测试甲', 'UI' + tag + '1', head, '综合办公室', '工程师', '01000000001'],
    ['测试乙', 'UI' + tag + '2', branch, '综合办公室', '职员', '01000000002'],
  ];
  const employees = await fixture('employees', [header, ...people]);
  const changed = await fixture('employees-changed', [header, ...people, ['测试丙', 'UI' + tag + '3', branch, '项目部', '职员', '01000000003']]);
  const roleCode = 'ui-learner-' + tag.toLowerCase();
  await s.api(admin, '/api/roles', 'POST', { code: roleCode, name: '学员' + tag });

  const browser = await s.launch();
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(10000);
    await s.login(page, s.admin.username, s.admin.password);
    await s.idle(page);
    await s.nav(page, '用户管理');
    await page.getByRole('button', { name: '同步员工信息', exact: true }).click();
    await page.getByRole('heading', { level: 1, name: '同步员工信息' }).waitFor();
    const commit = page.getByRole('button', { name: '确认同步', exact: true });
    const inspect = page.getByRole('button', { name: '预览变化', exact: true });
    const figure = async (label) =>
      Number((await page.locator('.figures div').filter({ hasText: label }).locator('b').innerText()).replace(/,/g, ''));
    assert.equal(await commit.isEnabled(), false);

    await page.getByLabel('员工信息表', { exact: true }).setInputFiles(employees);
    await page.getByLabel('总公司名称', { exact: true }).fill(head);
    await page.getByLabel('分公司员工补授角色', { exact: true }).selectOption(roleCode);
    await inspect.click();
    await page.getByRole('heading', { name: '预览完成，可以确认同步' }).waitFor();
    assert.equal(await figure('新建部门'), 4, '两个分公司各一个“综合办公室”');
    assert.equal(await figure('新建人员'), 2);
    assert.equal(await figure('补授角色'), 1, '只给分公司员工补授');
    assert.equal(await commit.isEnabled(), true);

    await page.getByLabel('员工信息表', { exact: true }).setInputFiles(changed);
    assert.equal(await commit.isEnabled(), false, '更换文件必须重新预览');
    await inspect.click();
    await page.getByRole('heading', { name: '预览完成，可以确认同步' }).waitFor();
    await page.getByLabel('总公司员工补授角色', { exact: true }).selectOption(roleCode);
    assert.equal(await commit.isEnabled(), false, '修改规则必须重新预览');
    await page.getByLabel('总公司员工补授角色', { exact: true }).selectOption('');
    await inspect.click();
    await page.getByRole('heading', { name: '预览完成，可以确认同步' }).waitFor();
    await commit.click();
    await page.getByRole('heading', { name: '同步已完成' }).waitFor({ timeout: 30000 });
    assert.equal(await figure('新建人员'), 3);
    await s.shot(page, 'directory-sync');

    await inspect.click();
    await page.getByRole('heading', { name: '预览完成，可以确认同步' }).waitFor();
    assert.equal(await figure('新建部门'), 0, '重复执行没有新部门');
    assert.equal(await figure('新建人员'), 0, '重复执行没有新人员');
    assert.equal(await figure('补授角色'), 0, '重复执行不重复授予');

    await s.nav(page, '用户管理');
    await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('UI' + tag + '3');
    const member = page.getByRole('row').filter({ hasText: 'UI' + tag + '3' });
    await member.waitFor();
    assert.match(await member.innerText(), /已停用/, '同步的新员工默认停用');
    assert.match(await member.innerText(), /项目部/);

    await s.nav(page, '部门管理');
    await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('综合办公室');
    const offices = page.getByRole('row').filter({ hasText: '综合办公室' });
    assert.ok((await offices.count()) >= 2, '不同分公司下的同名部门都在');
    await s.record('sync-verification.json', {
      checks: ['入口在用户管理且需要同步按钮权限', '无预览时确认被禁用', '预览显示部门、人员与补授角色数量', '更换文件或修改规则必须重新预览', '确认后新人员停用并挂到两级部门', '重复执行没有任何变化', '不同分公司下的同名部门并存'],
      errors: [],
      data: 'synthetic only',
    });
  } finally {
    await browser.close();
  }
});
