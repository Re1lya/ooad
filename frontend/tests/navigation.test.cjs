/** FR-22：浏览器前进后退还原页面，不产生额外历史条目。 */
const { test } = require('node:test');
const s = require('./support.cjs');

// 测试类型：等价类
test('FR-22 浏览器后退和前进恢复正确页面', { skip: s.skip }, async () => {
  await s.ensureConsole(await s.token());
  const browser = await s.launch();
  try {
    const page = await browser.newPage();
    await s.login(page, s.admin.username, s.admin.password);
    const ready = async (name) => {
      await page.getByRole('heading', { level: 1, name: new RegExp('^' + name) }).waitFor();
      await s.idle(page);
    };
    await ready('工作台');
    await s.nav(page, '用户管理');
    await s.nav(page, '角色管理');
    await page.goBack();
    await ready('用户管理');
    await page.goBack();
    await ready('工作台');
    await page.goForward();
    await ready('用户管理');
    await s.record('navigation-verification.json', { checks: ['连续后退回到工作台', '前进还原用户管理'], errors: [] });
  } finally {
    await browser.close();
  }
});
