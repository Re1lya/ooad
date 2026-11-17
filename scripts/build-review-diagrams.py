"""Generate reproducible UML SVG + PNG from one geometry model and verify it against source.

UML rules: uml-class-sequence-diagrams skill. ER is a separate conceptual model.
Requires Pillow. Run from the repository: python scripts/build-review-diagrams.py.
"""
from pathlib import Path
import html
import json
import math
import re
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/design/images'
OUT.mkdir(parents=True,exist_ok=True)
SRC=ROOT/'src/main/java/com/cdwy/permission'
INK='#24354b';MUTED='#60748c';BLUE='#e9f0fa';LINE='#758aa4'
FONT=Path('C:/Windows/Fonts/msyh.ttc')
BOLD=Path('C:/Windows/Fonts/msyhbd.ttc')
if not FONT.exists():
    FONT=Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf');BOLD=Path('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf')

class Canvas:
    def __init__(self,w,h,title,subtitle):
        self.w,self.h=w,h
        self.image=Image.new('RGB',(w,h),'white');self.d=ImageDraw.Draw(self.image)
        self.svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">', '<rect width="100%" height="100%" fill="white"/>']
        self.text(w/2,25,title,30,True,'middle');self.text(w/2,70,subtitle,17,False,'middle',MUTED)
    def text(self,x,y,text,size=18,bold=False,anchor='start',fill=INK,max_width=None):
        font=ImageFont.truetype(str(BOLD if bold else FONT),size)
        while max_width and self.d.textlength(text,font=font)>max_width and size>12:
            size-=1;font=ImageFont.truetype(str(BOLD if bold else FONT),size)
        assert not max_width or self.d.textlength(text,font=font)<=max_width,('text overflow',text)
        self.d.text((x,y),text,font=font,fill=fill,anchor={'start':'lt','middle':'mt','end':'rt'}[anchor])
        self.svg.append(f'<text x="{x}" y="{y+size}" font-family="Microsoft YaHei,DejaVu Sans,sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{fill}" text-anchor="{anchor}">{html.escape(text)}</text>')
    def rect(self,x,y,w,h,fill='white',stroke=INK):
        self.d.rectangle((x,y,x+w,y+h),fill=fill,outline=stroke,width=2)
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill or "none"}" stroke="{stroke}" stroke-width="2"/>')
    def line(self,points,dashed=False,fill=LINE,width=2):
        if dashed:
            for a,b in zip(points,points[1:]):
                length=math.dist(a,b);n=max(1,math.ceil(length/8))
                for i in range(0,n,2):
                    p=tuple(a[j]+(b[j]-a[j])*i/n for j in [0,1]);q=tuple(a[j]+(b[j]-a[j])*min(i+1,n)/n for j in [0,1]);self.d.line([p,q],fill=fill,width=width)
        else:self.d.line(points,fill=fill,width=width)
        self.svg.append(f'<polyline points="'+ ' '.join(f'{x},{y}' for x,y in points)+f'" fill="none" stroke="{fill}" stroke-width="{width}"'+(' stroke-dasharray="8 8"' if dashed else '')+'/>')
    def arrow(self,points,dashed=False,solid=False,triangle=False):
        self.line(points,dashed)
        a,b=points[-2:];angle=math.atan2(b[1]-a[1],b[0]-a[0]);length=17 if triangle else 12
        p=(b[0]-length*math.cos(angle-.45),b[1]-length*math.sin(angle-.45));q=(b[0]-length*math.cos(angle+.45),b[1]-length*math.sin(angle+.45))
        if solid or triangle:
            self.d.polygon([b,p,q],fill=LINE if solid else 'white',outline=LINE)
            self.svg.append('<polygon points="'+' '.join(f'{x},{y}' for x,y in [b,p,q])+f'" fill="{LINE if solid else "white"}" stroke="{LINE}"/>')
        else:self.line([p,b,q])
    def save(self,name):
        self.image.save(OUT/(name+'.png'))
        (OUT/(name+'.svg')).write_text('\n'.join(self.svg+['</svg>']),encoding='utf-8')

def assertNoOverlap(boxes):
    for i,a in enumerate(boxes):
        for b in boxes[i+1:]:
            ax,ay,aw,ah=a['box'];bx,by,bw,bh=b['box']
            assert max(ax,bx)>=min(ax+aw,bx+bw) or max(ay,by)>=min(ay+ah,by+bh),('overlap',a['name'],b['name'])

def segment_intersects_box(a,b,box):
    x,y,w,h=box;x+=2;y+=2;w-=4;h-=4
    # Slab clipping against the open interior.
    t0,t1=0.0,1.0
    for j,low,high in [(0,x,x+w),(1,y,y+h)]:
        delta=b[j]-a[j]
        if abs(delta)<1e-9:
            if not low<a[j]<high:return False
        else:
            lo,hi=sorted(((low-a[j])/delta,(high-a[j])/delta));t0=max(t0,lo);t1=min(t1,hi)
            if t0>=t1:return False
    return t0<t1

def assertEdgesClearOfBoxes(boxes,edges):
    for source,target,points in edges:
        for node in boxes:
            if node['name'] in [source,target]:continue
            assert not any(segment_intersects_box(a,b,node['box']) for a,b in zip(points,points[1:])),('line crosses box',source,target,node['name'])

