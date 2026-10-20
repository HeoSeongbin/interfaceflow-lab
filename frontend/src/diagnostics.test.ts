import { describe,it,expect } from 'vitest';
import { jsonChanges,hours } from './diagnostics';
describe('masked diagnostic comparisons',()=>{
 it('distinguishes absent, null, false and zero',()=>{const result=jsonChanges({a:null,b:false,c:0},{b:0,d:null});expect(result.items.map(c=>[c.path,c.kind])).toEqual([['/a','제거'],['/b','변경'],['/c','제거'],['/d','추가']]);expect(result.items[0].beforePresent).toBe(true);expect(result.items[0].afterPresent).toBe(false);});
 it('escapes paths and compares array positions',()=>{expect(jsonChanges({'a/b~c':[1,2]},{'a/b~c':[2,1]}).items.map(c=>c.path)).toEqual(['/a~1b~0c/0','/a~1b~0c/1']);});
 it('ignores object order and preserves empty containers',()=>{expect(jsonChanges({a:1,b:2},{b:2,a:1}).items).toEqual([]);expect(jsonChanges([] ,{}).items).toHaveLength(1);expect(jsonChanges({}, {'':null}).items.map(c=>c.path)).toEqual(['','/']);});
 it('limits output without claiming a complete comparison',()=>{expect(jsonChanges({a:1,b:2},{a:3,b:4},1)).toMatchObject({truncated:true,items:[{path:'/a'}]});});
});
describe('UTC hourly monitoring',()=>{
 it('fills missing hours and keeps unknown separate from failed',()=>{const rows=hours([{hour:'2026-10-20T00:00:00',status:'UNKNOWN',count:2},{hour:'2026-10-20T02:00:00',status:'VALIDATION_FAILED',count:1}],'2026-10-20T00:00:00Z','2026-10-20T03:00:00Z');expect(rows).toHaveLength(3);expect(rows[0].UNKNOWN).toBe(2);expect(rows[0].FAILED).toBe(0);expect(rows[1].SUCCESS).toBe(0);expect(rows[2].FAILED).toBe(1);});
 it('uses half-open bounds and rejects invalid ranges',()=>{expect(hours([],'2026-10-20T00:00:00Z','2026-10-20T01:00:00Z')).toHaveLength(1);expect(hours([],'bad','bad')).toEqual([]);expect(hours([],'2026-01-01T00:00:00Z','2026-03-01T00:00:00Z')).toEqual([]);});
});
