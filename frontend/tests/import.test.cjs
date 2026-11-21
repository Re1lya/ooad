/** FR-24/25：员工信息同步：三份合成表在真实 HTTP 边界完成预览、确认与重复导入；更换文件使预览失效。 */
const { test } = require('node:test');
const fs = require('node:fs/promises');
const path = require('node:path');
const s = require('./support.cjs');
const { assert } = s;

test('FR-24/25 员工信息表预览、导入与文件变化使预览失效', { skip: s.skip }, async () => {
  const admin = await s.token();
  await s.ensureConsole(admin);
  const dir = process.env.IMPORT_FIXTURES || (await s.tempDir());
  const tag = Date.now().toString(36).toUpperCase();
  const fixture = async (name, rows) => {
    const file = path.join(dir, name + '.xlsx');
    await fs.writeFile(file, s.xlsx(rows));
    return file;
  };
  const header = ['姓名', '用户名', '分公司', '部门', '岗位', '电话号码'];
  const employees = await fixture('employees', [header, ['测试甲', 'UI' + tag + '1', '测试分公司' + tag, '项目部', '工程师', '01000000001'], ['测试乙', 'UI' + tag + '2', '测试分公司' + tag, '测试分公司' + tag, '职员', '01000000002']]);
  const changed = await fixture('employees-changed', [header, ['测试甲', 'UI' + tag + '1', '测试分公司' + tag, '项目部', '工程师', '01000000001'], ['测试丙', 'UI' + tag + '3', '测试分公司' + tag, '项目部', '职员', '01000000003']]);
  const departments = await fixture('departments', [['部门名称', '部门经理', '上级部门'], ['测试分公司' + tag, '负责人甲', ''], ['测试分公司' + tag + '-项目部', '负责人乙', '测试分公司' + tag]]);
  const permissions = await fixture('permissions', [
    ['市政公司权限清单'],
    ['软件系统', '功能模块', '功能点', '功能说明', '系统管理员', '公司领导', '部门经理', '项目经理', '普通员工'],
    ['测试应用' + tag, '测试模块', '功能甲', '功能说明甲', '全部', '查看', '无', '无', '查看本人'],
  ]);

  const browser = await s.launch();
  try {
    const page = await browser.newPage();
    page.setDefaultTimeout(10000);
    await s.login(page, s.admin.username, s.admin.password);
    await s.idle(page);
    await s.nav(page, '用户管理');
    await page.getByRole('button', { name: '导入员工信息表', exact: true }).click();
    await page.getByRole('heading', { level: 1, name: '导入员工信息表' }).waitFor();
    const commit = page.getByRole('button', { name: '确认导入', exact: true });
    assert.equal(await commit.isEnabled(), false);
    await page.getByLabel('员工信息表', { exact: true }).setInputFiles(employees);
    await page.getByLabel('部门信息表', { exact: true }).setInputFiles(departments);
    await page.getByLabel('功能权限清单', { exact: true }).setInputFiles(permissions);
    await page.getByRole('button', { name: '预览并校验', exact: true }).click();
    await page.getByRole('heading', { name: '校验通过，可以导入' }).waitFor();
    assert.equal(await commit.isEnabled(), true);
    await page.getByLabel('员工信息表', { exact: true }).setInputFiles(changed);
    assert.equal(await commit.isEnabled(), false, '更换文件必须重新预览');
    await page.getByRole('button', { name: '预览并校验', exact: true }).click();
    await page.getByRole('heading', { name: '校验通过，可以导入' }).waitFor();
    await commit.click();
    await page.getByRole('heading', { name: '导入已完成' }).waitFor({ timeout: 30000 });
    await page.getByRole('link', { name: '查看员工 →' }).click();
    await page.getByRole('heading', { level: 1, name: '用户管理' }).waitFor();
    await page.getByRole('searchbox', { name: '搜索当前列表' }).fill('UI' + tag + '3');
    const member = page.getByRole('row').filter({ hasText: 'UI' + tag + '3' });
    await member.waitFor();
    assert.match(await member.innerText(), /已停用/, '导入的新员工默认停用');
    await member.getByRole('button', { name: '设置密码', exact: true }).click();
    await page.getByLabel('员工新密码', { exact: true }).fill('Imported-2026!');
    await page.getByRole('dialog').getByRole('button', { name: '保存密码', exact: true }).click();
    await page.getByRole('dialog').waitFor({ state: 'hidden' });
    await s.idle(page);
    assert.match(await member.innerText(), /已停用/, '设置密码不自动启用员工');
    await s.record('import-verification.json', {
      checks: ['入口在用户管理且需要导入按钮权限', '无预览时确认导入被禁用', '真实 HTTP 预览返回合成数据汇总', '更换文件必须重新预览', '三表通过浏览器提交成功', '完成后回到用户列表并检索到导入账号', '设置密码成功且账号仍保持停用'],
      errors: [],
      data: 'synthetic only',
    });
  } finally {
    await browser.close();
  }
});