manifest=[]
def class_diagram(name,title,w,h,nodes,edges,center):
    assertNoOverlap(nodes);assertEdgesClearOfBoxes(nodes,edges)
    target=next(n for n in nodes if n['name']==center);x,y,bw,bh=target['box']
    assert abs((x+bw/2)-w/2)<w*.1 and abs((y+bh/2)-h/2)<h*.1,'Service not central'
    for node in nodes:
        p=SRC/node['source'];assert p.exists(),p
        text=p.read_text(encoding='utf-8')
        if node.get('external'):
            assert 'import org.springframework.security.crypto.password.PasswordEncoder;' in text
        else: assert re.search(r'\b(class|interface|record)\s+'+re.escape(node['class'])+r'\b',text),node['class']
        for method in node.get('verify',[]):assert re.search(r'\b'+method+r'\s*\(',text),(node['class'],method)
    c=Canvas(w,h,title,'类和方法取自当前源码 · 实线标角色名为关联，虚线为依赖 · Service 位于中心')
    association_counts={}
    for source,target,points in edges:
        source_node=next(n for n in nodes if n['name']==source)
        fields=source_node.get('fields',[])
        matches=[field for field in fields if re.search(r':\s*'+re.escape(target)+r'\b',field)]
        index=association_counts.get((source,target),0)
        association=matches[index] if index<len(matches) else None
        association_counts[(source,target)]=index+1
        realization=target in source_node.get('implements',[])
        c.arrow(points,dashed=realization or not bool(association),triangle=realization)
        if association:
            role_name=association.split(':',1)[0].replace('-','').strip()
            a,b=points[0],points[1]
            if a[1]==b[1]:c.text((a[0]+b[0])/2,a[1]-25,role_name,15,anchor='middle')
            else:c.text(a[0]-10,(a[1]+b[1])/2,role_name,15,anchor='end')
    for n in nodes:
        x,y,bw,bh=n['box'];c.rect(x,y,bw,bh,BLUE if n['name']==center else '#f8fafc')
        c.text(x+bw/2,y+15,('«interface» ' if n.get('interface') else '')+n['name'],23,True,'middle',max_width=bw-24)
        c.line([(x,y+55),(x+bw,y+55)])
        props=n.get('fields',[]);split=y+66+max(1,len(props))*27+10
        for i,line in enumerate(props or ['—']):c.text(x+14,y+67+i*27,line,17,max_width=bw-28)
        c.line([(x,split),(x+bw,split)])
        for i,line in enumerate(n['methods']):
            assert split+14+i*27+22<y+bh,('vertical overflow',n['name'])
            c.text(x+14,split+13+i*27,line,17,max_width=bw-28)
    c.save(name);manifest.append({'name':name,'type':'class','verifiedSourceClasses':[n['name'] for n in nodes],'checks':['noOverlap','edgesClearOfBoxes','serviceCentered','sourceMethodsExist','textFits']})

def node(name,box,source,methods,fields=(),interface=False,verify=(),cls=None,external=False):
    return dict(name=name,box=box,source=source,methods=methods,fields=fields,interface=interface,verify=verify,external=external,**{'class':cls or name})

class_diagram('class-auth','JWT 登录服务类图',1640,1020,[
 node('AuthService',(550,355,540,320),'service/auth/AuthService.java',['+ login(AuthData.Login): Token','+ resolve(String): Identity','+ changePassword(Long, PasswordChange): void'],['- users: UserAccountRepository','- encoder: PasswordEncoder','- tokens: JwtTokenService','- userService: UserService'],verify=['login','resolve','changePassword']),
 node('UserAccountRepository',(590,125,460,170),'repository/user/UserAccountRepository.java',['+ findByUsername(String): Optional<UserAccount>','+ lockById(Long): Optional<UserAccount>'],interface=True,verify=['findByUsername','lockById']),
 node('JwtTokenService',(1210,390,390,200),'service/auth/JwtTokenService.java',['+ issue(UserAccount): String','+ verify(String): Identity','+ expiresIn(): long'],['- signingKey: SecretKey'],verify=['issue','verify']),
 node('UserService',(590,825,460,170),'service/user/UserService.java',['+ password(Long, String): void','+ get(Long): UserResult'],verify=['password','get']),
 node('UserAccount',(40,795,390,200),'entity/user/UserAccount.java',['+ usable(Instant): boolean'],['- credentialVersion: long','- passwordHash: String'],verify=['usable']),
 node('AuthData.Token',(1210,795,390,200),'service/port/data/AuthData.java',['+ accessToken(): String','+ expiresIn(): long'],['- tokenType: String','- user: UserResult'],cls='Token'),
 node('PasswordEncoder',(40,385,390,190),'service/auth/AuthService.java',['+ matches(CharSequence, String): boolean'],interface=True,verify=['matches'],external=True)
], [
 ('AuthService','UserAccountRepository',[(820,355),(820,295)]),
 ('AuthService','JwtTokenService',[(1090,480),(1210,480)]),
 ('AuthService','UserService',[(820,675),(820,825)]),
 ('AuthService','UserAccount',[(550,675),(430,795)]),
 ('AuthService','AuthData.Token',[(1090,675),(1210,795)]),
 ('AuthService','PasswordEncoder',[(550,480),(430,480)])
], 'AuthService')

class_diagram('class-permission','前端权限与 API 资源关联类图',1840,1200,[
 node('PermissionService',(650,430,540,330),'service/permission/PermissionService.java',['+ create(Input): View','+ bind(Long, Long): void','+ status(Long, boolean): View','+ tree(Long): List<Tree>'],['- permissions: PermissionRepository','- grants: RolePermissionRepository','- bindings: PermissionApiRepository','- systems: SystemService','- resources: ApiResourceService'],verify=['create','bind','status','tree']),
 node('PermissionRepository',(640,135,560,190),'repository/permission/PermissionRepository.java',['+ existsByParent_Id(Long): boolean','+ findAllBySystem_IdOrderById(Long): List<Permission>'],interface=True,verify=['existsByParent_Id']),
 node('SystemService',(40,450,440,210),'service/system/SystemService.java',['+ require(Long): BusinessSystem','+ status(Long, boolean): View'],verify=['require','status']),
 node('ApiResourceService',(1360,450,440,210),'service/resource/ApiResourceService.java',['+ require(Long): ApiResource','+ status(Long, boolean): View'],verify=['require','status']),
 node('RolePermissionRepository',(40,940,440,190),'repository/rbac/RolePermissionRepository.java',['+ existsByPermission_Id(Long): boolean'],interface=True,verify=['existsByPermission_Id']),
 node('Permission',(640,900,560,240),'entity/permission/Permission.java',['+ getParent(): Permission','+ getType(): PermissionType'],['- parent: Permission','- type: MENU | BUTTON','- system: BusinessSystem'],verify=['getParent','getType']),
 node('PermissionApiRepository',(1360,940,440,190),'repository/rbac/PermissionApiRepository.java',['+ findAllByPermission_Id(Long): List<PermissionApi>','+ existsByResource_Id(Long): boolean'],interface=True,verify=['findAllByPermission_Id'])
],[
 ('PermissionService','PermissionRepository',[(920,430),(920,325)]),
 ('PermissionService','SystemService',[(650,550),(480,550)]),
 ('PermissionService','ApiResourceService',[(1190,550),(1360,550)]),
 ('PermissionService','RolePermissionRepository',[(650,760),(480,940)]),
 ('PermissionService','Permission',[(920,760),(920,900)]),
 ('PermissionService','PermissionApiRepository',[(1190,760),(1360,940)])
],'PermissionService')

