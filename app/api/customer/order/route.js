import { db } from '@/lib/db';

export async function POST(request) {
  const body = await request.json().catch(() => ({}));
  const requestId = String(body.request_id || '').trim();
  if (!requestId || requestId.length > 120) {
    return Response.json({ error: 'Invalid order request.' }, { status: 400 });
  }
  const { data, error } = await db().rpc('submit_customer_order', {
    p_table_token: String(body.token || ''),
    p_items: Array.isArray(body.items) ? body.items : [],
    p_request_id: requestId,
  });
  if (error) return Response.json({ error: error.message }, { status: 409 });
  return Response.json(data);
}
