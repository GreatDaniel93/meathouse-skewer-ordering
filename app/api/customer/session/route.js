import { db } from '@/lib/db';
export async function GET(request){const token=new URL(request.url).searchParams.get('token')||'';const {data,error}=await db().rpc('customer_session',{p_table_token:token});if(error)return Response.json({error:error.message},{status:404});return Response.json(data);}