def sequence(name,title,subtitle,lifelines,activations,messages,fragments=()):
    width=max(x for _,x in lifelines)+180;height=max(a[2] for a in activations)+90
    c=Canvas(width,height,title,subtitle)
    positions=dict(lifelines)
    for label,x in lifelines:
        c.rect(x-118,120,236,65,BLUE)
        lines=label.split('\n')
        for i,line in enumerate(lines):c.text(x,130+i*25 if len(lines)>1 else 143,line,18,True,'middle',max_width=220)
        c.line([(x,185),(x,height-35)],dashed=True)
    for label,start,end in activations:
        assert start<end
        for other,s,e in activations:
            if label==other and start<s:assert end<s,('merged activations',label)
        c.rect(positions[label]-7,start,14,end-start,'#d9e8fa')
    def active(label,y):return any(l==label and s<=y<=e for l,s,e in activations)
    for source,target,y,label,reply in messages:
        assert active(source,y) and active(target,y),('message outside activation',source,target,y)
        if not reply:assert any(l==target and s==y for l,s,_ in activations),('call misses execution top',label)
        left=positions[source]<positions[target];sx=positions[source]+(7 if left else -7);tx=positions[target]+(-7 if left else 7)
        c.arrow([(sx,y),(tx,y)],dashed=reply,solid=not reply)
        c.text((sx+tx)/2,y-29,label,16,anchor='middle',max_width=abs(tx-sx)-20)
    for x,y,w,h,label in fragments:
        for l,s,e in activations:
            px=positions[l]
            assert not (x<px<x+w and (s<y<e or s<y+h<e)),('fragment cuts execution',label,l)
        c.rect(x,y,w,h,fill=None);c.text(x+10,y+7,label,16,True)
    c.save(name);manifest.append({'name':name,'type':'sequence','checks':['executionTopAligned','messagesTouchActivationEdges','independentCallsHaveSeparateActivations','noFragmentCutsExecutions','lifelineTextFits']})

sequence('sequence-login','登录成功顺序图','账号和密码验证后签发 JWT · 不创建数据库登录会话',[
 ('obj: MVC 分发器',140),('auth: AuthController',430),('service: AuthService',740),('users: UserAccountRepository',1080),('encoder: PasswordEncoder',1410),('tokens: JwtTokenService',1740),('«MetaClass»\n: UserViews',2070)
],[('obj: MVC 分发器',235,1010),('auth: AuthController',235,950),('service: AuthService',315,890),('users: UserAccountRepository',400,445),('encoder: PasswordEncoder',520,565),('tokens: JwtTokenService',650,695),('«MetaClass»\n: UserViews',780,825)], [
 ('obj: MVC 分发器','auth: AuthController',235,'1 login(input)',False),
 ('auth: AuthController','service: AuthService',315,'2 token = login(input)',False),
 ('service: AuthService','users: UserAccountRepository',400,'3 user = findByUsername(name)',False),
 ('service: AuthService','encoder: PasswordEncoder',520,'4 valid = matches(raw, hash)',False),
 ('service: AuthService','tokens: JwtTokenService',650,'5 jwt = issue(user)',False),
 ('service: AuthService','«MetaClass»\n: UserViews',780,'6 userView = view(user)',False),
 ('auth: AuthController','obj: MVC 分发器',950,'Token（由 MVC 序列化为 HTTP 200）',True)
])

sequence('sequence-authorize','请求鉴权顺序图','课程示例请求 · ApiAccessConfig 注册的拦截器在控制器执行前认证并授权',[
 ('obj: MVC 分发器',140),('guard: HandlerInterceptor',470),('auth: AuthService',810),('tokens: JwtTokenService',1140),('users: UserAccountRepository',1490),('rbac: AuthorizationService',1840)
],[('obj: MVC 分发器',235,1070),('guard: HandlerInterceptor',235,1010),('auth: AuthService',320,650),('tokens: JwtTokenService',405,450),('users: UserAccountRepository',535,580),('rbac: AuthorizationService',755,880)], [
 ('obj: MVC 分发器','guard: HandlerInterceptor',235,'1 preHandle(request, response, handler)',False),
 ('guard: HandlerInterceptor','auth: AuthService',320,'2 user = resolve(header)',False),
 ('auth: AuthService','tokens: JwtTokenService',405,'3 identity = verify(header)',False),
 ('auth: AuthService','users: UserAccountRepository',535,'4 user = findById(identity.userId)',False),
 ('guard: HandlerInterceptor','rbac: AuthorizationService',755,'5 requireApi(id, course, GET, path)',False),
 ('guard: HandlerInterceptor','obj: MVC 分发器',1010,'true（允许继续分发到业务控制器）',True)
])

