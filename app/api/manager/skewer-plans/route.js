import {db} from '@/lib/db';
import {getAccessToken,requireRole} from '@/lib/auth';

function clamp(v,min,max){return Math.max(min,Math.min(max,Number(v)||0));}

export async function GET(){
  const role=await requireRole(['manager']);
  if(!role)return Response.json({error:'Manager login required.'},{status:401});
  const token=await getAccessToken();
  const {data,error}=await db().rpc('manager_get_skewer_plans',{p_secret:token});
  if(error)return Response.json({error:error.message},{status:409});
  return Response.json(data);
}

export async function POST(request){
  const role=await requireRole(['manager']);
  if(!role)return Response.json({error:'Manager login required.'},{status:401});
  const token=await getAccessToken();
  const b=await request.json().catch(()=>({}));
  const {data,error}=await db().rpc('manager_set_skewer_plans',{
    p_secret:token,
    p_included_cooldown:clamp(b.included_cooldown_minutes,0,90),
    p_included_rate:clamp(b.included_rate_per_guest,1,100),
    p_paid_cooldown:clamp(b.paid_cooldown_minutes,0,90),
    p_paid_rate:clamp(b.paid_rate_per_guest,1,100),
  });
  if(error)return Response.json({error:error.message},{status:409});
  return Response.json(data);
}
