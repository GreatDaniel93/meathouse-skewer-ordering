'use client';

import { useEffect, useState } from 'react';

function Login({ done }) {
  const [pin, setPin] = useState('');
  const [error, setError] = useState('');
  async function submit(e) {
    e.preventDefault();
    const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ pin }) });
    const j = await r.json();
    if (!r.ok || j.role !== 'manager') return setError('Manager PIN required.');
    done();
  }
  return <main className="page"><div className="card" style={{ maxWidth: 380, margin: '80px auto' }}><h1>Manager Login</h1>{error && <div className="error">{error}</div>}<form onSubmit={submit}><div className="field"><label>PIN</label><input type="password" inputMode="numeric" value={pin} onChange={e => setPin(e.target.value)} /></div><button className="btn brand" style={{ width: '100%', marginTop: 12 }}>SIGN IN</button></form></div></main>;
}

function PinCard({ role, act }) {
  const [pin, setPin] = useState('');
  return <div className="card"><h3>{role.toUpperCase()} PIN</h3><div className="field"><label>New PIN</label><input type="password" inputMode="numeric" value={pin} onChange={e => setPin(e.target.value)} /></div><button className="btn brand" style={{ marginTop: 12 }} onClick={async () => { if (await act({ type: 'pin', role, pin })) setPin(''); }}>CHANGE PIN</button></div>;
}

function EditProductForm({ item, onCancel, onSave }) {
  const [form, setForm] = useState({
    name: item.name || '',
    display_name: item.display_name || '',
    description: item.description || '',
    portion_label: item.portion_label || '1 skewer',
    max_per_round: item.max_per_round,
    sort_order: item.sort_order ?? 100,
  });
  const [unlimited, setUnlimited] = useState(item.max_per_round == null);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!form.name.trim()) return alert('Internal name is required.');
    if (!unlimited && (!Number.isFinite(Number(form.max_per_round)) || Number(form.max_per_round) < 1)) return alert('Max / Round must be at least 1.');
    setSaving(true);
    const ok = await onSave({
      ...form,
      name: form.name.trim(),
      display_name: form.display_name.trim(),
      description: form.description.trim(),
      portion_label: form.portion_label.trim() || '1 skewer',
      max_per_round: unlimited ? null : Number(form.max_per_round),
      sort_order: Number(form.sort_order) || 100,
    });
    setSaving(false);
    if (ok) onCancel();
  }

  return <div className="card" style={{ marginTop: 14, border: '2px solid var(--brand)', boxShadow: '0 14px 35px rgba(0,0,0,.12)' }}>
    <div className="actions"><div><h2 style={{ margin: 0 }}>Edit Product</h2><div className="muted" style={{ marginTop: 4 }}>Update all product settings in one place.</div></div><span className="spacer"/><button className="btn secondary small" onClick={onCancel}>CANCEL</button></div>
    <div className="grid grid-2" style={{ marginTop: 16 }}>
      <div className="field"><label>Kitchen / Internal Name</label><input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></div>
      <div className="field"><label>Customer Display Name</label><input value={form.display_name} onChange={e => setForm({ ...form, display_name: e.target.value })} /></div>
      <div className="field"><label>Portion Label</label><input value={form.portion_label} onChange={e => setForm({ ...form, portion_label: e.target.value })} /></div>
      <div className="field"><label>Sort Order</label><input type="number" value={form.sort_order} onChange={e => setForm({ ...form, sort_order: Number(e.target.value) })} /></div>
    </div>
    <div className="field" style={{ marginTop: 12 }}><label>Description</label><textarea rows="3" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></div>
    <div className="card" style={{ marginTop: 12, background: 'var(--soft)' }}>
      <div className="actions">
        <div style={{ flex: 1 }}><b>Max / Round</b><div className="muted" style={{ fontSize: 12, marginTop: 3 }}>Limit this dish per round, or make it unlimited. The table-wide diners × rate limit still applies.</div></div>
        <label style={{ display: 'flex', gap: 8, alignItems: 'center' }}><input type="checkbox" checked={unlimited} onChange={e => { setUnlimited(e.target.checked); if (!e.target.checked && form.max_per_round == null) setForm({ ...form, max_per_round: 2 }); }} /> Unlimited</label>
      </div>
      {!unlimited && <div className="field" style={{ marginTop: 10, maxWidth: 240 }}><label>Maximum quantity</label><input type="number" min="1" value={form.max_per_round ?? 2} onChange={e => setForm({ ...form, max_per_round: Number(e.target.value) })} /></div>}
    </div>
    <div className="actions" style={{ marginTop: 16 }}><span className="spacer"/><button className="btn secondary" onClick={onCancel}>CANCEL</button><button className="btn brand" disabled={saving} onClick={save}>{saving ? 'SAVING…' : 'SAVE CHANGES'}</button></div>
  </div>;
}

