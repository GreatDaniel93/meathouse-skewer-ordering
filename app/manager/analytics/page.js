'use client';

import {useEffect,useState} from 'react';

export default function Analytics(){
  const today=new Date().toISOString().slice(0,10);const weekAgo=new Date(Date.now()-6*86400000).toISOString().slice(0,10);
  const [from,setFrom]=useState(weekAgo),[to,setTo]=useState(today),[data,setData]=useState(null),[error,setError]=useState('');
  async function load(){const fromIso=new Date(`${from}T00:00:00+10:00`).toISOString();const toIso=new Date(new Date(`${to}T00:00:00+10:00`).getTime()+86400000).toISOString();const r=await fetch(`/api/manager/dashboard?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`,{cache:'no-store'});const j=await r.json();if(!r.ok){setError(j.error||'Unable to load analytics.');return;}setData(j.analytics);setError('');}
  useEffect(()=>{load()},[]);
  return <main className="page">
    <section className="hero"><h1>Operations Dashboard</h1><p>Skewer demand, guest behaviour and service flow.</p></section>
    {error&&<div className="error" style={{marginTop:14}}>{error}</div>}
    <div className="card" style={{marginTop:16}}><div className="actions"><div className="field"><label>From</label><input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></div><div className="field"><label>To</label><input type="date" value={to} onChange={e=>setTo(e.target.value)}/></div><button className="btn brand" onClick={load}>RUN REPORT</button></div></div>
    <div className="grid grid-3" style={{marginTop:16}}>
      <div className="card"><div className="muted">Guests</div><b style={{fontSize:30}}>{data?.guests??'—'}</b></div>
      <div className="card"><div className="muted">Sessions</div><b style={{fontSize:30}}>{data?.sessions??'—'}</b></div>
      <div className="card"><div className="muted">Orders</div><b style={{fontSize:30}}>{data?.orders??'—'}</b></div>
      <div className="card"><div className="muted">Skewers</div><b style={{fontSize:30}}>{data?.skewers??'—'}</b></div>
      <div className="card"><div className="muted">Skewers / guest</div><b style={{fontSize:30}}>{data?.skewers_per_guest??'—'}</b></div>
      <div className="card"><div className="muted">Orders / session</div><b style={{fontSize:30}}>{data?.orders_per_session??'—'}</b></div>
      <div className="card"><div className="muted">Skewers / order</div><b style={{fontSize:30}}>{data?.skewers_per_order??'—'}</b></div>
      <div className="card"><div className="muted">Lucky Skewer value</div><b style={{fontSize:30}}>${Number(data?.voucher_value||0).toFixed(2)}</b></div>
    </div>
    <div className="grid grid-2" style={{marginTop:16}}>
      <div className="card"><h3 style={{marginTop:0}}>Top skewers</h3>{data?.top_items?.length?data.top_items.map((x,i)=><div className="actions" key={i} style={{padding:'7px 0',borderBottom:'1px solid var(--line)'}}><span>{x.name}</span><span className="spacer"/><b>{x.qty}</b></div>):<span className="muted">No data</span>}</div>
      <div className="card"><h3 style={{marginTop:0}}>Highest consumption tables</h3>{data?.top_tables?.length?data.top_tables.map((x,i)=><div className="actions" key={i} style={{padding:'7px 0',borderBottom:'1px solid var(--line)'}}><span>{x.table}</span><span className="spacer"/><b>{x.skewers}</b></div>):<span className="muted">No data</span>}</div>
    </div>
    <div className="card" style={{marginTop:16}}><h3 style={{marginTop:0}}>Hourly order flow</h3>{data?.hourly_orders?.length?data.hourly_orders.map((x,i)=><div className="actions" key={i} style={{padding:'7px 0',borderBottom:'1px solid var(--line)'}}><span>{x.hour}</span><span className="spacer"/><span>{x.orders} orders</span><b>{x.skewers} skewers</b></div>):<span className="muted">No data</span>}</div>
  </main>;
}
