import type { Edge, Role } from '@/api/types';

/** 高级角色 → 直接继承的角色。 */
export function juniorsOf(edges: Edge[], id: number): number[] {
  return edges.filter((e) => e.seniorRoleId === id).map((e) => e.inheritedRoleId);
}

export function seniorsOf(edges: Edge[], id: number): number[] {
  return edges.filter((e) => e.inheritedRoleId === id).map((e) => e.seniorRoleId);
}

/**
 * 若让 senior 继承 junior 会成环，返回环路（senior → junior → … → senior），否则返回 null。
 * 与后端一致：环检测包含停用角色。
 */
export function cyclePath(edges: Edge[], senior: number, junior: number): number[] | null {
  if (senior === junior) return [senior, senior];
  const prev = new Map<number, number | null>([[junior, null]]);
  const queue = [junior];
  while (queue.length) {
    const current = queue.shift()!;
    if (current === senior) {
      const back: number[] = [];
      for (let x: number | null = senior; x !== null; x = prev.get(x) ?? null) back.push(x);
      return [senior, ...back.reverse()];
    }
    for (const next of juniorsOf(edges, current))
      if (!prev.has(next)) {
        prev.set(next, current);
        queue.push(next);
      }
  }
  return null;
}

/** 选中角色沿启用角色可达的继承边，用于高亮继承链。 */
export function chainEdges(edges: Edge[], roles: Role[], start: number): Set<string> {
  const enabled = new Set(roles.filter((r) => r.enabled).map((r) => r.id));
  const seen = new Set([start]);
  const result = new Set<string>();
  const queue = [start];
  while (queue.length) {
    const current = queue.shift()!;
    for (const next of juniorsOf(edges, current)) {
      result.add(`${current}>${next}`);
      if (!seen.has(next) && enabled.has(next)) {
        seen.add(next);
        queue.push(next);
      }
    }
  }
  return result;
}

export interface Placed {
  id: number;
  layer: number;
  x: number;
}

/**
 * 分层布局：层号 = 到基础角色的最长路径（基础角色在最下层）；
 * 层内按下层邻居的重心排序并保持最小间距。只布置参与继承的角色。
 */
export function layout(
  roles: Role[],
  edges: Edge[],
  slot: number,
): { nodes: Placed[]; layers: number; width: number } {
  const ids = roles
    .map((r) => r.id)
    .filter((id) => edges.some((e) => e.seniorRoleId === id || e.inheritedRoleId === id));
  const memo = new Map<number, number>();
  const visiting = new Set<number>();
  const depth = (id: number): number => {
    if (memo.has(id)) return memo.get(id)!;
    if (visiting.has(id)) return 0;
    visiting.add(id);
    const below = juniorsOf(edges, id);
    const value = below.length ? 1 + Math.max(...below.map(depth)) : 0;
    visiting.delete(id);
    memo.set(id, value);
    return value;
  };
  ids.forEach(depth);
  const layers = ids.length ? Math.max(...ids.map(depth)) + 1 : 0;
  const x = new Map<number, number>();
  let width = 0;
  for (let layer = 0; layer < layers; layer++) {
    const row = ids.filter((id) => depth(id) === layer);
    const want = row
      .map((id, i) => {
        const below = juniorsOf(edges, id).filter((j) => x.has(j));
        const w = below.length ? below.reduce((s, j) => s + x.get(j)!, 0) / below.length : i * slot;
        return { id, i, w };
      })
      .sort((a, b) => a.w - b.w || a.i - b.i);
    const pos = want.map((o) => o.w);
    for (let k = 1; k < pos.length; k++) pos[k] = Math.max(pos[k], pos[k - 1] + slot);
    const shift =
      want.reduce((s, o) => s + o.w, 0) / want.length - pos.reduce((s, p) => s + p, 0) / pos.length;
    want.forEach((o, k) => x.set(o.id, pos[k] + shift));
  }
  const min = Math.min(0, ...x.values());
  for (const [id, value] of x) {
    x.set(id, value - min);
    width = Math.max(width, value - min + slot);
  }
  return { nodes: ids.map((id) => ({ id, layer: depth(id), x: x.get(id)! })), layers, width };
}
