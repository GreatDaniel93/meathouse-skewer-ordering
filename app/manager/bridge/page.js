'use client';

import {useEffect,useState} from 'react';

function ageText(seconds){if(seconds==null)return 'No heartbeat yet';if(seconds<60)return `${seconds}s ago`;const m=Math.floor(seconds/60);return `${m}m ${seconds%60}s ago`;}
function dt(v){return v?new Date(v).toLocaleString():'—';}

export default function SystemHealth(){
  const [data,setData]=useState(null);const [error,setError]=useState('');
  async function load(){const r=await fetch('/api/manager/dashboard',{cache:'no-store'});const j=await r.json();if(!r.ok){setError(j.error||'Unable to load system health.');return;}setData(j.settings||{});setError('');}
  useEffect(()=>{load();const i=setInterval(load,15000);return()=>clearInterval(i)},[]);
  const bridge=data?.bridge_state||'unknown';const bridgeOnline=bridge==='online';const stuck=Number(data?.print_stuck_jobs||0);const pending=Number(data?.print_pending_jobs||0);const info=data?.bridge_device_info||{};
  return <main className="page">
    <section className="hero"><h1>System Health</h1><p>Live status for the ordering system, Android bridge and both kitchen printers.</p></section>
    {error&&<div className="error" style={{marginTop:14}}>{error}</div>}
    <div className="grid grid-3" style={{marginTop:16}}>
      <div className="card"><div className="muted">Android Bridge</div><div style={{fontSize:28,fontWeight:900,marginTop:6,color:bridgeOnline?'#2a7848':'#a12a2a'}}>{bridgeOnline?'● ONLINE':bridge==='offline'?'● OFFLINE':'● UNKNOWN'}</div><div className="muted" style={{fontSize:12,marginTop:5}}>{ageText(data?.bridge_heartbeat_age_seconds)}</div></div>
      <div className="card"><div className="muted">Pending print jobs</div><b style={{fontSize:30}}>{pending}</b><div className="muted" style={{fontSize:12}}>Oldest {data?.print_oldest_pending_seconds??0}s</div></div>
      <div className="card"><div className="muted">Stuck print jobs</div><b style={{fontSize:30,color:stuck>0?'#a12a2a':'#2a7848'}}>{stuck}</b><div className="muted" style={{fontSize:12}}>Red if waiting over 90 seconds</div></div>
    </div>
    <div className="grid grid-3" style={{marginTop:16}}>
      <div className="card"><div className="muted">Printer 1 last success</div><b>{dt(data?.printer1_last_success_at)}</b></div>
      <div className="card"><div className="muted">Printer 2 last success</div><b>{dt(data?.printer2_last_success_at)}</b></div>
      <div className="card"><div className="muted">Last customer order</div><b>{dt(data?.last_order_at)}</b></div>
    </div>
    <div className="card" style={{marginTop:16}}><h3 style={{marginTop:0}}>Bridge device</h3><div className="grid grid-3"><div><div className="muted">Manufacturer</div><b>{info.manufacturer||'—'}</b></div><div><div className="muted">Model</div><b>{info.model||'—'}</b></div><div><div className="muted">Android</div><b>{info.android?`${info.android} (SDK ${info.sdk||'?'})`:'—'}</b></div></div></div>
    {(bridge==='offline'||stuck>0)&&<div className="error" style={{marginTop:16}}><b>Attention required.</b> {bridge==='offline'?'The print bridge has stopped sending heartbeats. ':''}{stuck>0?`${stuck} print job(s) have been waiting over 90 seconds. Open Print Queue & History to investigate or reprint.`:''}</div>}
    <div className="actions" style={{marginTop:16}}><a className="btn brand" href="/manager/print">OPEN PRINT QUEUE & HISTORY</a><button className="btn secondary" onClick={load}>REFRESH NOW</button></div>
  </main>;
}
