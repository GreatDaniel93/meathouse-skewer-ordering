import { db } from '@/lib/db';
import { getAccessToken, requireRole } from '@/lib/auth';

export async function GET(request) {
  const role = await requireRole(['manager']);
  if (!role) return Response.json({ error: 'Manager login required.' }, { status: 401 });
  const token = await getAccessToken();
  const u = new URL(request.url);
  const from = u.searchParams.get('from') || new Date(Date.now() - 7 * 86400000).toISOString();
  const to = u.searchParams.get('to') || new Date(Date.now() + 86400000).toISOString();
  const { data, error } = await db().rpc('manager_get_vouchers', { p_secret: token, p_from: from, p_to: to });
  if (error) return Response.json({ error: error.message }, { status: 409 });
  return Response.json(data);
}

export async function POST(request) {
  const role = await requireRole(['manager']);
  if (!role) return Response.json({ error: 'Manager login required.' }, { status: 401 });
  const token = await getAccessToken();
  const body = await request.json().catch(() => ({}));
  const code = String(body.code || '').trim();
  if (!code) return Response.json({ error: 'Voucher code is required.' }, { status: 400 });
  const { data, error } = await db().rpc('manager_redeem_voucher', { p_secret: token, p_code: code });
  if (error) return Response.json({ error: error.message }, { status: 409 });
  return Response.json(data);
}
