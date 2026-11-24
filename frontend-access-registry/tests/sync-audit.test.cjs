/** FR-24/25：员工同步经界面预览、确认、重复执行无变化；FR-30：审计日志按来源查看，类型与对象写中文。 */
const { test } = require('node:test');
const fs = require('node:fs/promises');
const path = require('node:path');
const s = require('./support.cjs');
const { assert } = s;

/** 预览或写入后的摘要：标签 → 数字。 */
async function summary(page) {
  const out = {};
  for (const item of await page.locator('.sum').all())
    out[(await item.locator('.sum__label').innerText()).trim()] = Number(await item.locator('.sum__num').innerText());
  return out;
}

// 测试类型：等价类
test('FR-24 FR-25 员工同步：预览变动、确认写入，同一张表再预览没有变化', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix().toUpperCase();
  const head = '测试总公司' + tag;
  const branch = '测试分公司' + tag;
  const teacher = await s.api(admin, '/api/roles', 'POST', { code: 'sync-t-' + tag.toLowerCase(), name: '同步教师' + tag });
  const learner = await s.api(admin, '/api/roles', 'POST', { code: 'sync-s-' + tag.toLowerCase(), name: '同步学员' + tag });
  const dir = await s.tempDir();
  const file = path.join(dir, 'employees.xlsx');
  await fs.writeFile(
    file,
    s.xlsx([
      ['姓名', '用户名', '分公司', '部门', '岗位', '电话号码'],
      ['测试甲', 'AR' + tag + '1', head, '综合办公室', '工程师', '01000000001'],
      ['测试乙', 'AR' + tag + '2', branch, '项目部', '职员', '01000000002'],
    ]),
  );

  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, '员工同步');
    await page.locator('input[type=file]').setInputFiles(file);
    await page.getByPlaceholder('如 总公司').fill(head);
    await page.locator('select').nth(0).selectOption(teacher.code);
    await page.locator('select').nth(1).selectOption(learner.code);
    await page.getByRole('button', { name: '预览变动' }).click();
    await page.getByRole('button', { name: '确认写入' }).waitFor();
    const preview = await summary(page);
    assert.equal(preview['新建用户'], 2);
    assert.equal(preview['补授角色'], 2);
    await page.getByRole('button', { name: '确认写入' }).click();
    await page.getByText('台账已更新，可以重复执行').waitFor();
    const created = (await s.api(admin, `/api/users?keyword=AR${tag}&page=1&pageSize=20`)).items;
    assert.equal(created.length, 2);
    assert.ok(created.every((u) => !u.enabled), '新人员默认停用');

    await page.getByRole('button', { name: '预览变动' }).click();
    await page.getByRole('button', { name: '确认写入' }).waitFor();
    const again = await summary(page);
    assert.equal(again['新建用户'], 0);
    assert.equal(again['未变动'], 2, '同一张表再同步没有变化');
    await s.shot(page, 'registry-directory-sync');
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});

// 测试类型：等价类
test('FR-30 审计日志默认看权限系统自己的记录，类型与对象写中文', { skip: s.skip }, async () => {
  const admin = await s.token();
  const tag = s.suffix();
  const senior = await s.api(admin, '/api/roles', 'POST', { code: 'aud-a-' + tag, name: '审计上级' + tag });
  const junior = await s.api(admin, '/api/roles', 'POST', { code: 'aud-b-' + tag, name: '审计下级' + tag });
  await s.api(admin, `/api/roles/${senior.id}/inherits/${junior.id}`, 'PUT');

  const browser = await s.launch();
  try {
    const { page, errors } = await s.open(browser);
    await s.login(page, s.admin.username, s.admin.password);
    await s.nav(page, '审计日志');
    assert.equal(await page.locator('select').first().inputValue(), 'rbac', '默认来源是权限系统自己');
    await page.getByPlaceholder('搜操作目标…').fill('审计上级' + tag);
    await page.getByRole('button', { name: '查', exact: true }).click();
    await page.settle();
    const entry = s.row(page, '审计上级' + tag);
    await entry.waitFor();
    assert.match(await entry.innerText(), /建立继承/);
    assert.match(await entry.innerText(), /角色/);
    await entry.getByRole('button', { name: '看' }).click();
    await page.getByRole('dialog').getByText('改动后').waitFor();
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
  }
});
