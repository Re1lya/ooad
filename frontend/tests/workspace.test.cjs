/** FR-21/22：隔离演示实例的真实浏览器业务验收；不使用模拟 API。 */
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {chromium}=require('playwright-core');
const fs=require('node:fs/promises');
const path=require('node:path');
const base=process.env.PHASE_TWO_URL||'http://127.0.0.1:18082';
const out=process.env.PHASE_TWO_OUTPUT;
const password=process.env.PHASE_TWO_ADMIN_PASSWORD;
const username=process.env.PHASE_TWO_ADMIN_USERNAME;

// 显式指定隔离环境，避免在真实业务数据库执行新增和撤回测试。
test('FR-21/22 前端资源管理、继承、授权、个人来源与窄屏验收',{skip:process.env.PHASE_TWO_DEMO_ACK!=='isolated-h2'},async()=>{
 assert.ok(out&&password&&username,'需要设置演示账号及独立输出目录');
 await fs.mkdir(out,{recursive:true});
 const browser=await chromium.launch({executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
 const page=await context.newPage();const errors=[],checks=[];let bearer='';
 page.on('pageerror',e=>errors.push(e.message));
 async function api(route,method='GET',body){const response=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...(bearer?{Authorization:'Bearer '+bearer}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});assert.ok(response.ok,method+' '+route+' '+response.status);return response.status===204?null:response.json();}
 async function check(name,work){await work();checks.push(name);}
 async function nav(name){await page.getByRole('navigation',{name:'主导航'}).getByRole('button',{name,exact:false}).click();await page.getByRole('heading',{level:1,name:new RegExp('^'+name)}).waitFor();await page.locator('#main-content[aria-busy=false]').waitFor();}
 async function shot(name){if(await page.getByRole('button',{name:'关闭提示',exact:true}).isVisible().catch(()=>false))await page.getByRole('button',{name:'关闭提示',exact:true}).click();await page.locator('.loading-line').waitFor({state:'hidden'}).catch(()=>{});await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});}
 async function fillLogin(target,user,pass){await target.getByLabel('登录名',{exact:true}).fill(user);await target.getByLabel('密码',{exact:true}).fill(pass);await target.getByRole('button',{name:'登录',exact:true}).click();}
 async function confirm(){await page.getByRole('dialog').getByRole('button',{name:/^确认/}).click();await page.getByRole('dialog').waitFor({state:'hidden'});await page.locator('#main-content[aria-busy=false]').waitFor();}
 try{
  // 账号、组织和资源全部是该隔离演示内的合成数据。
  bearer=(await api('/api/auth/login','POST',{username,password})).accessToken;
  const suffix=Date.now().toString(36);const staffPass='Member-2026!Demo';
  const dept=await api('/api/departments','POST',{name:'技术管理部（演示）'});
  const d2=await api('/api/departments','POST',{name:'综合管理部（演示）'});
  const d3=await api('/api/departments','POST',{name:'项目管理部（演示）'});
  const app=await api('/api/systems','POST',{code:'course',name:'课程管理（鉴权示例）',baseUrl:'/api/demo/courses'});
  await api('/api/systems','POST',{code:'engineering_'+suffix,name:'工程协同（演示）'});
  await api('/api/systems','POST',{code:'expense_'+suffix,name:'财务报销（演示）'});
  const reader=await api('/api/roles','POST',{code:'reader_'+suffix,name:'业务浏览者',description:'查看业务资源的基础角色'});
  const specialist=await api('/api/roles','POST',{code:'specialist_'+suffix,name:'业务专员',description:'继承基础浏览能力'});
  const manager=await api('/api/roles','POST',{code:'manager_'+suffix,name:'部门负责人',description:'通过多级继承复用业务权限'});
  await api('/api/roles','POST',{code:'auditor_'+suffix,name:'审计观察员',description:'用于演示独立角色配置'});
  const menu=await api('/api/permissions','POST',{systemId:app.id,code:'course:menu',name:'业务工作台',type:'MENU',menuPath:'/workbench'});
  const button=await api('/api/permissions','POST',{systemId:app.id,code:'course:read',name:'查看业务列表',type:'BUTTON',parentId:menu.id});
  const resource=await api('/api/resources','POST',{systemId:app.id,name:'业务列表示例',method:'GET',path:'/api/demo/courses'});
  await api(`/api/roles/${reader.id}/permissions/${button.id}`,'PUT');
  await api(`/api/roles/${specialist.id}/inherits/${reader.id}`,'PUT');
  const staff=await api('/api/users','POST',{username:'demo-member-'+suffix,displayName:'演示成员',password:staffPass,departmentId:dept.id,personType:'EMPLOYEE'});
  await api(`/api/users/${staff.id}/roles/${manager.id}`,'PUT');
  for(let i=0;i<8;i++)await api('/api/users','POST',{username:'member-'+suffix+'-'+i,displayName:['示例成员甲','示例成员乙','示例成员丙','示例成员丁','示例成员戊','示例成员己','示例成员庚','示例成员辛'][i],password:staffPass,departmentId:[dept.id,d2.id,d3.id][i%3],personType:'EMPLOYEE'});
  await page.goto(base+'/ui/index.html');await page.getByRole('heading',{name:'登录工作区'}).waitFor();await shot('login');
  await check('错误密码有明确提示且不进入工作区',async()=>{await fillLogin(page,username,'wrong-password');await page.locator('.inline-error').waitFor();assert.equal(await page.getByRole('navigation').count(),0);});
  await fillLogin(page,username,password);await page.getByRole('heading',{name:'工作台',level:1}).waitFor();await page.locator('#main-content[aria-busy=false]').waitFor();await shot('overview');
  await check('工作台真实统计与快捷导航',async()=>{assert.ok((await page.locator('.metric-card').allTextContents()).some(t=>t.includes('业务角色')));assert.equal(await page.locator('.metric-card').count(),4);});
  await nav('用户管理');await shot('users');
  await check('资源列表筛选及搜索',async()=>{await page.getByRole('textbox',{name:'搜索当前列表'}).fill('no-such-member');await page.getByRole('heading',{name:'没有匹配的结果'}).waitFor();await page.getByRole('textbox',{name:'搜索当前列表'}).fill('演示成员');assert.equal(await page.locator('tbody tr').count(),1);await page.getByRole('textbox',{name:'搜索当前列表'}).fill('');});
  await check('新增弹窗取消不写入',async()=>{const before=(await api('/api/users?size=100')).total;await page.getByRole('button',{name:'新增用户',exact:true}).click();await page.getByRole('dialog').getByLabel('登录名',{exact:false}).fill('cancelled');await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();assert.equal((await api('/api/users?size=100')).total,before);});
  await check('前端创建与编辑角色',async()=>{await nav('角色管理');await page.getByRole('button',{name:'新增角色',exact:true}).click();const dialog=page.getByRole('dialog');await dialog.getByLabel('角色编码').fill('ui_'+suffix);await dialog.getByLabel('角色名称').fill('界面验收角色');await dialog.getByLabel('角色说明').fill('从真实界面创建');await dialog.getByRole('button',{name:'保存角色'}).click();await dialog.waitFor({state:'hidden'});await page.locator('#main-content[aria-busy=false]').waitFor();const row=page.getByRole('row').filter({hasText:'界面验收角色'});await row.getByRole('button',{name:'编辑',exact:true}).click();await dialog.getByLabel('角色名称').fill('界面验收角色已更新');await dialog.getByRole('button',{name:'保存角色'}).click();await dialog.waitFor({state:'hidden'});await page.getByRole('row').filter({hasText:'界面验收角色已更新'}).waitFor();});
  await check('停用取消与确认具有明确结果',async()=>{const row=page.getByRole('row').filter({hasText:'界面验收角色已更新'});await row.getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();assert.ok((await row.innerText()).includes('已启用'));await row.getByRole('button',{name:'停用',exact:true}).click();await confirm();assert.ok((await row.innerText()).includes('已停用'));});
  await nav('角色继承');await page.locator('.role-option').filter({hasText:'部门负责人'}).click();
  await check('建立继承前展示方向并支持取消',async()=>{await page.getByLabel('被继承角色',{exact:true}).selectOption(String(specialist.id));await page.getByRole('button',{name:'建立继承',exact:true}).click();await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();assert.equal((await api('/api/role-hierarchy')).length,1);await page.getByRole('button',{name:'建立继承',exact:true}).click();await confirm();await page.locator('.inherited-card').filter({hasText:'业务专员'}).waitFor();await page.locator('.origin-row').filter({hasText:'查看业务列表'}).waitFor();assert.ok((await page.locator('.origin-list').innerText()).includes('继承权限'));});
  await shot('hierarchy');
  await check('循环继承错误可理解且不产生关系',async()=>{await page.locator('.role-option').filter({hasText:'业务浏览者'}).click();await page.getByLabel('被继承角色',{exact:true}).selectOption(String(manager.id));await page.getByRole('button',{name:'建立继承',exact:true}).click();await confirm();await page.getByRole('alert').filter({hasText:'循环继承'}).waitFor();assert.equal((await api('/api/role-hierarchy')).length,2);});
  await nav('授权工作台');await page.getByRole('tab',{name:/权限 → API/}).click();
  await check('权限绑定API并显示直接关系',async()=>{await page.getByLabel('选择前端权限',{exact:true}).selectOption(String(button.id));await page.getByLabel('待绑定 API',{exact:true}).selectOption(String(resource.id));await page.getByRole('button',{name:'保存绑定',exact:true}).click();await page.locator('.assigned-row').filter({hasText:'业务列表示例'}).waitFor();});
  await page.getByRole('tab',{name:/用户 → 角色/}).click();await page.getByLabel('选择用户',{exact:true}).selectOption(String(staff.id));await page.locator('.assigned-row').filter({hasText:'部门负责人'}).waitFor();await shot('grants');
  const memberContext=await browser.newContext({viewport:{width:1440,height:1000}});const member=await memberContext.newPage();member.on('pageerror',e=>errors.push(e.message));await member.goto(base+'/ui/index.html');await fillLogin(member,staff.username,staffPass);await member.getByRole('heading',{name:'我的权限',level:1}).waitFor();await member.locator('#main-content[aria-busy=false]').waitFor();
  await check('普通用户仅有个人空间且看见继承来源',async()=>{assert.equal(await member.getByRole('navigation').getByRole('button',{name:'角色继承'}).count(),0);assert.ok((await member.locator('.origin-list').innerText()).includes('继承权限'));await member.getByRole('button',{name:'发送验证请求'}).click();await member.getByRole('status').filter({hasText:'接口鉴权通过'}).waitFor();assert.ok((await member.locator('pre').innerText()).includes('example'));});
  await member.getByRole('button',{name:'关闭提示',exact:true}).click();
  await member.screenshot({path:path.join(out,'my-permissions.png'),fullPage:true});
  await check('解除继承即时影响同一个已登录用户',async()=>{await nav('角色继承');await page.locator('.role-option').filter({hasText:'部门负责人'}).click();await page.getByRole('button',{name:'解除继承业务专员'}).click();await confirm();await member.getByRole('button',{name:'刷新权限'}).click();await member.locator('.empty').filter({hasText:'暂无有效权限'}).waitFor();await member.getByRole('button',{name:'发送验证请求'}).click();await member.getByRole('alert').filter({hasText:'没有此 API'}).waitFor();});
  // 恢复演示关系，供用户直接预览。
  await api(`/api/roles/${manager.id}/inherits/${specialist.id}`,'PUT');
  await check('窄屏导航可操作且页面无整体横向溢出',async()=>{await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'打开导航'}).click();await page.getByRole('navigation').getByRole('button',{name:'角色继承',exact:false}).click();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));await shot('mobile-hierarchy');await page.getByRole('button',{name:'打开导航'}).click();await page.getByRole('navigation').getByRole('button',{name:'用户管理',exact:true}).click();await page.locator('#main-content[aria-busy=false]').waitFor();assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));await shot('mobile-users');});
  await check('退出清除会话且重新载入仍需登录',async()=>{await member.getByRole('button',{name:'退出登录'}).click();await member.getByRole('heading',{name:'登录工作区'}).waitFor();assert.equal(await member.evaluate(()=>sessionStorage.getItem('permission-token')),null);await member.reload();await member.getByRole('heading',{name:'登录工作区'}).waitFor();});
  await memberContext.close();assert.deepEqual(errors,[]);checks.push('浏览器无未捕获JavaScript异常');
  await fs.writeFile(path.join(out,'browser-verification.json'),JSON.stringify({checks,errors,base,generatedAt:new Date().toISOString(),scope:'isolated H2 demo, synthetic records only'},null,2));
  console.log('Passed '+checks.length+' browser checks; screenshots saved.');
 }catch(error){await shot('failure');await fs.writeFile(path.join(out,'browser-failure.json'),JSON.stringify({checks,errors,message:error.message},null,2));throw error;}finally{await browser.close();}
});
