import { reactive } from 'vue';

export type Row = Record<string, any>;
export type Field = { key: string; label: string; type?: string; optional?: boolean; choices?: [string, string][] };
export type Screen = { title: string; noun: string; caption: string; icon: string; fields: Field[]; columns: [string, string][] };
export const personChoices: [string,string][] = [['EMPLOYEE','正式员工'],['EXTERNAL','外部人员'],['TEMPORARY','临时人员']];
export const screens: Record<string, Screen> = {
 users: {title:'用户管理',noun:'用户',icon:'users',caption:'让每一位成员，拥有恰当的访问权限。',fields:[{key:'username',label:'登录名'},{key:'displayName',label:'姓名'},{key:'password',label:'初始密码',type:'password'},{key:'departmentId',label:'所属部门',type:'department',optional:true},{key:'personType',label:'人员身份',choices:personChoices},{key:'expiresOn',label:'有效期至',type:'date',optional:true}],columns:[['displayName','成员'],['personType','身份类型'],['departmentId','所属部门'],['enabled','账号状态']]},
 departments:{title:'部门管理',noun:'部门',icon:'organization',caption:'以清晰的组织关系，维护成员归属。',fields:[{key:'name',label:'部门名称'},{key:'parentId',label:'上级部门',type:'department',optional:true}],columns:[['name','部门名称'],['parentId','上级部门'],['managerName','部门经理']]},
 systems:{title:'应用管理',noun:'应用',icon:'grid',caption:'将各个业务应用的权限资源，纳入统一目录。',fields:[{key:'code',label:'应用编码'},{key:'name',label:'应用名称'},{key:'baseUrl',label:'应用地址',optional:true}],columns:[['name','应用名称'],['code','应用编码'],['baseUrl','访问地址'],['enabled','运行状态']]},
 roles:{title:'角色管理',noun:'角色',icon:'shield',caption:'用角色组织权限，让授权更加可维护。',fields:[{key:'code',label:'角色编码'},{key:'name',label:'角色名称'},{key:'description',label:'角色说明',optional:true}],columns:[['name','角色名称'],['code','角色编码'],['description','角色说明'],['enabled','角色状态']]},
 permissions:{title:'菜单与按钮',noun:'权限',icon:'layers',caption:'定义用户看得到、点得到的功能入口。',fields:[{key:'systemId',label:'所属应用',type:'system'},{key:'code',label:'权限编码'},{key:'name',label:'显示名称'},{key:'type',label:'权限类型',choices:[['MENU','菜单'],['BUTTON','按钮']]},{key:'menuPath',label:'菜单地址',optional:true},{key:'parentId',label:'父菜单',type:'menu',optional:true}],columns:[['name','权限名称'],['code','权限编码'],['type','资源类型'],['systemId','所属应用'],['active','权限状态']]},
 resources:{title:'API 资源',noun:'资源',icon:'code',caption:'让每一次接口访问，都经过真实的授权校验。',fields:[{key:'systemId',label:'所属应用',type:'system'},{key:'name',label:'资源名称'},{key:'method',label:'HTTP 方法',choices:['GET','POST','PUT','PATCH','DELETE'].map(v=>[v,v])},{key:'path',label:'接口路径'},{key:'code',label:'后端权限码',optional:true}],columns:[['name','资源名称'],['method','请求方法'],['path','接口路径'],['code','权限码'],['systemId','所属应用'],['active','资源状态']]}
};
export const pageInfo: Record<string, [string,string,string]> = {
 imports:['数据导入','将组织、成员与功能权限，安全汇入同一个工作空间。','organization'],
 overview:['工作台','所有访问关系，一目了然。','overview'],
 hierarchy:['角色继承','复用已有权限，明确每一层继承关系。','hierarchy'],
 grants:['授权工作台','从人员到接口，逐步建立完整的授权链。','link'],
 mine:['我的权限','了解你能访问什么，以及权限来自哪里。','key'],
 password:['账号安全','定期更新密码，保护你的访问凭证。','lock']
};
export type Api = (path:string,method?:string,body?:unknown)=>Promise<any>;

