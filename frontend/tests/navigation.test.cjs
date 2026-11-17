/** FR-22：浏览器前进后退应还原页面，不产生额外历史条目。 */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const fs=require('node:fs/promises');
const path=require('node:path');
test('FR-22 浏览器后退和前进恢复正确页面',{skip:process.env.PHASE_TWO_DEMO_ACK!=='isolated-h2'},async()=>{
 const browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 try{
  const page=await browser.newPage();
  await page.goto((process.env.PHASE_TWO_URL||'http://127.0.0.1:18082')+'/ui/index.html');
  await page.getByLabel('登录名',{exact:true}).fill(process.env.PHASE_TWO_ADMIN_USERNAME);
  await page.getByLabel('密码',{exact:true}).fill(process.env.PHASE_TWO_ADMIN_PASSWORD);
  await page.getByRole('button',{name:'登录',exact:true}).click();
  const ready=async(name)=>{await page.getByRole('heading',{level:1,name:new RegExp('^'+name)}).waitFor();await page.locator('#main-content[aria-busy=false]').waitFor();};
  await ready('工作台');
  await page.getByRole('navigation').getByRole('button',{name:'用户管理',exact:true}).click();await ready('用户管理');
  await page.getByRole('navigation').getByRole('button',{name:'角色管理',exact:true}).click();await ready('角色管理');
  await page.goBack();await ready('用户管理');
  await page.goBack();
  await page.waitForTimeout(600);
  assert.match(await page.getByRole('heading',{level:1}).innerText(),/^工作台/,'连续后退两次应回到工作台');
  await page.goForward();await ready('用户管理');
  if(process.env.PHASE_TWO_OUTPUT)await fs.writeFile(path.join(process.env.PHASE_TWO_OUTPUT,'navigation-verification.json'),JSON.stringify({generatedAt:new Date().toISOString(),checks:['连续后退回到工作台','前进还原用户管理'],errors:[]},null,2));
 }finally{await browser.close();}
});
