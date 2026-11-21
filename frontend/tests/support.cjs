/**
 * 浏览器测试公共部分：隔离开关、浏览器启动、HTTP 辅助、控制台菜单登记、合成 xlsx。
 * 只在 PHASE_TWO_DEMO_ACK=isolated-h2 的隔离实例上运行；所有数据都是合成的。
 */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const zlib = require('node:zlib');
const { chromium } = require('playwright-core');

const isolated = process.env.PHASE_TWO_DEMO_ACK === 'isolated-h2';
const base = process.env.PHASE_TWO_URL || 'http://127.0.0.1:18082';
const admin = { username: process.env.PHASE_TWO_ADMIN_USERNAME, password: process.env.PHASE_TWO_ADMIN_PASSWORD };
const out = process.env.PHASE_TWO_OUTPUT;
const skip = isolated ? false : '需要 PHASE_TWO_DEMO_ACK=isolated-h2 的隔离实例';

function launch() {
  return chromium.launch({
    executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true,
  });
}

async function call(token, route, method = 'GET', body) {
  const response = await fetch(base + route, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  return { status: response.status, data };
}

async function api(token, route, method = 'GET', body) {
  const { status, data } = await call(token, route, method, body);
  assert.ok(status >= 200 && status < 300, `${method} ${route} → ${status} ${JSON.stringify(data)}`);
  return data;
}

async function token(username = admin.username, password = admin.password) {
  return (await api('', '/api/auth/login', 'POST', { username, password })).accessToken;
}

/** 权限系统自身在 rbac 应用下登记的控制台菜单与按钮（与前端路由一致）。 */
const CONSOLE = [
  ['rbac.overview', '工作台', 'MENU', '/overview'],
  ['rbac.users', '用户管理', 'MENU', '/users'],
  ['rbac.users.write', '维护用户', 'BUTTON', 'rbac.users'],
  ['rbac.directory.sync', '同步员工信息', 'BUTTON', 'rbac.users'],
  ['rbac.departments', '部门管理', 'MENU', '/departments'],
  ['rbac.departments.write', '维护部门', 'BUTTON', 'rbac.departments'],
  ['rbac.systems', '应用管理', 'MENU', '/systems'],
  ['rbac.systems.write', '维护应用', 'BUTTON', 'rbac.systems'],
  ['rbac.roles', '角色管理', 'MENU', '/roles'],
  ['rbac.roles.write', '维护角色', 'BUTTON', 'rbac.roles'],
  ['rbac.hierarchy', '角色继承', 'MENU', '/hierarchy'],
  ['rbac.hierarchy.write', '维护继承', 'BUTTON', 'rbac.hierarchy'],
  ['rbac.permissions', '菜单与按钮', 'MENU', '/permissions'],
  ['rbac.permissions.write', '维护菜单与按钮', 'BUTTON', 'rbac.permissions'],
  ['rbac.resources', 'API 资源', 'MENU', '/resources'],
  ['rbac.resources.write', '维护 API 资源', 'BUTTON', 'rbac.resources'],
  ['rbac.grants', '授权工作台', 'MENU', '/grants'],
  ['rbac.grants.write', '维护授权', 'BUTTON', 'rbac.grants'],
  ['rbac.effective', '有效权限', 'MENU', '/effective'],
  ['rbac.audit', '审计日志', 'MENU', '/audit'],
];

function flatten(tree) {
  return tree.flatMap((n) => [n, ...flatten(n.children || [])]);
}

/**
 * 确保管理员在 rbac 应用内拥有全部控制台菜单。后端已在启动时登记时直接复用；
 * 否则通过管理接口幂等地补齐（应用、菜单、按钮、全权限角色、分配）。返回 rbac 应用与各权限。
 */
async function ensureConsole(adminToken) {
  let system = (await api(adminToken, '/api/systems')).find((s) => s.code === 'rbac');
  if (!system) system = await api(adminToken, '/api/systems', 'POST', { code: 'rbac', name: '权限管理系统', baseUrl: '/ui/' });
  const existing = Object.fromEntries((await api(adminToken, `/api/permissions?systemId=${system.id}`)).map((p) => [p.code, p]));
  for (const [code, name, type, extra] of CONSOLE) {
    if (existing[code]) continue;
    existing[code] = await api(adminToken, '/api/permissions', 'POST', {
      systemId: system.id,
      code,
      name,
      type,
      menuPath: type === 'MENU' ? extra : null,
      parentId: type === 'BUTTON' ? existing[extra].id : null,
    });
  }
  const me = await api(adminToken, '/api/me');
  const granted = new Set(flatten(await api(adminToken, '/api/me/menu?system=rbac')).filter((n) => n.granted).map((n) => n.permission.code));
  if (CONSOLE.every(([code]) => granted.has(code))) return { system, perms: existing };
  let role = (await api(adminToken, '/api/roles')).find((r) => r.code === 'console-admin');
  if (!role) role = await api(adminToken, '/api/roles', 'POST', { code: 'console-admin', name: '权限管理员', description: '拥有权限系统控制台的全部菜单与按钮' });
  for (const [code] of CONSOLE) await api(adminToken, `/api/roles/${role.id}/permissions/${existing[code].id}`, 'PUT');
  await api(adminToken, `/api/users/${me.id}/roles/${role.id}`, 'PUT');
  return { system, perms: existing };
}

async function login(page, username, password) {
  await page.goto(base + '/ui/index.html');
  await page.getByRole('heading', { name: '登录工作区' }).waitFor();
  await page.getByLabel('登录名', { exact: true }).fill(username);
  await page.getByLabel('密码', { exact: true }).fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
}

async function idle(page) {
  await page.locator('#main-content[aria-busy=false]').waitFor();
}

async function nav(page, name) {
  await page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name, exact: false }).click();
  await page.getByRole('heading', { level: 1, name: new RegExp('^' + name) }).waitFor();
  await idle(page);
}

