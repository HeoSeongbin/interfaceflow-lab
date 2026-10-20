import { it,expect,vi,afterEach } from 'vitest';
import { api,ApiError } from './api';
afterEach(()=>vi.unstubAllGlobals());
it('preserves HTTP, error code and validated trace for API feedback',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'PAYLOAD_CONFLICT',traceId:'a'.repeat(32)}),{status:409,headers:{'Content-Type':'application/json'}})));try{await api('/api/requests');throw new Error('expected error');}catch(e){expect(e).toBeInstanceOf(ApiError);expect(e).toMatchObject({status:409,code:'PAYLOAD_CONFLICT'});expect((e as Error).message).toContain('a'.repeat(32));}});
it('handles an unstructured JSON error without replacing the HTTP status',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('null',{status:503,headers:{'Content-Type':'application/json'}})));await expect(api('/api/requests')).rejects.toMatchObject({status:503,code:'HTTP_ERROR'});});