/** 工作区状态与 HTTP 边界；JWT 只保存在当前标签页，异步结果不得恢复已退出的身份。 */
export function useConsole() {
 const s = reactive({ token:sessionStorage.getItem('permission-token')||'', me:null as Row|null, page:'overview', backendStrategy:'', refreshVersion:0, busy:false, notice:'', failure:false,
  systems:[] as Row[], roles:[] as Row[], permissions:[] as Row[], resources:[] as Row[], departments:[] as Row[], users:[] as Row[], edges:[] as Row[],
  rows:[] as Row[], total:0, currentPage:0, userTotal:0, pickerPage:0, myPermissions:[] as Row[], myMenu:[] as Row[], origins:[] as Row[],
  username:'', password:'', editor:false, editing:null as Row|null, confirm:null as {title:string;description:string;accept:()=>void;cancel:()=>void;danger:boolean}|null });
 let noticeTimer: ReturnType<typeof setTimeout>;
 /** 反馈在新操作前清除，错误保留直到用户处理。 */
 function message(text:string,error=false){clearTimeout(noticeTimer);s.notice=text;s.failure=error;if(!error)noticeTimer=setTimeout(()=>s.notice='',6000);}
 /** 完全清理当前身份与目录，避免不同登录账号看到前一账号的数据。 */
 function logout(){s.token='';sessionStorage.removeItem('permission-token');s.me=null;s.password='';s.editor=false;s.confirm?.cancel();s.confirm=null;for(const key of ['systems','roles','permissions','resources','departments','users','edges','rows','myPermissions','myMenu','origins'] as const)s[key]=[];}
 /** 对非成功响应保留后端信息，超时取消；过期会话立即退出。X-Client-Menu 让后端 logback 日志标出触发请求的菜单。 */
 const api:Api=async(path,method='GET',body)=>{
  const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),body instanceof FormData?180000:15000);
  try {const response=await fetch(path,{method,signal:controller.signal,headers:{...(body instanceof FormData?{}:{'Content-Type':'application/json'}),...(s.token?{Authorization:`Bearer ${s.token}`}:{}),'X-Client-Menu':s.page},...(body===undefined?{}:{body:body instanceof FormData?body:JSON.stringify(body)})});
   const data=response.status===204?null:await response.json().catch(()=>null);
   if(!response.ok){if(response.status===401&&path!=='/api/auth/login')logout();throw new Error(data?.message||`请求失败（${response.status}）`);}return data;
  }catch(error){if(error instanceof DOMException&&error.name==='AbortError')throw new Error('请求超时，请检查连接后重试');throw error;}finally{clearTimeout(timer);}
 };
 /** 防止提交期间重复触发写操作。 */
 async function run(work:()=>Promise<void>){if(s.busy)return;s.busy=true;s.notice='';try{await work();}catch(error){message(error instanceof Error?error.message:String(error),true);}finally{s.busy=false;}}
 /** 所有目录取真实接口；用户选择器按页加载，避免悄悄漏掉第 101 位用户。 */
 async function catalogs(){if(!s.me?.administrator)return;const owner=s.token;const data=await Promise.all(['/api/systems','/api/roles','/api/permissions','/api/resources','/api/departments','/api/role-hierarchy','/api/system-info'].map(p=>api(p)));if(owner!==s.token)return;[s.systems,s.roles,s.permissions,s.resources,s.departments,s.edges]=data;s.backendStrategy=data[6].backendStrategy;await picker(s.pickerPage);}
 async function picker(page:number){const owner=s.token;const data=await api(`/api/users?page=${page}&size=25`);if(owner!==s.token)return;s.users=data.items;s.userTotal=data.total;s.pickerPage=page;}
 /** 刷新当前页面；全局忙碌态保证切页与刷新顺序执行。 */
 async function refresh(){const owner=s.token;if(s.me?.administrator)await catalogs();if(owner!==s.token)return;
  if(screens[s.page]){const data=await api(`/api/${s.page}${s.page==='users'?`?page=${s.currentPage}&size=10`:''}`);if(owner!==s.token)return;s.rows=Array.isArray(data)?data:data.items;s.total=Array.isArray(data)?data.length:data.total;}
  if(s.page==='mine'){const data=await Promise.all(['/api/me/permissions','/api/me/menu','/api/me/permission-origins'].map(p=>api(p)));if(owner===s.token)[s.myPermissions,s.myMenu,s.origins]=data;}s.refreshVersion++;}
 /** 保留可复制的页面地址，浏览器后退同样可以导航。 */
 async function select(page:string,recordHistory=true){if(s.busy)return;const allowed=s.me?.administrator?[...Object.keys(screens),...Object.keys(pageInfo)]:['mine','password'];s.page=allowed.includes(page)?page:(s.me?.administrator?'overview':'mine');s.currentPage=0;s.rows=[];if(recordHistory)history.pushState(null,'',`#${s.page}`);await run(refresh);}
 async function initialize(){const owner=s.token;const user=await api('/api/me');if(owner!==s.token)return;s.me=user;const target=location.hash.slice(1);const allowed=user.administrator?[...Object.keys(screens),...Object.keys(pageInfo)]:['mine','password'];s.page=allowed.includes(target)?target:(user.administrator?'overview':'mine');await refresh();}
 async function login(){await run(async()=>{const data=await api('/api/auth/login','POST',{username:s.username,password:s.password});s.token=data.accessToken;sessionStorage.setItem('permission-token',s.token);s.password='';await initialize();});}
 /** 统一确认对话框，取消和 Escape 均不发起写入。 */
 function ask(title:string,description:string,danger=true){return new Promise<boolean>(resolve=>{s.confirm={title,description,danger,accept:()=>{s.confirm=null;resolve(true);},cancel:()=>{s.confirm=null;resolve(false);}};});}
 async function remove(row:Row){if(!await ask('删除'+(row.name||row.displayName), '删除后无法恢复。存在授权、继承或其他关联时，系统会拒绝删除，请先解除关联。'))return;await run(async()=>{await api(`/api/${s.page}/${row.id}`,'DELETE');if(s.rows.length===1&&s.currentPage>0)s.currentPage--;await refresh();message('已删除');});}
 async function status(row:Row){const key='active'in row?'active':'enabled';if(!await ask(`${row[key]?'停用':'启用'}${row.name||row.displayName}`,row[key]?'该对象提供的有效授权将受到影响，关联关系会保留。停用角色会阻断经过它的继承路径。':'重新启用后，保留的关联将重新参与有效权限计算。',row[key]))return;await run(async()=>{await api(`/api/${s.page}/${row.id}/status`,'PUT',{[key]:!row[key]});await refresh();message('状态已更新');});}
 function label(key:string,value:any){if(value==null||value==='')return '—';if(key==='personType')return personChoices.find(v=>v[0]===value)?.[1]||value;if(key==='type')return value==='MENU'?'菜单':'按钮';if(key==='systemId')return s.systems.find(v=>v.id===value)?.name||value;if(key==='departmentId'||key==='parentId')return s.departments.find(v=>v.id===value)?.name||'顶级部门';return value;}
 return {s,api,run,message,logout,login,initialize,refresh,select,ask,remove,status,label,picker};
}
export type Console = ReturnType<typeof useConsole>;