sequence('sequence-password-failure','密码错误的异常顺序图','未捕获异常从实际抛出方返回调用者 · MVC 异常处理器转为 401',[
 ('obj: MVC 分发器',140),('auth: AuthController',470),('service: AuthService',820),('users: UserAccountRepository',1180),('encoder: PasswordEncoder',1530),('advice: ApiExceptionHandler',1880)
],[('obj: MVC 分发器',235,1100),('auth: AuthController',235,790),('service: AuthService',320,780),('users: UserAccountRepository',410,455),('encoder: PasswordEncoder',535,580),('advice: ApiExceptionHandler',900,1000)], [
 ('obj: MVC 分发器','auth: AuthController',235,'1 login(input)',False),
 ('auth: AuthController','service: AuthService',320,'2 login(input)',False),
 ('service: AuthService','users: UserAccountRepository',410,'3 user = findByUsername(name)',False),
 ('service: AuthService','encoder: PasswordEncoder',535,'4 valid = matches(raw, hash)',False),
 ('service: AuthService','obj: MVC 分发器',755,'AuthenticationFailedException',True),
 ('obj: MVC 分发器','advice: ApiExceptionHandler',900,'5 unauthorized(exception)',False),
 ('advice: ApiExceptionHandler','obj: MVC 分发器',990,'ResponseEntity（HTTP 401）',True)
])

def conceptual_er():
    c=Canvas(1700,1080,'统一权限管理系统核心 ER 图','六类核心业务对象 · 三组授权关联与角色继承自关联 · 登录 Token 不是持久化实体')
    boxes={
        '部门':(60,170,360,190,['id','名称','上级部门']),
        '用户':(670,170,360,190,['id / 登录名 / 姓名','身份属性 / 有效期','所属部门']),
        '角色':(1280,170,360,190,['id / 编码 / 名称','启用状态']),
        '应用':(60,720,360,210,['id / 编码 / 名称','启用状态']),
        '前端权限':(670,720,360,210,['id / 编码 / 名称','类型 MENU 或 BUTTON','父菜单 / 启用状态']),
        'API 资源':(1280,720,360,210,['id / 名称 / 所属应用','HTTP 方法 / 路径','启用状态']),
    }
    links=[('部门','用户',[(420,260),(670,260)],'0..1              0..N'),('用户','角色',[(1030,260),(1280,260)],'M                 N'),('角色','前端权限',[(1460,360),(1460,535),(850,535),(850,720)],'M : N 角色授权'),('应用','前端权限',[(420,825),(670,825)],'1                 0..N'),('前端权限','API 资源',[(1030,825),(1280,825)],'M                 N'),('应用','API 资源',[(240,930),(240,1000),(1460,1000),(1460,930)],'1 : N 应用拥有 API 资源')]
    for a,b,points,label in links:
        c.line(points)
        if len(points)==2:c.text((points[0][0]+points[-1][0])/2,points[0][1]-40,label,17,anchor='middle')
        elif a=='角色':c.text(1140,495,label,18,anchor='middle')
        else:c.text(850,1010,label,18,anchor='middle')
    for name,(x,y,w,h,fields) in boxes.items():
        c.rect(x,y,w,h,BLUE);c.text(x+w/2,y+12,name,25,True,'middle');c.line([(x,y+54),(x+w,y+54)])
        for i,field in enumerate(fields):c.text(x+20,y+70+i*32,field,19,max_width=w-40)
    # Self relationships live outside other entity boxes.
    c.line([(100,170),(100,130),(370,130),(370,170)]);c.text(235,96,'0..1 上级 / 0..N 子部门',16,anchor='middle')
    c.line([(720,930),(720,970),(980,970),(980,930)]);c.text(850,975,'0..1 父菜单 / 0..N 子节点',16,anchor='middle')
    c.line([(1320,170),(1320,130),(1590,130),(1590,170)]);c.text(1455,96,'M 高级角色 / N 被继承角色',16,anchor='middle')
    c.save('er-core');manifest.append({'name':'er-core','type':'conceptual-er','entities':6,'manyToMany':['用户-角色','角色-前端权限','前端权限-API资源','角色-角色继承']})

conceptual_er()

