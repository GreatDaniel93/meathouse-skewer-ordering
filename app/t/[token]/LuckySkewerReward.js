'use client';

import { useEffect, useMemo, useState } from 'react';

function makePieces() {
  return Array.from({ length: 72 }, (_, i) => ({
    id: i,
    left: 2 + Math.random() * 96,
    drift: -95 + Math.random() * 190,
    rot: 420 + Math.random() * 900,
    delay: Math.random() * .85,
    duration: 2.5 + Math.random() * 2,
    color: ['#ffd166', '#ff9f1c', '#ef4444', '#fff2a8', '#f59e0b'][Math.floor(Math.random() * 5)],
  }));
}

export default function LuckySkewerReward({ voucher, onClose }) {
  const [ready, setReady] = useState(false);
  const [pieces, setPieces] = useState([]);

  useEffect(() => {
    if (!voucher) return;
    setPieces(makePieces());
    const t = setTimeout(() => setReady(true), 80);
    try { navigator.vibrate?.([90, 45, 140]); } catch {}
    return () => { clearTimeout(t); setReady(false); };
  }, [voucher]);

  if (!voucher) return null;
  const amount = Number(voucher.reward_amount || 0);
  const milestone = Number(voucher.milestone || 0);

  return <div className={`lucky-overlay ${ready ? 'show' : ''}`} role="dialog" aria-modal="true" onClick={onClose}>
    <div className="lucky-dim" />
    <div className="lucky-flash" />
    <div className="lucky-fx">
      {pieces.map(p => <i key={p.id} className="lucky-confetti" style={{ left: `${p.left}%`, background: p.color, '--drift': `${p.drift}px`, '--rot': `${p.rot}deg`, animationDelay: `${p.delay}s`, animationDuration: `${p.duration}s` }} />)}
      {[['18%','18%'],['82%','20%'],['16%','62%'],['84%','64%']].map((pos, i) => <span key={i} className="lucky-burst" style={{ left: pos[0], top: pos[1] }} />)}
    </div>

    <div className="lucky-stage">
      <div className="lucky-prize" onClick={e => e.stopPropagation()}>
        <div className="lucky-card">
          <div className="lucky-logo">MEAT <span>HOUSE</span></div>
          <div className="lucky-title">LUCKY SKEWER!</div>
          <div className="lucky-sub">YOU ARE THE LUCKY ONE!</div>
          <div className="lucky-label">You just ordered</div>
          <div className="lucky-number">{milestone}<sup>TH</sup></div>
          <div className="lucky-today">SKEWER TODAY!</div>

          <div className="lucky-ticket">
            <div className="lucky-won">YOU'VE<br/>WON</div>
            <div><div className="lucky-amount">${amount}</div><div className="lucky-voucher-word">VOUCHER</div></div>
          </div>

          <div className="lucky-codebox">
            <div className="lucky-code-label">YOUR VOUCHER CODE</div>
            <div className="lucky-code">{voucher.voucher_code}</div>
          </div>

          <div className="lucky-notice">请截图保存此优惠券<span>Please screenshot and save this voucher.</span></div>
          <div className="lucky-next">Valid for your next visit only · 下次到店使用</div>
          <button className="lucky-save" onClick={onClose}>I'VE SAVED MY VOUCHER</button>
        </div>
      </div>
    </div>

    <style jsx global>{`
      .lucky-overlay{position:fixed;inset:0;z-index:9999;opacity:0;pointer-events:none}.lucky-overlay.show{opacity:1;pointer-events:auto}.lucky-dim{position:absolute;inset:0;background:rgba(0,0,0,.86);backdrop-filter:blur(4px);animation:luckyDim .32s ease both}.lucky-flash{position:absolute;inset:0;background:#000;animation:luckyFlash .55s ease both}.lucky-fx{position:absolute;inset:0;overflow:hidden;pointer-events:none}.lucky-confetti{position:absolute;top:-25px;width:8px;height:17px;border-radius:2px;opacity:0;animation:luckyFall linear forwards}.lucky-burst{position:absolute;width:12px;height:12px;border:3px solid #ffd166;border-radius:50%;opacity:0;box-shadow:0 0 18px #ff9f1c;animation:luckyRing .9s .32s ease-out forwards}.lucky-stage{position:absolute;inset:0;display:grid;place-items:center;padding:18px}.lucky-prize{width:min(76vw,380px);max-height:84vh;opacity:0;transform:scale(.18) rotate(-7deg);animation:luckyPrize .72s .34s cubic-bezier(.12,1.6,.3,1) forwards}.lucky-card{position:relative;border-radius:22px;padding:16px 15px 15px;color:#28140c;background:linear-gradient(#fffaf0,#ffe4a7) padding-box,linear-gradient(135deg,#fff0a6,#c57900,#fff1aa,#d97706) border-box;border:4px solid transparent;box-shadow:0 30px 70px rgba(0,0,0,.7),0 0 45px rgba(255,174,0,.18);overflow:hidden}.lucky-card:after{content:'';position:absolute;inset:-20%;background:linear-gradient(115deg,transparent 42%,rgba(255,255,255,.55) 50%,transparent 58%);transform:translateX(-120%);animation:luckySheen 2.4s 1.4s infinite;pointer-events:none}.lucky-logo{text-align:center;font-weight:1000;font-size:16px;letter-spacing:.05em;position:relative;z-index:1}.lucky-logo span{color:#c62828}.lucky-title{text-align:center;font-weight:1000;color:#a30f19;font-size:clamp(28px,8.8vw,42px);line-height:.96;margin-top:7px;text-shadow:0 2px #fff,0 4px #e9b441;opacity:0;animation:luckyHit .38s .7s cubic-bezier(.15,1.8,.4,1) forwards}.lucky-sub{text-align:center;font-size:12px;font-weight:900;color:#8a3c18;letter-spacing:.06em;margin-top:7px}.lucky-label{text-align:center;margin-top:10px;font-size:12px;font-weight:800}.lucky-number{text-align:center;font-weight:1000;color:#c5221f;font-size:clamp(56px,18vw,88px);line-height:.78;text-shadow:0 2px #fff,0 5px #f4b942;opacity:0;animation:luckyHit .38s .86s cubic-bezier(.15,1.8,.4,1) forwards}.lucky-number sup{font-size:.27em;vertical-align:top}.lucky-today{text-align:center;font-size:13px;font-weight:1000;margin-top:6px}.lucky-ticket{margin:14px auto 10px;background:linear-gradient(#cc2323,#901313);border:3px solid #f6c453;border-radius:16px;color:#fff;display:grid;grid-template-columns:auto 1fr;align-items:center;gap:8px;padding:13px 11px;box-shadow:inset 0 0 0 2px #6d0f0f;opacity:0;transform:translateY(12px) scale(.94);animation:luckyUp .38s 1.02s forwards}.lucky-won{font-size:11px;font-weight:900;line-height:1.05}.lucky-amount{text-align:center;font-size:clamp(44px,14vw,68px);font-weight:1000;color:#ffd568;line-height:.8;text-shadow:0 3px #7e4b00}.lucky-voucher-word{text-align:center;font-size:14px;font-weight:1000;color:#ffd568;margin-top:5px}.lucky-codebox{background:rgba(255,255,255,.62);border:2px dashed #d89c36;border-radius:13px;text-align:center;padding:10px;opacity:0;transform:translateY(8px);animation:luckyUp .38s 1.24s forwards}.lucky-code-label{font-size:10px;font-weight:900;color:#8b4f14;letter-spacing:.07em}.lucky-code{font-size:20px;font-weight:1000;color:#a31c15;margin-top:2px}.lucky-notice{font-size:12px;text-align:center;font-weight:900;margin-top:10px;opacity:0;animation:luckyUp .38s 1.38s forwards}.lucky-notice span{display:block;color:#8e3c13;font-weight:800;margin-top:3px}.lucky-next{font-size:10px;text-align:center;color:#705340;font-weight:700;margin-top:5px;opacity:0;animation:luckyUp .38s 1.48s forwards}.lucky-save{position:relative;z-index:2;width:100%;margin-top:12px;border:0;border-radius:12px;padding:11px 10px;background:#9f1717;color:#fff;font-size:12px;font-weight:1000;letter-spacing:.03em;opacity:0;animation:luckyUp .38s 1.62s forwards}.lucky-save:active{transform:scale(.98)}
      @keyframes luckyDim{from{opacity:0}to{opacity:1}}@keyframes luckyFlash{0%{opacity:0}22%{opacity:1}54%{opacity:.15}100%{opacity:0}}@keyframes luckyPrize{0%{opacity:0;transform:scale(.18) rotate(-7deg)}48%{opacity:1;transform:scale(1.16) rotate(2deg)}72%{transform:scale(.95) rotate(-.7deg)}100%{opacity:1;transform:scale(1) rotate(0)}}@keyframes luckyHit{0%{opacity:0;transform:scale(.45)}70%{opacity:1;transform:scale(1.14)}100%{opacity:1;transform:scale(1)}}@keyframes luckyUp{to{opacity:1;transform:none}}@keyframes luckySheen{0%,68%{transform:translateX(-120%)}92%,100%{transform:translateX(120%)}}@keyframes luckyFall{0%{opacity:0;transform:translateY(-5vh) rotate(0)}8%{opacity:1}100%{opacity:.95;transform:translate(var(--drift),112vh) rotate(var(--rot))}}@keyframes luckyRing{0%{opacity:.95;transform:scale(.2)}100%{opacity:0;transform:scale(10)}}
      @media(min-width:700px){.lucky-prize{width:330px}}
    `}</style>
  </div>;
}
