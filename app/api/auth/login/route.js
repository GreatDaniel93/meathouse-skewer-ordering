import { db } from '@/lib/db';
import { setAccessCookie } from '@/lib/auth';
export async function POST(request){const body=await request.json().catch(()=>({}));const pin=String(body.pin||'');const {data,error}=await db().rpc('access_login',{p_pin:pin});if(error)return Response.json({error:error.message},{status:401});await setAccessCookie(data.token);return Response.json({role:data.role,expires_at:data.expires_at});}
