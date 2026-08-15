'use client';

import { useEffect } from 'react';

export default function ManagerLayout({ children }) {
  useEffect(() => {
    function addVoucherButton() {
      const rows = Array.from(document.querySelectorAll('.actions'));
      const nav = rows.find((row) => {
        const text = row.textContent || '';
        return text.includes('OVERVIEW') && text.includes('PRODUCTS') && text.includes('TABLES') && text.includes('SETTINGS') && text.includes('SECURITY');
      });
      if (!nav || nav.querySelector('[data-vouchers-nav]')) return;

      const link = document.createElement('a');
      link.href = '/manager/vouchers';
      link.textContent = 'VOUCHERS';
      link.className = 'btn secondary';
      link.setAttribute('data-vouchers-nav', 'true');
      link.style.textDecoration = 'none';
      link.style.display = 'inline-flex';
      link.style.alignItems = 'center';
      link.style.justifyContent = 'center';
      nav.appendChild(link);
    }

    addVoucherButton();
    const observer = new MutationObserver(addVoucherButton);
    observer.observe(document.body, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, []);

  return children;
}
