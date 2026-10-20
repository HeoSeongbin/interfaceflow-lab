import { jsonChanges } from './diagnostics';
import { pretty } from './api';
export function StatusBadge({value}:{value:string}){
 const tone=['SUCCESS','PASSED','DONE'].includes(value)?'good':['UNKNOWN','DEAD_LETTER'].includes(value)?'uncertain':['FAILED','VALIDATION_FAILED','MAPPING_FAILED'].includes(value)?'bad':'pending';
 return <span className={`status-badge ${tone}`}>{value}</span>;
}
export function PayloadComparison({before,after}:{before:unknown;after:unknown}){
 if(before===null||before===undefined)return <p>이전 기록에는 비교할 전문이 없습니다.</p>;
 if(after===null||after===undefined)return <p>변환 결과가 없습니다. 실패 단계와 검증 오류를 확인하세요.</p>;
 const changes=jsonChanges(before,after);
 return <section aria-label="전문 비교"><h3>변환 전후 비교</h3><p>마스킹된 진단 JSON의 경로별 차이입니다. 없던 필드와 null을 구분하며 실제 전송 원본이 아닙니다. 최대 500개 차이만 표시합니다.</p>
 {changes.items.length===0?<p>표시 범위에서 변경된 필드가 없습니다.</p>:<div className="table-scroll" role="region" aria-label="전문 차이 표" tabIndex={0}><table className="diff-table"><thead><tr><th>JSON 경로 / 종류</th><th>변환 전</th><th>변환 후</th></tr></thead><tbody>{changes.items.map(c=><tr key={c.path}><td><code>{c.path||"(root)"}</code><small>{c.kind}</small></td><td><code>{c.beforePresent?pretty(c.before):'∅ 필드 없음'}</code></td><td><code>{c.afterPresent?pretty(c.after):'∅ 필드 없음'}</code></td></tr>)}</tbody></table></div>}
 {changes.truncated&&<p role="status">표시 한도를 초과했습니다. 전체 진단 JSON을 확인하세요.</p>}</section>;
}
export interface RequestDetail {id:string;requestId:string;traceId:string;status:string;originalPayload:unknown;mappedPayload:unknown;[key:string]:unknown}
export function DetailPanel({data}:{data:RequestDetail}){return <section className="panel spaced"><h2>요청 상세 · 마스킹된 전문과 설정 스냅샷</h2><div className="detail-meta"><StatusBadge value={data.status}/><span>Request ID: <code>{data.requestId}</code></span><span>Trace ID: <code>{data.traceId}</code></span></div><PayloadComparison before={data.originalPayload} after={data.mappedPayload}/><details open><summary>전체 진단 JSON · 설정·응답·검증 오류</summary><pre>{pretty(data)}</pre></details></section>;}