async function shot(page, name) {
  if (!out) return;
  await fs.mkdir(out, { recursive: true });
  await page.screenshot({ path: path.join(out, name + '.png'), fullPage: true });
}

async function record(name, data) {
  if (!out) return;
  await fs.mkdir(out, { recursive: true });
  await fs.writeFile(path.join(out, name), JSON.stringify({ generatedAt: new Date().toISOString(), base, ...data }, null, 2));
}

async function noOverflow(page) {
  return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
}

/** 最小合法 OOXML（仅一个 inlineStr 工作表，不压缩），只用于合成测试数据。 */
function xlsx(rows) {
  const escape = (v) => String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;');
  const col = (j) => String.fromCharCode(65 + j);
  const sheet =
    '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1"/><sheetData>' +
    rows.map((row, i) => `<row r="${i + 1}">` + row.map((v, j) => `<c r="${col(j)}${i + 1}" t="inlineStr"><is><t>${escape(v)}</t></is></c>`).join('') + '</row>').join('') +
    '</sheetData></worksheet>';
  const files = [
    ['[Content_Types].xml', '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>'],
    ['xl/worksheets/sheet1.xml', sheet],
  ];
  const parts = [];
  const central = [];
  let offset = 0;
  for (const [name, text] of files) {
    const data = Buffer.from(text, 'utf8');
    const nameBytes = Buffer.from(name, 'utf8');
    const crc = zlib.crc32(data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt32LE(0, 10);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBytes.length, 26);
    local.writeUInt16LE(0, 28);
    parts.push(local, nameBytes, data);
    const entry = Buffer.alloc(46);
    entry.writeUInt32LE(0x02014b50, 0);
    entry.writeUInt16LE(20, 4);
    entry.writeUInt16LE(20, 6);
    entry.writeUInt16LE(0x0800, 8);
    entry.writeUInt16LE(0, 10);
    entry.writeUInt32LE(0, 12);
    entry.writeUInt32LE(crc, 16);
    entry.writeUInt32LE(data.length, 20);
    entry.writeUInt32LE(data.length, 24);
    entry.writeUInt16LE(nameBytes.length, 28);
    entry.writeUInt32LE(offset, 42);
    central.push(entry, nameBytes);
    offset += 30 + nameBytes.length + data.length;
  }
  const centralBuffer = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(centralBuffer.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...parts, centralBuffer, end]);
}

async function tempDir() {
  return fs.mkdtemp(path.join(os.tmpdir(), 'permission-ui-'));
}

module.exports = { assert, base, admin, out, skip, launch, call, api, token, ensureConsole, CONSOLE, flatten, login, idle, nav, shot, record, noOverflow, xlsx, tempDir };
