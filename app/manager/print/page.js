'use client';

import {useEffect,useState} from 'react';

function age(s){if(s==null)return '—';if(s<60)return `${s}s`;const m=Math.floor(s/60);return `${m}m ${s%60}s`;}
function dt(v){return v?new Date(v).toLocaleString():'—';}

export default function PrintManager(){
  const [data,setData]=useState(null);const [error,setError]=useState('');const [busy,setBusy]=useState('');
  async function load(){const r=await fetch('/api/manager/print',{cache:'no-store'});const j=await r.json();if(!r.ok){setError(j.error||'Unable to load print queue.');return;}setData(j);setError('');}
  useEffect(()=>{load();const i=setInterval(load,10000);return()=>clearInterval(i)},[]);
  async function reprint(job,printer){const key=`${job}-${printer}`;setBusy(key);const r=await fetch('/api/manager/print',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({job_id:job,printer})});const j=await r.json();setBusy('');if(!r.ok){setError(j.error||'Reprint failed.');return;}await load();}
  const jobs=data?.jobs||[];
  return <main className="page">
    <section className="hero"><h1>Print Queue & History</h1><p>See stuck jobs, printing history and manually reprint to either kitchen printer.</p></section>
    {error&&<div className="error" style={{marginTop:14}}>{error}</div>}
    <div className="grid grid-3" style={{marginTop:16}}>
      <div className="card"><div className="muted">Pending jobs</div><b style={{fontSize:30}}>{data?.pending_jobs??'—'}</b></div>
      <div className="card"><div className="muted">Stuck over 90s</div><b style={{fontSize:30,color:(data?.stuck_jobs||0)>0?'#a12a2a':'inherit'}}>{data?.stuck_jobs??'—'}</b></div>
      <div className="card"><div className="muted">Oldest pending</div><b style={{fontSize:30}}>{age(data?.oldest_pending_seconds)}</b></div>
    </div>
    <div className="grid grid-2" style={{marginTop:16}}>
      <div className="card"><div className="muted">Printer 1 last success</div><b>{dt(data?.printer1_last_success_at)}</b></div>
      <div className="card"><div className="muted">Printer 2 last success</div><b>{dt(data?.printer2_last_success_at)}</b></div>
    </div>
    <div className="section-title"><h3>Latest 100 print jobs</h3></div>
    <div style={{display:'grid',gap:10}}>{jobs.map(j=>{
      const stuck=!j.completed_at&&j.age_seconds>90;
      return <div className="card" key={j.job_id} style={stuck?{border:'2px solid #b93434'}:undefined}>
        <div className="actions"><div><b>{j.table_name} · Round {j.round_no}</b><div className="muted" style={{fontSize:12}}>{dt(j.created_at)} · age {age(j.age_seconds)}</div></div><span className={`badge ${j.completed_at?'available':'new'}`}>{j.completed_at?'COMPLETED':stuck?'STUCK':'PENDING'}</span><span className="spacer"/></div>
        <div style={{fontSize:13,marginTop:9}}>{(j.items||[]).map(x=>`${x.name} ×${x.qty}`).join(' · ')||'No items'}</div>
        <div className="grid grid-2" style={{marginTop:12}}>
          <div><div className="muted">Printer 1</div><b>{j.printer1_done?'✓ Printed':'Waiting'}</b><div className="muted" style={{fontSize:11}}>{dt(j.printer1_done_at)}</div></div>
          <div><div className="muted">Printer 2</div><b>{j.printer2_done?'✓ Printed':'Waiting'}</b><div className="muted" style={{fontSize:11}}>{dt(j.printer2_done_at)}</div></div>
        </div>
        <div className="actions" style={{marginTop:12}}><button className="btn secondary small" disabled={!!busy} onClick={()=>reprint(j.job_id,1)}>{busy===`${j.job_id}-1`?'QUEUED…':'REPRINT P1'}</button><button className="btn secondary small" disabled={!!busy} onClick={()=>reprint(j.job_id,2)}>{busy===`${j.job_id}-2`?'QUEUED…':'REPRINT P2'}</button><button className="btn brand small" disabled={!!busy} onClick={()=>reprint(j.job_id,0)}>{busy===`${j.job_id}-0`?'QUEUED…':'REPRINT BOTH'}</button></div>
      </div>})}</div>
  </main>;
}
