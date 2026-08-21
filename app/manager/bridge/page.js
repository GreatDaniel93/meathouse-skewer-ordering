'use client';

import {useEffect,useState} from 'react';

function ageText(seconds){
  if(seconds==null)return 'No heartbeat received yet';
  if(seconds<60)return `${seconds}s ago`;
  const m=Math.floor(seconds/60);return `${m}m ${seconds%60}s ago`;
}

export default function BridgeHealth(){
  const [data,setData]=useState(null);const [error,setError]=useState('');
  async function load(){
    const r=await fetch('/api/manager/dashboard',{cache:'no-store'});const j=await r.json();
    if(!r.ok){setError(j.error||'Unable to load bridge health.');return;}
    setData(j.settings||{});setError('');
  }
  useEffect(()=>{load();const i=setInterval(load,15000);return()=>clearInterval(i)},[]);
  const state=data?.bridge_state||'unknown';
  const online=state==='online';
  const info=data?.bridge_device_info||{};
  return <main className="page">
    <section className="hero"><h1>Print Bridge Health</h1><p>Live heartbeat monitor for the Meat House Android kitchen print bridge.</p></section>
    {error&&<div className="error" style={{marginTop:14}}>{error}</div>}
    <div className="grid grid-3" style={{marginTop:16}}>
      <div className="card"><div className="muted">Bridge status</div><div style={{fontSize:28,fontWeight:900,marginTop:6,color:online?'#2a7848':'#a12a2a'}}>{online?'● ONLINE':state==='offline'?'● OFFLINE':'● UNKNOWN'}</div></div>
      <div className="card"><div className="muted">Last heartbeat</div><b style={{fontSize:22,display:'block',marginTop:8}}>{ageText(data?.bridge_heartbeat_age_seconds)}</b><div className="muted" style={{fontSize:12,marginTop:5}}>{data?.bridge_last_heartbeat_at?new Date(data.bridge_last_heartbeat_at).toLocaleString():'—'}</div></div>
      <div className="card"><div className="muted">Health rule</div><b style={{fontSize:22,display:'block',marginTop:8}}>30s heartbeat</b><div className="muted" style={{fontSize:12,marginTop:5}}>Offline if no heartbeat for 90 seconds.</div></div>
    </div>
    <div className="card" style={{marginTop:16}}>
      <h3 style={{marginTop:0}}>Device</h3>
      <div className="grid grid-3">
        <div><div className="muted">Manufacturer</div><b>{info.manufacturer||'—'}</b></div>
        <div><div className="muted">Model</div><b>{info.model||'—'}</b></div>
        <div><div className="muted">Android</div><b>{info.android?`${info.android} (SDK ${info.sdk||'?'})`:'—'}</b></div>
      </div>
    </div>
    <div className="notice" style={{marginTop:16}}>This page refreshes automatically every 15 seconds. If the bridge service, phone internet, or Supabase connection stops, the status will turn OFFLINE after 90 seconds.</div>
  </main>;
}