def physical_er():
    actual=json.loads((ROOT/'target/first-review/schema.json').read_text(encoding='utf-8'))
    c=Canvas(2280,1810,'十一表物理关系图','来自实际 JPA 结构 · 箭头从外键表指向被引用表 · 14 个外键 · 不含 Hibernate 临时表')
    boxes=[
        ('sys_department',(60,180,420,200)),('sys_user',(630,180,420,480)),
        ('sys_account',(1200,180,420,210)),('sys_role',(1770,180,420,270)),
        ('biz_system',(60,760,420,270)),('sys_permission',(630,760,420,440)),
        ('sys_user_role',(1200,760,420,210)),('sys_role_permission',(1770,760,420,210)),
        ('api_resource',(60,1390,420,310)),('permission_api',(630,1390,420,210)),('sys_role_hierarchy',(1770,1390,420,210))]
    assert set(n.upper() for n,_ in boxes)==set(actual)
    nodes=[dict(name=n,box=b) for n,b in boxes];assertNoOverlap(nodes)
    edges=[
        ('sys_department','sys_department',[(100,180),(100,140),(420,140),(420,180)],(260,110),'parent_id'),
        ('sys_user','sys_department',[(630,280),(480,280)],(555,248),'department_id'),
        ('sys_account','sys_user',[(1200,280),(1050,280)],(1125,248),'user_id'),
        ('sys_user_role','sys_user',[(1270,760),(1270,640),(1130,640),(1130,690),(840,690),(840,660)],(1000,708),'user_id'),
        ('sys_user_role','sys_role',[(1550,760),(1550,590),(1980,590),(1980,450)],(1770,558),'role_id'),
        ('sys_role_permission','sys_role',[(2120,760),(2120,450)],(2190,600),'role_id'),
        ('sys_role_permission','sys_permission',[(1770,860),(1680,860),(1680,1160),(1110,1160),(1110,1080),(1050,1080)],(1380,1128),'permission_id'),
        ('sys_permission','biz_system',[(630,860),(480,860)],(555,828),'system_id'),
        ('sys_permission','sys_permission',[(700,1200),(700,1270),(900,1270),(900,1200)],(800,1280),'parent_id'),
        ('api_resource','biz_system',[(260,1390),(260,1030)],(350,1240),'system_id'),
        ('permission_api','sys_permission',[(1010,1390),(1010,1200)],(1070,1300),'permission_id'),
        ('permission_api','api_resource',[(630,1500),(480,1500)],(555,1468),'resource_id'),
    ]
    edges += [('sys_role_hierarchy','sys_role',[(1840,1390),(1720,1390),(1720,480),(1840,480),(1840,450)],(1610,1050),'senior_role_id'), ('sys_role_hierarchy','sys_role',[(2190,1500),(2240,1500),(2240,350),(2190,350)],(2165,1175),'inherited_role_id')]
    assertEdgesClearOfBoxes(nodes,[(a,b,p) for a,b,p,_,_ in edges])
    fk={(a,label) for a,_,_,_,label in edges}
    primary={'sys_account':'user_id'}
    for a,b,points,(tx,ty),label in edges:
        c.arrow(points);c.text(tx,ty,label,16,anchor='middle')
    for table,(x,y,w,h) in boxes:
        c.rect(x,y,w,h,BLUE);c.text(x+w/2,y+15,table,24,True,'middle');c.line([(x,y+55),(x+w,y+55)])
        for i,column in enumerate(actual[table.upper()]):
            col=column['COLUMN_NAME'].lower();is_pk=col==primary.get(table,'id')
            marks=('PK ' if is_pk else '')+('FK ' if (table,col) in fk else '')
            kind=column['DATA_TYPE'].replace('CHARACTER VARYING','varchar').replace('TIMESTAMP WITH TIME ZONE','timestamp').lower()
            required='' if column['IS_NULLABLE']=='YES' else ' !'
            assert y+70+i*29+23<y+h,('physical field overflow',table)
            c.text(x+16,y+70+i*29,f'{marks}{col}: {kind}{required}',17,max_width=w-32)
    c.text(1410,1480,'! 表示 NOT NULL',19,True,anchor='middle')
    c.text(1410,1525,'sys_account 是 UserAccount 的物理拆表',18,anchor='middle')
    c.text(1410,1570,'四张关联表各有两端 ID 的组合唯一键',18,anchor='middle')
    c.text(1410,1615,'其他唯一键与长度以 MySQL DDL 为准',18,anchor='middle')
    c.save('er-physical');manifest.append({'name':'er-physical','type':'physical-er','tables':11,'foreignKeys':14,'checks':['persistentSchemaMatches','noOverlap','edgesClearOfBoxes']})

physical_er()

# RBAC1 图使用同一渲染及布局校验基础，参与者与方法逐一核对源码。
class_diagram('class-rbac1','RBAC1 角色继承与权限计算',1800,1100,[
 node('RoleHierarchyService',(620,390,560,340),'service/rbac/RoleHierarchyService.java',['+ inherit(Long, Long): void','+ remove(Long, Long): void','+ graph(): Map<Long, List<Role>>','+ resolve(Role, Map): List<ResolvedRole>'],['- hierarchy: RoleHierarchyRepository','- roles: RoleRepository','- policy: RoleGraphUseCase'],verify=['inherit','remove','graph','resolve']),
 node('RoleHierarchyRepository',(580,130,640,190),'repository/rbac/RoleHierarchyRepository.java',['+ existsBySeniorRole_IdAndInheritedRole_Id(...)','+ deleteBySeniorRole_IdAndInheritedRole_Id(...)'],interface=True,verify=['existsBySeniorRole_IdAndInheritedRole_Id']),
 node('RoleGraphUseCase',(35,110,470,230),'service/port/rbac/RoleGraphUseCase.java',['+ validate(Long, Long, Map): void','+ resolve(Node, Map): List<Path>'],interface=True,verify=['validate','resolve']),
 node('RoleRepository',(35,425,470,220),'repository/role/RoleRepository.java',['+ lockHierarchyRoles(): List<Role>'],interface=True,verify=['lockHierarchyRoles']),
 node('AuthorizationService',(1300,390,465,250),'service/rbac/AuthorizationService.java',['+ effective(Long): List<Permission>','+ origins(Long): List<PermissionOrigin>','+ roleOrigins(Long): List<PermissionOrigin>'],['- hierarchy: RoleHierarchyService'],verify=['effective','origins','roleOrigins']),
 node('RoleHierarchy',(300,850,470,180),'entity/rbac/RoleHierarchy.java',['+ RoleHierarchy(Role, Role)'],['- seniorRole: Role','- inheritedRole: Role'],verify=['RoleHierarchy']),
 node('Role',(1030,850,470,180),'entity/role/Role.java',['+ getEnabled(): boolean'],['- id: Long','- enabled: boolean'],verify=['getEnabled'])
],[('RoleHierarchyService','RoleGraphUseCase',[(700,390),(700,365),(270,365),(270,340)]),('RoleHierarchyService','RoleHierarchyRepository',[(900,390),(900,320)]),('RoleHierarchyService','RoleRepository',[(620,520),(505,520)]),('AuthorizationService','RoleHierarchyService',[(1300,520),(1180,520)]),('RoleHierarchyService','RoleHierarchy',[(700,730),(700,790),(535,790),(535,850)]),('RoleHierarchyService','Role',[(1080,730),(1080,790),(1265,790),(1265,850)]),('RoleHierarchy','Role',[(770,930),(1030,930)]),('RoleHierarchy','Role',[(770,995),(1030,995)])],'RoleHierarchyService')
sequence('sequence-rbac1','建立角色继承关系顺序图','当前代码成功路径 · 共享事务与持久化，角色图校验按配置切换',[
 ('obj: MVC 分发器',150),('api: RoleHierarchyController',470),('service: RoleHierarchyService',830),('roles: RoleRepository',1220),('edges: RoleHierarchyRepository',1630),('policy: RoleGraphUseCase',2010)
],[('obj: MVC 分发器',235,1120),('api: RoleHierarchyController',235,1060),('service: RoleHierarchyService',315,1010),('roles: RoleRepository',400,440),('roles: RoleRepository',490,530),('roles: RoleRepository',580,620),('edges: RoleHierarchyRepository',670,710),('edges: RoleHierarchyRepository',760,800),('policy: RoleGraphUseCase',850,890),('edges: RoleHierarchyRepository',940,980)],[
 ('obj: MVC 分发器','api: RoleHierarchyController',235,'1 inherit(seniorId, inheritedId)',False),
 ('api: RoleHierarchyController','service: RoleHierarchyService',315,'2 inherit(seniorId, inheritedId)',False),
 ('service: RoleHierarchyService','roles: RoleRepository',400,'3 lockHierarchyRoles()',False),
 ('service: RoleHierarchyService','roles: RoleRepository',490,'4 senior = findById(seniorId)',False),
 ('service: RoleHierarchyService','roles: RoleRepository',580,'5 inherited = findById(inheritedId)',False),
 ('service: RoleHierarchyService','edges: RoleHierarchyRepository',670,'6 existsBySeniorRole_IdAndInheritedRole_Id(...)',False),
 ('service: RoleHierarchyService','edges: RoleHierarchyRepository',760,'7 allEdges = findAll()',False),
 ('service: RoleHierarchyService','policy: RoleGraphUseCase',850,'8 validate(seniorId, inheritedId, snapshot)',False),
 ('service: RoleHierarchyService','edges: RoleHierarchyRepository',940,'9 save(new RoleHierarchy(...))',False),
 ('api: RoleHierarchyController','obj: MVC 分发器',1060,'HTTP 204 · 提交后后续请求读取新关系',True)
])

