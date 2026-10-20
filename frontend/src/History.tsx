import { useEffect, useRef, useState } from 'react';
import { api, message } from './api';
import { DetailPanel, StatusBadge, type RequestDetail } from './RequestDiagnostics';
interface Row {id:string;requestId:string;interfaceId:string;traceId:string;status:string;interfaceVersion:number|null;processingStage:string;createdAt:string;durationMs:number|null;errorCode:string|null}
interface Page {items:Row[];nextCursor:string|null}
export function History(){
 const [query,setQuery]=useState(''),[status,setStatus]=useState(''),[from,setFrom]=useState(''),[to,setTo]=useState('');
 const [filter,setFilter]=useState(''),[cursors,setCursors]=useState<(string|null)[]>([null]),[page,setPage]=useState(0),[reload,setReload]=useState(0);
 const [data,setData]=useState<Page|null>(null),[detail,setDetail]=useState<RequestDetail|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[detailBusy,setDetailBusy]=useState(false);
 const detailRequest=useRef<AbortController|null>(null);
 useEffect(()=>()=>detailRequest.current?.abort(),[]);
 useEffect(()=>{
  const controller=new AbortController();setBusy(true);setError('');setData(null);
  api<Page>(`/api/requests/scroll?size=10&${filter}${cursors[page]?`&cursor=${encodeURIComponent(cursors[page]!)}`:''}`,{signal:controller.signal})
   .then(value=>{if(!controller.signal.aborted)setData(value);}).catch(e=>{if(!controller.signal.aborted)setError(message(e));}).finally(()=>{if(!controller.signal.aborted)setBusy(false);});
  return()=>controller.abort();
 },[filter,cursors,page,reload]);
 function search(){
  try{
   const params=new URLSearchParams({query,status});
   if(from)params.set('from',new Date(from).toISOString());if(to)params.set('to',new Date(to).toISOString());
   if(from&&to&&new Date(from)>=new Date(to))throw new Error('시작은 종료보다 이전이어야 합니다.');
   detailRequest.current?.abort();setDetailBusy(false);setFilter(params.toString());setCursors([null]);setPage(0);setReload(v=>v+1);setDetail(null);
  }catch(e){setError(message(e));}
 }
 async function show(id:string){
  detailRequest.current?.abort();const controller=new AbortController();detailRequest.current=controller;setDetailBusy(true);setDetail(null);setError('');
  try{const value=await api<RequestDetail>(`/api/requests/${id}`,{signal:controller.signal});if(!controller.signal.aborted)setDetail(value);}catch(e){if(!controller.signal.aborted)setError(message(e));}finally{if(!controller.signal.aborted)setDetailBusy(false);}
 }
 function next(){if(data?.nextCursor){setCursors(v=>[...v.slice(0,page+1),data.nextCursor]);setPage(v=>v+1);}}
 return <><section className="intro"><div className="eyebrow">TRANSMISSION RECORDS</div><h1>전송 결과와 사용한 설정을 확인하세요.</h1><p>민감 필드는 마스킹됩니다. 내부 UUID로 상세를 조회하고 커서로 다음 이력을 탐색합니다.</p></section>
 <section className="panel"><h2>전송 이력</h2><form className="toolbar" onSubmit={e=>{e.preventDefault();search();}}>
 <label className="search-label">Request / Interface / Trace ID<input aria-label="이력 검색" value={query} onChange={e=>setQuery(e.target.value)} maxLength={160}/></label>
 <label>상태<select aria-label="이력 상태" value={status} onChange={e=>setStatus(e.target.value)}>{['','SUCCESS','VALIDATION_FAILED','MAPPING_FAILED','UNKNOWN','FAILED','SENDING','READY','VALIDATING'].map(v=><option key={v} value={v}>{v||'전체'}</option>)}</select></label>
 <label>시작<input aria-label="이력 시작" type="datetime-local" value={from} onChange={e=>setFrom(e.target.value)}/></label><label>종료<input aria-label="이력 종료" type="datetime-local" value={to} onChange={e=>setTo(e.target.value)}/></label><button disabled={busy}>검색 / 새로고침</button></form>
 {error&&<p className="error" role="alert">{error}</p>}{busy&&<p role="status">이력 조회 중…</p>}
 <p className="table-hint">표를 좌우로 스크롤하여 나머지 열을 확인하세요.</p><div className="table-scroll" role="region" aria-label="전송 이력 표" tabIndex={0} aria-busy={busy}><table><thead><tr><th>요청</th><th>인터페이스</th><th>상태 / 단계</th><th>소요시간 / 오류</th><th>설정</th><th>접수 시각</th></tr></thead><tbody>{data?.items.map(row=><tr key={row.id}><td><button onClick={()=>void show(row.id)}>{row.requestId}</button><small>{row.traceId}</small></td><td>{row.interfaceId}</td><td><StatusBadge value={row.status}/><small>{row.processingStage}</small></td><td>{row.durationMs??'미확인'} ms<small>{row.errorCode}</small></td><td>{row.interfaceVersion===null?'이전 기록':`v${row.interfaceVersion}`}</td><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td></tr>)}</tbody></table></div>
 {!busy&&data?.items.length===0&&<p>조회 결과가 없습니다.</p>}<div className="toolbar spaced"><button disabled={busy||page===0} onClick={()=>setPage(v=>v-1)}>이전</button><span>{page+1} 페이지 · 현재 {data?.items.length??0}건</span><button disabled={busy||!data?.nextCursor} onClick={next}>다음</button></div><p>접수 시각·UUID 순서로 조회합니다. 전체 건수 계산을 생략하며 새 기록은 검색 / 새로고침으로 확인합니다. 상태 변경은 페이지 사이에 반영될 수 있습니다.</p></section>
 {detailBusy&&<p role="status">상세 조회 중…</p>}{detail&&<DetailPanel data={detail}/>}</>;
}
