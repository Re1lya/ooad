/** FR-24/25：合成三表在真实 HTTP 边界完成预览、确认和重复导入。 */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const path=require('node:path');
test('FR-24/25 三表预览、导入与文件变化使预览失效',{skip:process.env.PHASE_TWO_DEMO_ACK!=='isolated-h2'},async()=>{
 const browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 try{
  const page=await browser.newPage();page.setDefaultTimeout(7000);
  await page.goto((process.env.PHASE_TWO_URL||'http://127.0.0.1:18082')+'/ui/index.html');
  await page.getByLabel('登录名',{exact:true}).fill(process.env.PHASE_TWO_ADMIN_USERNAME);
  await page.getByLabel('密码',{exact:true}).fill(process.env.PHASE_TWO_ADMIN_PASSWORD);
  await page.getByRole('button',{name:'登录',exact:true}).click();
  await page.locator('#main-content[aria-busy=false]').waitFor();
  await page.getByRole('navigation').getByRole('button',{name:'数据导入',exact:true}).click();
  const commit=page.getByRole('button',{name:'确认导入',exact:true});assert.equal(await commit.isEnabled(),false);
  for(const [name,label] of [['employees','员工信息表'],['departments','部门信息表'],['permissions','功能权限清单']])await page.getByLabel(label,{exact:true}).setInputFiles(path.resolve(process.env.IMPORT_FIXTURES,name+'.xlsx'));
  await page.getByRole('button',{name:'预览并校验',exact:true}).click();
  await page.getByRole('heading',{name:'校验通过，可以导入'}).waitFor();
  assert.equal(await commit.isEnabled(),true);
  await page.getByLabel('员工信息表',{exact:true}).setInputFiles(path.resolve(process.env.IMPORT_FIXTURES,'employees-changed.xlsx'));
  assert.equal(await commit.isEnabled(),false,'更换文件必须重新预览');
  await page.getByRole('button',{name:'预览并校验',exact:true}).click();
  await page.getByRole('heading',{name:'校验通过，可以导入'}).waitFor();
  await commit.click();await page.getByRole('heading',{name:'导入已完成'}).waitFor({timeout:30000});
  await page.getByRole('button',{name:'查看员工',exact:true}).click();await page.locator('#main-content[aria-busy=false]').waitFor();
  assert.match(await page.locator('main').innerText(),/成员/);
  const member=page.getByRole('row').filter({hasText:'UIIMPORT002'});
  for(let i=0;i<20&&!(await member.count());i++){const next=page.getByRole('button',{name:'下一页',exact:true});if(!await next.isEnabled())break;await next.click();await page.locator('#main-content[aria-busy=false]').waitFor();}
  await member.getByRole('button',{name:'设置密码',exact:true}).click();
  await page.getByLabel('员工新密码',{exact:true}).fill('Imported-2026!');
  await page.getByRole('dialog').getByRole('button',{name:'保存密码',exact:true}).click();
  await page.getByRole('dialog').waitFor({state:'hidden'});
  await page.locator('#main-content[aria-busy=false]').waitFor();
  assert.match(await member.innerText(),/已停用/,'设置密码不自动启用员工');
  if(process.env.PHASE_TWO_OUTPUT){const fs=require('node:fs/promises');await fs.writeFile(path.join(process.env.PHASE_TWO_OUTPUT,'import-verification.json'),JSON.stringify({generatedAt:new Date().toISOString(),checks:['管理员导入入口和三份文件选择可用','无预览时确认导入被禁用','真实HTTP预览返回合成数据汇总','更换文件必须重新预览','三表通过浏览器提交成功','完成后跳转用户列表并找到导入账号','设置密码成功且账号仍保持停用'],errors:[],data:'synthetic only'},null,2));}
 }finally{await browser.close();}
});