export default function Manager() {
  const [auth, setAuth] = useState(true);
  const [data, setData] = useState(null);
  const [tab, setTab] = useState('overview');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [cooldown, setCooldown] = useState(5);
  const [rate, setRate] = useState(10);
  const [newItem, setNewItem] = useState({ name: '', display_name: '', description: '', portion_label: '1 skewer', max_per_round: null, sort_order: 100 });
  const [newItemUnlimited, setNewItemUnlimited] = useState(true);
  const [editingProduct, setEditingProduct] = useState(null);
  const [newTable, setNewTable] = useState('');
  const today = new Date().toISOString().slice(0, 10);
  const weekAgo = new Date(Date.now() - 6 * 86400000).toISOString().slice(0, 10);
  const [from, setFrom] = useState(weekAgo);
  const [to, setTo] = useState(today);

  async function load(rangeFrom = from, rangeTo = to) {
    const fromIso = new Date(`${rangeFrom}T00:00:00+10:00`).toISOString();
    const toIso = new Date(new Date(`${rangeTo}T00:00:00+10:00`).getTime() + 86400000).toISOString();
    const r = await fetch(`/api/manager/dashboard?from=${encodeURIComponent(fromIso)}&to=${encodeURIComponent(toIso)}`, { cache: 'no-store' });
    const j = await r.json();
    if (r.status === 401) { setAuth(false); return; }
    if (!r.ok) return setError(j.error || 'Unable to load manager data.');
    setAuth(true); setData(j); setCooldown(j.settings?.skewer_cooldown_minutes ?? 5); setRate(j.settings?.skewer_rate_per_guest ?? 10);
  }

  useEffect(() => { load(); }, []);

  async function act(body) {
    setError(''); setMessage('');
    const r = await fetch('/api/manager/dashboard', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
    const j = await r.json();
    if (!r.ok) { setError(j.error || 'Update failed.'); return false; }
    setMessage('Saved.'); await load(); return true;
  }

  function editTable(table) {
    const name = prompt('Table name', table.name); if (name === null) return;
    const capacity = Number(prompt('Capacity', table.capacity)); if (!Number.isFinite(capacity)) return;
    act({ type: 'table', action: 'update', table_id: table.id, name, capacity });
  }

  if (!auth) return <Login done={() => load()} />;
  if (!data) return <main className="page">Loading…</main>;

  return <>
    <div className="topbar"><div className="logo">MEAT HOUSE<small>MANAGER CONTROL</small></div><span className="spacer"/><a className="btn secondary small" href="/staff">Staff</a><a className="btn secondary small" href="/kitchen">Kitchen</a><a className="btn secondary small" href="/manager/qr">QR</a><button className="btn secondary small" onClick={async () => { await fetch('/api/auth/logout', { method: 'POST' }); setAuth(false); }}>Logout</button></div>
    <main className="page">
      <section className="hero"><h1>Manager Control</h1><p>Skewer ordering, tables, security and performance.</p></section>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
      {message && <div className="notice" style={{ marginTop: 12 }}>{message}</div>}
      <div className="actions" style={{ marginTop: 16 }}>{['overview', 'products', 'tables', 'settings', 'security'].map(x => <button key={x} className={`btn ${tab === x ? 'brand' : 'secondary'}`} onClick={() => { setTab(x); setEditingProduct(null); }}>{x.toUpperCase()}</button>)}</div>

      {tab === 'overview' && <>
        <div className="card" style={{ marginTop: 16 }}><div className="actions"><div className="field"><label>From</label><input type="date" value={from} onChange={e => setFrom(e.target.value)} /></div><div className="field"><label>To</label><input type="date" value={to} onChange={e => setTo(e.target.value)} /></div><button className="btn brand" onClick={() => load(from, to)}>RUN REPORT</button></div></div>
        <div className="grid grid-3" style={{ marginTop: 16 }}><div className="card"><div className="muted">Sessions</div><b style={{ fontSize: 30 }}>{data.analytics.sessions}</b></div><div className="card"><div className="muted">Guests</div><b style={{ fontSize: 30 }}>{data.analytics.guests}</b></div><div className="card"><div className="muted">Skewers ordered</div><b style={{ fontSize: 30 }}>{data.analytics.skewers}</b></div><div className="card"><div className="muted">Orders</div><b style={{ fontSize: 30 }}>{data.analytics.orders}</b></div><div className="card" style={{ gridColumn: 'span 2' }}><h3 style={{ marginTop: 0 }}>Top Skewers</h3>{data.analytics.top_items?.length ? data.analytics.top_items.map((x, i) => <div key={i} className="actions" style={{ padding: '6px 0', borderBottom: '1px solid var(--line)' }}><span>{x.name}</span><span className="spacer"/><b>{x.qty}</b></div>) : <span className="muted">No orders in this range.</span>}</div></div>
      </>}

      {tab === 'products' && <>
        <div className="card" style={{ marginTop: 16 }}><h3>Add Product</h3><div className="grid grid-3"><div className="field"><label>Internal Name</label><input value={newItem.name} onChange={e => setNewItem({ ...newItem, name: e.target.value })} /></div><div className="field"><label>Customer Name</label><input value={newItem.display_name} onChange={e => setNewItem({ ...newItem, display_name: e.target.value })} /></div><div className="field"><label>Max / Round</label><input type="number" min="1" disabled={newItemUnlimited} value={newItem.max_per_round ?? ''} placeholder="Unlimited" onChange={e => setNewItem({ ...newItem, max_per_round: e.target.value === '' ? null : Number(e.target.value) })} /></div></div><label style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 10 }}><input type="checkbox" checked={newItemUnlimited} onChange={e => { setNewItemUnlimited(e.target.checked); setNewItem({ ...newItem, max_per_round: e.target.checked ? null : 2 }); }} /> Unlimited per item</label><button className="btn brand" style={{ marginTop: 12 }} onClick={async () => { const payload = { ...newItem, max_per_round: newItemUnlimited ? null : newItem.max_per_round }; if (await act({ type: 'menu', action: 'add', payload })) { setNewItem({ name: '', display_name: '', description: '', portion_label: '1 skewer', max_per_round: null, sort_order: 100 }); setNewItemUnlimited(true); } }}>ADD PRODUCT</button></div>
        {editingProduct && <EditProductForm item={editingProduct} onCancel={() => setEditingProduct(null)} onSave={payload => act({ type: 'menu', action: 'update', item_id: editingProduct.id, payload })} />}
        <div style={{ display: 'grid', gap: 8, marginTop: 14 }}>{data.menu.map(item => <div className="card" key={item.id} style={editingProduct?.id === item.id ? { outline: '2px solid var(--brand)' } : undefined}><div className="actions"><div><b>{item.display_name || item.name}</b><div className="muted" style={{ fontSize: 12 }}>{item.portion_label} · max {item.max_per_round == null ? 'Unlimited' : item.max_per_round}</div></div><span className={`badge ${item.active ? 'available' : ''}`}>{item.active ? 'ACTIVE' : 'HIDDEN'}</span><span className="spacer"/><button className="btn secondary small" onClick={() => setEditingProduct(item)}>Edit</button><button className="btn secondary small" onClick={() => act({ type: 'menu', action: item.active ? 'disable' : 'restore', item_id: item.id, payload: {} })}>{item.active ? 'Hide' : 'Restore'}</button></div></div>)}</div>
      </>}

      {tab === 'tables' && <>
        <div className="card" style={{ marginTop: 16 }}><div className="actions"><div className="field" style={{ flex: 1 }}><label>New Table Name</label><input value={newTable} onChange={e => setNewTable(e.target.value)} /></div><button className="btn brand" onClick={async () => { if (await act({ type: 'table', action: 'add', name: newTable, capacity: 6 })) setNewTable(''); }}>ADD TABLE</button></div></div>
        <div className="grid grid-3" style={{ marginTop: 14 }}>{data.tables.map(table => <div className="card" key={table.id}><div className="actions"><div><b>{table.name}</b><div className="muted" style={{ fontSize: 12 }}>Capacity {table.capacity}</div></div><span className={`badge ${table.active ? 'available' : ''}`}>{table.active ? 'ACTIVE' : 'HIDDEN'}</span><span className="spacer"/><button className="btn secondary small" onClick={() => editTable(table)}>Edit</button><button className="btn secondary small" onClick={() => act({ type: 'table', action: table.active ? 'disable' : 'restore', table_id: table.id })}>{table.active ? 'Disable' : 'Restore'}</button></div></div>)}</div>
      </>}

      {tab === 'settings' && <div className="card" style={{ marginTop: 16, maxWidth: 620 }}><h2>Ordering Rules</h2><p className="muted">Round limit = diners × skewer rate. Children under 4 are not counted in the diner total.</p><div className="grid grid-2"><div className="field"><label>Skewers per diner / round</label><input type="number" min="1" max="100" value={rate} onChange={e => setRate(Number(e.target.value))} /></div><div className="field"><label>Reorder cooldown (minutes)</label><input type="number" min="0" max="15" value={cooldown} onChange={e => setCooldown(Number(e.target.value))} /></div></div><div className="notice" style={{ marginTop: 12 }}>Example: rate {rate} × 4 diners = {rate * 4} skewers per round.</div><button className="btn brand" style={{ marginTop: 12 }} onClick={() => act({ type: 'settings', skewer_cooldown_minutes: cooldown, skewer_rate_per_guest: rate })}>SAVE SETTINGS</button></div>}
      {tab === 'security' && <div className="grid grid-3" style={{ marginTop: 16 }}>{['staff', 'kitchen', 'manager'].map(role => <PinCard key={role} role={role} act={act} />)}</div>}
    </main>
  </>;
}