dual_nodes=[
 node('DddRoleGraphService',(630,410,560,280),'service/ddd/application/rbac/DddRoleGraphService.java',['+ validate(Long, Long, Map): void','+ resolve(Node, Map): List<Path>','+ strategy(): String'],verify=['validate','resolve','strategy']),
 node('RoleGraphUseCase',(630,120,560,210),'service/port/rbac/RoleGraphUseCase.java',['+ validate(Long, Long, Map): void','+ resolve(Node, Map): List<Path>','+ strategy(): String'],interface=True,verify=['validate','resolve']),
 node('StructuredRoleGraphService',(35,410,470,280),'service/structured/rbac/StructuredRoleGraphService.java',['+ validate(Long, Long, Map): void','+ resolve(Node, Map): List<Path>','+ strategy(): String'],verify=['validate','resolve']),
 node('RoleGraph',(1300,410,530,280),'service/ddd/domain/model/RoleGraph.java',['+ assertCanInherit(Long, Long): void','+ effectivePaths(Node): List<Path>'],['- edges: Map<Long, List<Node>>'],verify=['assertCanInherit','effectivePaths']),
 node('RoleHierarchyService',(630,810,560,220),'service/rbac/RoleHierarchyService.java',['+ inherit(Long, Long): void','+ resolve(Role, Map): List<ResolvedRole>'],['- policy: RoleGraphUseCase'],verify=['inherit','resolve'])]
for n in dual_nodes:
 if n['name'] in ['StructuredRoleGraphService','DddRoleGraphService']:n['implements']=['RoleGraphUseCase']
class_diagram('class-dual-design','角色图算法双实现类图（结构化调用支线）',1860,1120,dual_nodes,[
 ('DddRoleGraphService','RoleGraphUseCase',[(910,410),(910,330)]),
 ('StructuredRoleGraphService','RoleGraphUseCase',[(270,410),(270,240),(630,240)]),
 ('DddRoleGraphService','RoleGraph',[(1190,550),(1300,550)]),
 ('RoleHierarchyService','RoleGraphUseCase',[(1190,920),(1240,920),(1240,250),(1190,250)])
],'DddRoleGraphService')

sequence('sequence-ddd-policy','面向对象模式：角色图规则的委派','角色图快照由 DDD 应用服务通过领域仓储加载',[
 ('obj: DddHierarchyService',180),('policy: DddRoleGraphService',650),('graph: RoleGraph',1150)
],[('obj: DddHierarchyService',235,700),('policy: DddRoleGraphService',235,650),('graph: RoleGraph',400,540)],[
 ('obj: DddHierarchyService','policy: DddRoleGraphService',235,'1 validate(seniorId, inheritedId, snapshot)',False),
 ('policy: DddRoleGraphService','graph: RoleGraph',400,'2 assertCanInherit(seniorId, inheritedId)',False),
 ('policy: DddRoleGraphService','obj: DddHierarchyService',650,'正常结束，应用服务经仓储保存继承边',True)
])
sequence('sequence-company-import','三表导入：预览后确认的顺序图','摘要绑定原文件 · 提交前完整校验 · Service 事务失败全部回滚',[
 ('obj: 管理员页面',160),('api: CompanyImportController',530),('«MetaClass»\n: CompanyWorkbookParser',930),('service: CompanyImportService',1350),('users: UserAccountRepository',1800)
],[('obj: 管理员页面',235,1040),('api: CompanyImportController',235,500),('api: CompanyImportController',585,1000),('«MetaClass»\n: CompanyWorkbookParser',320,410),('«MetaClass»\n: CompanyWorkbookParser',675,755),('service: CompanyImportService',825,960),('users: UserAccountRepository',890,930)],[
 ('obj: 管理员页面','api: CompanyImportController',235,'1 preview(三份 multipart 文件)',False),
 ('api: CompanyImportController','«MetaClass»\n: CompanyWorkbookParser',320,'2 plan = parse(三份文件)',False),
 ('api: CompanyImportController','obj: 管理员页面',480,'统计、处理规则与 SHA-256 摘要（不写库）',True),
 ('obj: 管理员页面','api: CompanyImportController',585,'3 commit(三份原文件, digest)',False),
 ('api: CompanyImportController','«MetaClass»\n: CompanyWorkbookParser',675,'4 plan = parse(三份文件)',False),
 ('api: CompanyImportController','service: CompanyImportService',825,'5 commit(plan, digest)',False),
 ('service: CompanyImportService','users: UserAccountRepository',890,'6 save(account)（事务内按员工迭代）',False),
 ('api: CompanyImportController','obj: 管理员页面',1000,'事务提交后返回汇总；其他五类仓储调用略',True)
])

