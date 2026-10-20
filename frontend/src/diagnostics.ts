export interface Change { path:string; before:unknown; after:unknown; beforePresent:boolean; afterPresent:boolean; kind:'추가'|'제거'|'변경' }
export function jsonChanges(before:unknown,after:unknown,limit=500):{items:Change[];truncated:boolean}{
 const a=new Map<string,unknown>(),b=new Map<string,unknown>();let truncated=false;
 function walk(value:unknown,path:string,out:Map<string,unknown>,depth=0){
  if(out.size>=limit*4){truncated=true;return;}
  if(depth>64){out.set(path,'[깊이 제한]');truncated=true;return;}
  if(value!==null&&typeof value==='object'&&Object.keys(value).length){
   for(const [key,child] of Object.entries(value))walk(child,path+'/'+key.replace(/~/g,'~0').replace(/\//g,'~1'),out,depth+1);
  }else out.set(path,value);
 }
 walk(before,'',a);walk(after,'',b);
 const items:Change[]=[];
 for(const path of [...new Set([...a.keys(),...b.keys()])].sort()){
  const beforePresent=a.has(path),afterPresent=b.has(path),x=a.get(path),y=b.get(path);
  if(beforePresent&&afterPresent&&JSON.stringify(x)===JSON.stringify(y))continue;
  if(items.length===limit){truncated=true;break;}
  items.push({path,before:x,after:y,beforePresent,afterPresent,kind:!beforePresent?'추가':!afterPresent?'제거':'변경'});
 }
 return {items,truncated};
}
export interface Bucket {hour:string;status:string;count:number}
export interface Hour {hour:string;SUCCESS:number;FAILED:number;UNKNOWN:number;PROGRESS:number}
export function hours(buckets:Bucket[],from:string,to:string):Hour[]{
 const start=Date.parse(from),end=Date.parse(to);
 if(!Number.isFinite(start)||!Number.isFinite(end)||start>=end||end-start>31*86400000)return [];
 const map=new Map<number,Hour>();
 for(let time=Math.floor(start/3600000)*3600000;time<end;time+=3600000)map.set(time,{hour:new Date(time).toISOString(),SUCCESS:0,FAILED:0,UNKNOWN:0,PROGRESS:0});
 for(const b of buckets){const time=Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(b.hour)?b.hour:b.hour+'Z'),row=map.get(time);if(!row)continue;
  if(b.status==='SUCCESS')row.SUCCESS+=b.count;else if(['FAILED','VALIDATION_FAILED','MAPPING_FAILED'].includes(b.status))row.FAILED+=b.count;else if(b.status==='UNKNOWN')row.UNKNOWN+=b.count;else row.PROGRESS+=b.count;
 }
 return [...map.values()];
}
