'use client';

import { useEffect, useMemo, useState } from 'react';

function fmtMoney(v) { return `$${Number(v || 0).toFixed(0)}`; }
function fmtTime(v) { try { return new Intl.DateTimeFormat('en-AU', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Australia/Sydney' }).format(new Date(v)); } catch { return v || '-'; } }
function fmtDate(v) { try { return new Intl.DateTimeFormat('en-AU', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Australia/Sydney' }).format(new Date(v)); } catch { return v || '-'; } }

const LABELS = {
  ready: 'READY TO REDEEM',
  not_yet_valid: 'NOT YET VALID',
  expired: 'EXPIRED',
  redeemed: 'REDEEMED',
  void: 'VOID',
};

export default function VoucherRedemption() {
  const [data, setData] = useState({ today_skewers: 0, vouchers: [] });
  const [code, setCode] = useState('');
  const [filter, setFilter] = useState('all');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true); setError('');
    const from = new Date(Date.now() - 45 * 86400000).toISOString();
    const to = new Date(Date.now() + 86400000).toISOString();
    const r = await fetch(`/api/manager/vouchers?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { cache: 'no-store' });
    const j = await r.json();
    setLoading(false);
    if (r.status === 401) { location.href = '/manager'; return; }
    if (!r.ok) return setError(j.error || 'Unable to load vouchers.');
    setData(j);
  }

  useEffect(() => { load(); }, []);

  async function redeem(voucherCode = code) {
    const c = String(voucherCode || '').trim();
    if (!c) return;
    setError(''); setMessage('');
    const r = await fetch('/api/manager/vouchers', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ code: c }) });
    const j = await r.json();
    if (!r.ok) return setError(j.error || 'Unable to redeem voucher.');
    setMessage(`${j.voucher_code} redeemed — ${fmtMoney(j.reward_amount)}.`);
    setCode(''); await load();
  }

  const vouchers = data.vouchers || [];
  const rows = useMemo(() => vouchers.filter(v => filter === 'all' || v.status === filter), [vouchers, filter]);
  const counts = useMemo(() => ({
    all: vouchers.length,
    ready: vouchers.filter(v => v.status === 'ready').length,
    not_yet_valid: vouchers.filter(v => v.status === 'not_yet_valid').length,
    expired: vouchers.filter(v => v.status === 'expired').length,
    redeemed: vouchers.filter(v => v.status === 'redeemed').length,
  }), [vouchers]);

  return <>
    <div className="topbar"><div className="logo">MEAT HOUSE<small>VOUCHER REDEMPTION</small></div><span className="spacer"/><a className="btn secondary small" href="/manager">Manager</a><a className="btn secondary small" href="/staff">Staff</a><a className="btn secondary small" href="/kitchen">Kitchen</a></div>
    <main className="page">
      <section className="hero"><h1>Voucher Redemption</h1><p>Lucky Skewer rewards · 优惠券核销</p></section>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
      {message && <div className="notice" style={{ marginTop: 12 }}>{message}</div>}

      <div className="grid grid-3" style={{ marginTop: 16 }}>
        <div className="card"><div className="muted">Today's skewers</div><b style={{ fontSize: 34 }}>{data.today_skewers || 0}</b><div className="muted" style={{ fontSize: 12 }}>今日累计串数</div></div>
        <div className="card"><div className="muted">Ready to redeem</div><b style={{ fontSize: 34 }}>{counts.ready}</b><div className="muted" style={{ fontSize: 12 }}>当前可核销</div></div>
        <div className="card"><div className="muted">Redeemed</div><b style={{ fontSize: 34 }}>{counts.redeemed}</b><div className="muted" style={{ fontSize: 12 }}>已核销优惠券</div></div>
      </div>

      <div className="card" style={{ marginTop: 16 }}>
        <h2 style={{ marginTop: 0 }}>Redeem a voucher</h2>
        <p className="muted">Voucher is valid from the next day at 00:00, for 30 days, and can be redeemed once only.</p>
        <div className="notice" style={{ marginBottom: 12 }}><b>规则：</b>中奖次日 00:00 起生效 · 30 天内有效 · 每张仅限使用一次</div>
        <div className="actions"><div className="field" style={{ flex: 1 }}><label>Voucher Code</label><input value={code} onChange={e => setCode(e.target.value.toUpperCase())} placeholder="MH-200-A7K9" onKeyDown={e => { if (e.key === 'Enter') redeem(); }} /></div><button className="btn brand" onClick={() => redeem()}>REDEEM</button></div>
      </div>

      <div className="actions" style={{ marginTop: 16 }}>
        {['all','ready','not_yet_valid','expired','redeemed'].map(x => <button key={x} className={`btn ${filter === x ? 'brand' : 'secondary'}`} onClick={() => setFilter(x)}>{(LABELS[x] || x.toUpperCase())} ({counts[x] ?? 0})</button>)}
        <span className="spacer"/><button className="btn secondary" onClick={load}>REFRESH</button>
      </div>

      <div style={{ display: 'grid', gap: 10, marginTop: 14 }}>
        {loading ? <div className="card">Loading…</div> : !rows.length ? <div className="card"><span className="muted">No vouchers in this view.</span></div> : rows.map(v => <div className="card" key={v.id}>
          <div className="actions" style={{ alignItems: 'flex-start' }}>
            <div><div className="muted" style={{ fontSize: 11 }}>Voucher Code</div><b style={{ fontSize: 20 }}>{v.voucher_code}</b><div className="muted" style={{ marginTop: 4 }}>{v.table_name} · issued {fmtTime(v.issued_at)}</div></div>
            <div><div className="muted" style={{ fontSize: 11 }}>Milestone</div><b>{v.milestone}th skewer</b></div>
            <div><div className="muted" style={{ fontSize: 11 }}>Reward</div><b style={{ fontSize: 22 }}>{fmtMoney(v.reward_amount)}</b></div>
            <div><div className="muted" style={{ fontSize: 11 }}>Valid</div><b style={{ fontSize: 13 }}>{fmtDate(v.redeemable_after)}</b><div className="muted" style={{ fontSize: 11 }}>to {fmtDate(v.expires_at)}</div></div>
            <span className={`badge ${v.status === 'ready' ? 'available' : v.status === 'not_yet_valid' ? 'new' : ''}`}>{LABELS[v.status] || v.status.toUpperCase()}</span>
            <span className="spacer"/>
            {v.status === 'ready' ? <button className="btn brand small" onClick={() => redeem(v.voucher_code)}>REDEEM</button> : v.status === 'redeemed' ? <div style={{ textAlign: 'right' }}><div className="muted" style={{ fontSize: 11 }}>Redeemed</div><b>{fmtTime(v.redeemed_at)}</b></div> : null}
          </div>
        </div>)}
      </div>
    </main>
  </>;
}
