'use client';
import {useEffect,useState} from 'react';

export default function SkewerPlans(){
  const [data,setData]=useState(null);const[error,setError]=useState('');const[saving,setSaving]=useState(false);const[msg,setMsg]=useState('');
  async function load(){const r=await fetch('/api/manager/skewer-plans',{cache:'no-store'});const j=await r.json();if(!r.ok){setError(j.error||'Unable to load settings.');return}setData(j);setError('')}
  useEffect(()=>{load()},[]);
  async function save(){setSaving(true);setMsg('');setError('');const r=await fetch('/api/manager/skewer-plans',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({included_cooldown_minutes:data.included.cooldown_minutes,included_rate_per_guest:data.included.rate_per_guest,paid_cooldown_minutes:data.paid.cooldown_minutes,paid_rate_per_guest:data.paid.rate_per_guest})});const j=await r.json();setSaving(false);if(!r.ok){setError(j.error||'Save failed.');return}setData(j);setMsg('Saved. Active tables using each plan were updated too.')}
  function field(plan,key,v){setData(d=>({...d,[plan]:{...d[plan],[key]:Number(v)}}))}
  if(!data)return <main className="page">Loading…</main>;
  return <main className="page">
    <section className="hero"><h1>Skewer Plans</h1><p>Separate ordering rules for included and paid skewer access.</p></section>
    {error&&<div className="error" style={{marginTop:14}}>{error}</div>}{msg&&<div className="notice" style={{marginTop:14}}>{msg}</div>}
    <div className="grid grid-2" style={{marginTop:16}}>
      <div className="card"><div className="badge available">INCLUDED / NO EXTRA PAYMENT</div><h2>Included Skewers</h2><p className="muted">For guests who did not purchase the paid skewer option. Keep this deliberately limited.</p><div className="field"><label>Cooldown between rounds (minutes)</label><input type="number" min="0" max="90" value={data.included.cooldown_minutes} onChange={e=>field('included','cooldown_minutes',e.target.value)}/></div><div className="field" style={{marginTop:12}}><label>Skewers per person / round</label><input type="number" min="1" max="100" value={data.included.rate_per_guest} onChange={e=>field('included','rate_per_guest',e.target.value)}/></div><div className="notice" style={{marginTop:12}}>Example: 4 guests × {data.included.rate_per_guest} = {4*data.included.rate_per_guest} skewers per round.</div></div>
      <div className="card"><div className="badge new">PAID SKEWER OPTION</div><h2>Paid Skewers</h2><p className="muted">For guests who paid for skewer access. These rules are controlled independently.</p><div className="field"><label>Cooldown between rounds (minutes)</label><input type="number" min="0" max="90" value={data.paid.cooldown_minutes} onChange={e=>field('paid','cooldown_minutes',e.target.value)}/></div><div className="field" style={{marginTop:12}}><label>Skewers per person / round</label><input type="number" min="1" max="100" value={data.paid.rate_per_guest} onChange={e=>field('paid','rate_per_guest',e.target.value)}/></div><div className="notice" style={{marginTop:12}}>Example: 4 guests × {data.paid.rate_per_guest} = {4*data.paid.rate_per_guest} skewers per round.</div></div>
    </div>
    <button className="btn brand" disabled={saving} onClick={save} style={{marginTop:16,minWidth:180}}>{saving?'SAVING…':'SAVE BOTH PLANS'}</button>
  </main>
}
