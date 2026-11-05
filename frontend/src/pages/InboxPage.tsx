import React from 'react';
import { Badge, Button, Heading, Icon, Panel } from '../components/UI';
import type { SavedFormSummary } from '../types/api';

interface Props { forms: SavedFormSummary[]; onOpen: (id:string)=>void; onCreated:()=>void }
const owners=['아틀리에 · 피플팀','아틀리에 · 피플팀','아틀리에 · 운영팀','아틀리에 · 컬처팀'];
export function InboxPage({forms,onOpen,onCreated}:Props){
 const [tab,setTab]=React.useState<'all'|'pending'|'draft'>('all');
 const [filter,setFilter]=React.useState('');
 const [email,setEmail]=React.useState(true);
 const [site,setSite]=React.useState(true);
 const shown=forms.filter(x=>(tab==='all'||(tab==='draft'?x.has_draft:x.state==='saved'&&!x.has_draft))&&x.title.toLowerCase().includes(filter.toLowerCase()));
 return <div className="page-shell">
  <Heading eyebrow="내 워크스페이스  /  내 폼함" title="내 폼함" desc="받은 질문을 모아두고, 내 속도로 답하세요." action={<Button onClick={onCreated}><Icon name="plus" size={16}/> 받은 폼 저장</Button>}/>
  <div className="metric-grid">{[
   ['저장한 폼',String(forms.length),'내게 도착한 질문'],['작성 중',String(forms.filter(f=>f.has_draft).length),'이어서 작성할 수 있어요'],['마감 임박','2','이번 주에 마감돼요'],['제출 완료','8','답변을 다시 볼 수 있어요']
  ].map(([t,v,d])=><Panel key={t} className="metric"><div className="metric-top"><span>{t}</span><strong>{v}</strong></div><small>{d}</small></Panel>)}</div>
  <div className="tab-bar">{([['all','전체'],['pending','미제출'],['draft','작성 중']] as const).map(([k,t])=><button key={k} onClick={()=>setTab(k)} className={tab===k?'selected':''}>{t} <span>{k==='all'?forms.length:k==='draft'?forms.filter(f=>f.has_draft).length:forms.filter(f=>!f.has_draft).length}</span></button>)}</div>
  <div className="list-tools"><label className="search-inline"><Icon name="search" size={16}/><input aria-label="폼 검색" placeholder="폼 이름 또는 보낸 사람 검색" value={filter} onChange={e=>setFilter(e.target.value)}/></label><span>작성 중 우선 · 마감순 ↓</span></div>
  <div className="form-list">{shown.map((f,i)=><Panel className={`saved-form ${f.has_draft?'resume':''}`} key={f.form_id}>
   {f.has_draft&&<div className="card-status"><Badge tone="blue">작성 중</Badge><Badge tone="amber">마감 임박 · D-2</Badge><span className="corner-bookmark"><Icon name="bookmark" size={17}/></span></div>}
   <div className="form-line"><div><h3>{f.title}</h3><div className="muted small">{owners[forms.indexOf(f)]||'Passform 데모팀'} · {f.deadline?new Date(f.deadline).toLocaleDateString('ko-KR'):'마감 없음'} 마감</div>{!f.has_draft&&<div className="small muted" style={{marginTop:10}}>{i===2?'약 3분 · 6문항':'아직 작성하지 않았어요'}</div>}</div><Button variant={f.has_draft?'primary':'secondary'} onClick={()=>onOpen(f.form_id)}>{f.has_draft?'이어서 작성':'작성 시작'} {f.has_draft&&<Icon name="arrow" size={15}/>}</Button></div>
   {f.has_draft&&<div className="progress-wrap"><div className="progress-label"><span>3 / 5문항 작성</span><b>60%</b></div><div className="track"><div style={{width:'60%'}}/></div><small className="muted">오늘 10:32 저장됨 · 작성한 내용부터 이어집니다</small></div>}
  </Panel>)}{shown.length===0&&<Panel><p className="muted">검색 결과가 없어요.</p></Panel>}</div>
  <Panel className="reminder"><div><h3>마감 하루 전, 잊지 않도록</h3><p className="muted small">저장한 미제출 폼에만 알려드려요. 채널별로 언제든 끌 수 있어요.</p></div><div className="toggle-line"><label>이메일 <input type="checkbox" checked={email} onChange={e=>setEmail(e.target.checked)} /></label><label>사이트 내 알림 <input type="checkbox" checked={site} onChange={e=>setSite(e.target.checked)} /></label></div></Panel>
 </div>;
}
