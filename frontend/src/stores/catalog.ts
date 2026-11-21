import { reactive } from 'vue';
import { departmentsApi } from '@/api/departments';
import { permissionsApi } from '@/api/permissions';
import { resourcesApi } from '@/api/resources';
import { rolesApi } from '@/api/roles';
import { systemsApi } from '@/api/systems';
import type { Department, Edge, Permission, Resource, Role, System } from '@/api/types';

/** 目录数据：按需加载、在会话内复用；写操作后按需强制刷新；退出时清空。 */
export const catalog = reactive({
  systems: null as System[] | null,
  roles: null as Role[] | null,
  departments: null as Department[] | null,
  permissions: null as Permission[] | null,
  resources: null as Resource[] | null,
  edges: null as Edge[] | null,
});

type Key = keyof typeof catalog;

const loaders: Record<Key, () => Promise<unknown>> = {
  systems: systemsApi.list,
  roles: rolesApi.list,
  departments: departmentsApi.list,
  permissions: () => permissionsApi.list(),
  resources: resourcesApi.list,
  edges: rolesApi.hierarchy,
};

let generation = 0;

/** 加载指定目录；force 时忽略缓存。退出后返回的结果不会写回。 */
export async function load(keys: Key[], force = false): Promise<void> {
  const mine = generation;
  const wanted = keys.filter((key) => force || catalog[key] === null);
  const values = await Promise.all(wanted.map((key) => loaders[key]()));
  if (mine !== generation) return;
  wanted.forEach((key, index) => {
    (catalog as Record<Key, unknown>)[key] = values[index];
  });
}

export function resetCatalog(): void {
  generation++;
  for (const key of Object.keys(catalog) as Key[]) catalog[key] = null;
}

export const roleName = (id: number): string =>
  catalog.roles?.find((role) => role.id === id)?.name ?? `角色 ${id}`;

export const systemName = (id: number | null | undefined): string =>
  catalog.systems?.find((system) => system.id === id)?.name ?? '—';

export const departmentName = (id: number | null | undefined): string =>
  id == null ? '—' : (catalog.departments?.find((d) => d.id === id)?.name ?? '—');
