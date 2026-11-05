import React from 'react';
import { Brand, Icon, Badge } from './UI';
export type PageName = 'inbox' | 'response' | 'history' | 'profile' | 'created' | 'builder' | 'explore' | 'landing';
export interface HeaderProps { page: PageName; navigate: (p:PageName)=>void }
const menus: Array<{label:string;page:PageName}>=[
 {label:'내 폼함',page:'inbox'},{label:'제출 내역',page:'history'},
 {label:'내 프로필',page:'profile'},{label:'내가 만든 폼',page:'created'}
];
export function Header({page,navigate}:HeaderProps){
 const [open,setOpen]=React.useState(false);
 return <header className="global-header">
   <Brand onClick={()=>navigate('landing')}/>
   <nav aria-label="주 메뉴" className="main-nav">{menus.map(m=><button key={m.page} onClick={()=>navigate(m.page)} className={`nav-link ${page===m.page || (page==='response'&&m.page==='inbox') || ((page==='builder'||page==='explore')&&m.page==='created')?'active':''}`}>{m.label}</button>)}</nav>
   <div className="account-actions"><Badge>데모 워크스페이스</Badge><button className="icon-btn" aria-label="알림 보기" onClick={()=>setOpen(!open)}><Icon name="bell"/></button><span className="avatar">지</span><span className="account-name">김지윤</span><button className="icon-btn" aria-label="계정 메뉴" onClick={()=>setOpen(!open)}><Icon name="chevron" size={15}/></button>{open&&<div className="account-popover">이 화면은 가상 데이터 미리보기입니다.<br/>실제 로그인은 백엔드 연동이 필요합니다.</div>}</div>
 </header>;
}