# 结构化设计使用模块分解语义，不套用 UML 类的继承符号。
def structure_chart():
    c=Canvas(1640,920,'结构化设计：RBAC1 功能模块分解','方框表示功能模块，连线表示分解与调用；对应结构化模式的实际过程')
    boxes=[('继承与有效权限入口',(570,140,500,110)),('新增边校验 validate',(150,365,560,130)),('有效路径解析 resolve',(920,365,560,130)),('反向可达性检查',(60,680,400,120)),('读取完整邻接关系',(500,680,400,120)),('过滤停用与菱形去重',(1000,680,500,120))]
    nodes=[{'name':n,'box':b} for n,b in boxes];assertNoOverlap(nodes)
    edges=[('继承与有效权限入口','新增边校验 validate',[(680,250),(680,300),(430,300),(430,365)]),('继承与有效权限入口','有效路径解析 resolve',[(960,250),(960,300),(1200,300),(1200,365)]),('新增边校验 validate','反向可达性检查',[(330,495),(330,580),(260,580),(260,680)]),('新增边校验 validate','读取完整邻接关系',[(600,495),(600,560),(700,560),(700,680)]),('有效路径解析 resolve','过滤停用与菱形去重',[(1200,495),(1200,680)])]
    assertEdgesClearOfBoxes(nodes,edges)
    for _,_,points in edges:c.line(points)
    for name,(x,y,w,h) in boxes:
        c.rect(x,y,w,h,BLUE);c.text(x+w/2,y+30,name,23,True,'middle',max_width=w-30)
    c.text(820,850,'共享事务服务负责读写数据库；本图表达算法的功能分解，不是类图或部署图。',18,anchor='middle')
    c.save('structure-rbac1');manifest.append({'name':'structure-rbac1','type':'structure-chart','checks':['noOverlap','edgesClearOfBoxes','sourceBehaviorMapped']})
structure_chart()


# 全业务设计的用户模块代表图：中心为用例服务，不把实体或 JpaRepository 画入领域。
full_nodes=[
 node('DddUserService',(620,430,560,330),'service/ddd/application/user/DddUserService.java',['+ create(CreateUserCommand): UserResult','+ update(Long, Update): UserResult','+ status(Long, Long, boolean): UserResult','+ password(Long, String): void'],['- accounts: AccountRepository','- departments: DepartmentDirectory','- assignments: AssignmentRepository','- credentials: CredentialGateway'],verify=['create','update','status','password']),
 node('UserUseCase',(640,130,520,190),'service/port/user/UserUseCase.java',['+ create(CreateUserCommand): UserResult','+ status(Long, Long, boolean): UserResult'],interface=True,verify=['create','status']),
 node('AccountRepository',(30,440,480,220),'service/ddd/domain/repository/AccountRepository.java',['+ lock(Long): Optional<Account>','+ save(Account): Account','+ page(int, int): Page<Account>'],interface=True,verify=['lock','save','page']),
 node('Account',(1310,430,500,300),'service/ddd/domain/model/Account.java',['+ profile(..., AccessWindow): void','+ changeStatus(Long, boolean): void','+ resetPassword(String): void','+ assertDeletable(Long): void'],['- credentialVersion: long','- enabled: boolean'],verify=['profile','changeStatus','resetPassword','assertDeletable']),
 node('CredentialGateway',(30,930,480,220),'service/port/auth/CredentialGateway.java',['+ validatePassword(String): void','+ encode(String): String','+ window(): AccessWindow'],interface=True,verify=['encode','window']),
 node('DepartmentDirectory',(630,930,540,190),'service/ddd/domain/repository/DepartmentDirectory.java',['+ find(Long): Optional<DepartmentNode>'],interface=True,verify=['find']),
 node('AssignmentRepository',(1310,930,500,190),'service/ddd/domain/repository/AssignmentRepository.java',['+ removeUserRoles(Long): void'],interface=True,verify=['removeUserRoles'])
]
full_nodes[0]['implements']=['UserUseCase']
class_diagram('class-full-user','面向对象设计：账号管理与凭证状态',1840,1210,full_nodes,[
 ('DddUserService','UserUseCase',[(900,430),(900,320)]),
 ('DddUserService','AccountRepository',[(620,540),(510,540)]),
 ('DddUserService','Account',[(1180,550),(1310,550)]),
 ('DddUserService','CredentialGateway',[(620,760),(500,930)]),
 ('DddUserService','DepartmentDirectory',[(900,760),(900,930)]),
 ('DddUserService','AssignmentRepository',[(1180,760),(1330,930)])
],'DddUserService')

adapter_nodes=[
 node('JpaAccountRepositoryAdapter',(630,430,560,270),'infrastructure/persistence/JpaAccountRepositoryAdapter.java',['+ find(Long): Optional<Account>','+ lock(Long): Optional<Account>','+ save(Account): Account'],['- data: UserAccountRepository'],verify=['find','lock','save']),
 node('AccountRepository',(650,130,520,190),'service/ddd/domain/repository/AccountRepository.java',['+ lock(Long): Optional<Account>','+ save(Account): Account'],interface=True,verify=['lock','save']),
 node('UserAccountRepository',(30,450,480,200),'repository/user/UserAccountRepository.java',['+ lockById(Long): Optional<UserAccount>'],interface=True,verify=['lockById']),
 node('Account',(1320,450,480,200),'service/ddd/domain/model/Account.java',['+ changeStatus(Long, boolean): void','+ resetPassword(String): void'],verify=['changeStatus','resetPassword']),
 node('UserAccount',(650,920,520,200),'entity/user/UserAccount.java',['+ getCredentialVersion(): long','+ getDepartmentId(): Long'],verify=['getCredentialVersion','getDepartmentId'])
]
adapter_nodes[0]['implements']=['AccountRepository']
class_diagram('class-domain-adapter','领域仓储与 JPA 适配边界',1840,1190,adapter_nodes,[
 ('JpaAccountRepositoryAdapter','AccountRepository',[(910,430),(910,320)]),
 ('JpaAccountRepositoryAdapter','UserAccountRepository',[(630,540),(510,540)]),
 ('JpaAccountRepositoryAdapter','Account',[(1190,540),(1320,540)]),
 ('JpaAccountRepositoryAdapter','UserAccount',[(910,700),(910,920)])
],'JpaAccountRepositoryAdapter')

