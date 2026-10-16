export function parsePayload(text:string):unknown {
 const value:unknown=JSON.parse(text);
 if(value===null || Array.isArray(value) || typeof value!=='object') throw new Error('JSON 객체를 입력하세요.');
 return value;
}
