import { db } from '@/lib/db';
export async function POST(request){const body=await request.json().catch(()=>({}));const {data,error}=await db().rpc('submit_customer_order',{p_table_token:String(body.token||''),p_items:Array.isArray(body.items)?body.items:[]});if(error)return Response.json({error:error.message},{status:409});return Response.json(data);}