sequence('sequence-ddd-account','面向对象模式：停用账号使旧 JWT 失效','服务编排写锁、对象业务规则及仓储保存 · 当前事务完成后返回 HTTP',[('obj: 管理员页面',160),('api: UserController',500),('service: DddUserService',850),('accounts: AccountRepository',1240),('account: Account',1630)],[('obj: 管理员页面',235,1060),('api: UserController',235,1010),('service: DddUserService',330,950),('accounts: AccountRepository',430,510),('account: Account',620,730),('accounts: AccountRepository',830,900)],[
 ('obj: 管理员页面','api: UserController',235,'1 status(id, enabled=false)',False),
 ('api: UserController','service: DddUserService',330,'2 status(actorId, id, false)',False),
 ('service: DddUserService','accounts: AccountRepository',430,'3 account = lock(id)',False),
 ('service: DddUserService','account: Account',620,'4 changeStatus(actorId, false)',False),
 ('service: DddUserService','accounts: AccountRepository',830,'5 account = save(account)',False),
 ('api: UserController','obj: 管理员页面',1010,'事务完成，返回账号状态；旧 Token 下次请求 401',True)
])

def full_structure():
 c=Canvas(1900,950,'结构化设计：全业务功能模块分解','公共 HTTP 契约 → 十个事务脚本服务 → 共享 JPA 与技术能力')
 nodes=[{'name':'统一权限管理系统','box':(650,130,600,100)}]
 labels=['登录与密码','用户管理','部门管理','应用管理','角色管理','前端权限','API资源','授权鉴权','角色继承','公司三表导入']
 for i,label in enumerate(labels):
  row,col=divmod(i,5);nodes.append({'name':label,'box':(40+col*375,370+row*260,320,110)})
 assertNoOverlap(nodes)
 c.line([(950,230),(950,300),(75,300)]);c.line([(950,300),(1878,300)])
 c.line([(1878,300),(1878,560),(75,560)])
 for i,n in enumerate(nodes):
  x,y,w,h=n['box'];c.rect(x,y,w,h,BLUE);c.text(x+w/2,y+33,n['name'],24,True,'middle',max_width=w-24)
  if i:c.line([(x+w/2,300 if i<=5 else 560),(x+w/2,y)])
 c.text(950,850,'过程组织：读取 → 校验 → 写入 → 结果转换；每个业务端口在 structured / ddd 中只装载一个实现。',19,anchor='middle')
 c.save('structure-full-system');manifest.append({'name':'structure-full-system','type':'structure-chart','checks':['noOverlap','sourceModulesMapped']})
full_structure()


policy_nodes=[
 node('DddPermissionService',(650,440,560,330),'service/ddd/application/permission/DddPermissionService.java',['+ create(Input): View','+ update(Long, Update): View','+ bind(Long, Long): void','+ tree(Long): List<Tree>'],['- permissions: PermissionCatalog','- systems: SystemCatalog','- resources: ResourceCatalog','- assignments: AssignmentRepository'],verify=['create','update','bind','tree']),
 node('PermissionUseCase',(660,130,540,190),'service/port/permission/PermissionUseCase.java',['+ bind(Long, Long): void','+ tree(Long): List<Tree>'],interface=True,verify=['bind','tree']),
 node('PermissionCatalog',(30,440,500,220),'service/ddd/domain/repository/PermissionCatalog.java',['+ find(Long): Optional<FrontendPermission>','+ save(FrontendPermission): FrontendPermission'],interface=True,verify=['find','save']),
 node('FrontendPermission',(1360,420,540,300),'service/ddd/domain/model/FrontendPermission.java',['+ edit(..., Map, boolean): void','+ assertBindable(ProtectedResource): void','+ changeStatus(boolean): void'],['- systemId: Long','- parentId: Long'],verify=['edit','assertBindable','changeStatus']),
 node('SystemCatalog',(30,940,500,200),'service/ddd/domain/repository/SystemCatalog.java',['+ find(Long): Optional<ManagedSystem>'],interface=True,verify=['find']),
 node('ResourceCatalog',(660,940,540,200),'service/ddd/domain/repository/ResourceCatalog.java',['+ find(Long): Optional<ProtectedResource>'],interface=True,verify=['find']),
 node('AssignmentRepository',(1360,940,540,200),'service/ddd/domain/repository/AssignmentRepository.java',['+ bind(Long, Long): void','+ unbind(Long, Long): void'],interface=True,verify=['bind','unbind'])
]
policy_nodes[0]['implements']=['PermissionUseCase']
class_diagram('class-ddd-permission','面向对象设计：菜单按钮与 API 绑定',1940,1220,policy_nodes,[
 ('DddPermissionService','PermissionUseCase',[(930,440),(930,320)]),
 ('DddPermissionService','PermissionCatalog',[(650,550),(530,550)]),
 ('DddPermissionService','FrontendPermission',[(1210,550),(1360,550)]),
 ('DddPermissionService','SystemCatalog',[(650,770),(520,940)]),
 ('DddPermissionService','ResourceCatalog',[(930,770),(930,940)]),
 ('DddPermissionService','AssignmentRepository',[(1210,770),(1380,940)])
],'DddPermissionService')

(OUT.parent/'diagram-validation.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Generated and validated',len(manifest),'diagrams in PNG + SVG')
