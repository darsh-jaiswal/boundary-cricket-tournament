import { db } from '../server/auth.js';
import { reconcile } from '../server/shop-orders.js';
const sql=db();
let after='00000000-0000-0000-0000-000000000000';
while(true){
const rows=await sql`SELECT * FROM boundary_shop_orders WHERE status IN ('checkout','review','cancelled') AND id>${after}::uuid ORDER BY id LIMIT 100`;
if(!rows.length)break;
after=rows[rows.length-1].id;
for(const order of rows){try{const result=await reconcile(sql,order);console.log(order.id+' '+result.status+(result.refund_status?' '+result.refund_status:''));}catch(error){console.error(order.id+' reconciliation failed: '+(error.status||error.code||error.name));process.exitCode=1;}}

}
