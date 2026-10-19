import { useEffect, useState } from 'react';
import { api, message, pretty } from './api';

interface Row { id:string; requestId:string; interfaceId:string; traceId:string; status:string; interfaceVersion:number|null; processingStage:string; createdAt:string;durationMs:number|null;errorCode:string|null }
interface Page { items:Row[]; total:number; page:number; size:number }
export function History() {
  const [from,setFrom]=useState(''),[to,setTo]=useState('');
  const [query,setQuery]=useState(''),[filter,setFilter]=useState(''),[status,setStatus]=useState(''),[page,setPage]=useState(0),[reload,setReload]=useState(0);
  const [data,setData]=useState<Page|null>(null),[detail,setDetail]=useState<unknown>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  useEffect(()=>{let active=true;setBusy(true);setError('');api<Page>(`/api/requests?query=${encodeURIComponent(filter)}&status=${encodeURIComponent(status)}&page=${page}&size=10${from?`&from=${encodeURIComponent(new Date(from).toISOString())}`:''}${to?`&to=${encodeURIComponent(new Date(to).toISOString())}`:''}`).then(value=>{if(active)setData(value);}).catch(e=>{if(active)setError(message(e));}).finally(()=>{if(active)setBusy(false);});return()=>{active=false;};},[filter,status,page,reload,from,to]);
  async function show(id:string){try{setError('');setDetail(await api(`/api/requests/${id}`));}catch(e){setError(message(e));}}
  return <><section className="intro"><div className="eyebrow">TRANSMISSION RECORDS</div><h1>전송 결과와 사용한 설정을 확인하세요.</h1><p>민감 필드는 마스킹됩니다. 상세 조회 키는 논리 Request ID와 구분되는 내부 UUID입니다.</p></section>
    <section className="panel"><h2>전송 이력</h2><form className="toolbar" onSubmit={e=>{e.preventDefault();setFilter(query);setPage(0);setReload(v=>v+1);}}><label className="search-label">Request / Interface / Trace ID<input aria-label="이력 검색" value={query} onChange={e=>setQuery(e.target.value)} maxLength={160}/></label><label>상태<select aria-label="이력 상태" value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}>{['','SUCCESS','VALIDATION_FAILED','MAPPING_FAILED','UNKNOWN','FAILED','SENDING','READY','VALIDATING'].map(v=><option key={v} value={v}>{v||'전체'}</option>)}</select></label><label>시작<input aria-label="이력 시작" type="datetime-local" value={from} onChange={e=>{setFrom(e.target.value);setPage(0);}}/></label><label>종료<input aria-label="이력 종료" type="datetime-local" value={to} onChange={e=>{setTo(e.target.value);setPage(0);}}/></label><button disabled={busy}>검색 / 새로고침</button></form>
    {error&&<p className="error" role="alert">{error}</p>}{busy&&<p role="status">이력 조회 중…</p>}
    <p className="table-hint">표를 좌우로 스크롤하여 나머지 열을 확인하세요.</p><div className="table-scroll"><table><thead><tr><th>요청</th><th>인터페이스</th><th>상태 / 단계</th><th>소요시간 / 오류</th><th>설정</th><th>접수 시각</th></tr></thead><tbody>{data?.items.map(row=><tr key={row.id}><td><button onClick={()=>show(row.id)}>{row.requestId}</button><small>{row.traceId}</small></td><td>{row.interfaceId}</td><td>{row.status}<small>{row.processingStage}</small></td><td>{row.durationMs??"미확인"} ms<small>{row.errorCode}</small></td><td>{row.interfaceVersion===null?'이전 기록':`v${row.interfaceVersion}`}</td><td>{new Date(row.createdAt).toLocaleString('ko-KR')}</td></tr>)}</tbody></table></div>
    {!busy&&data?.items.length===0&&<p>조회 결과가 없습니다.</p>}<div className="toolbar spaced"><button disabled={busy||page===0} onClick={()=>setPage(page-1)}>이전</button><span>{page+1} 페이지 · 총 {data?.total??0}건</span><button disabled={busy||!data||(page+1)*10>=data.total} onClick={()=>setPage(page+1)}>다음</button></div></section>
    {detail!==null&&<section className="panel spaced"><h2>요청 상세 · 마스킹된 전문과 설정 스냅샷</h2><pre>{pretty(detail)}</pre></section>}
  </>;
}
