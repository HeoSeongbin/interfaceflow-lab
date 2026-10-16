import { expect,test } from 'vitest';
import {parsePayload} from './json';
test('JSON 객체만 전문으로 허용한다',()=>{expect(parsePayload('{"userId":"U001"}')).toEqual({userId:'U001'});for(const v of ['null','[]','1','{']) expect(()=>parsePayload(v)).toThrow();});
