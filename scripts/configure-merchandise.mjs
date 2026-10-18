import { readFile } from 'node:fs/promises';
import { neon } from '@neondatabase/serverless';

// Explicit owner-supplied inventory file. No payment credentials or customer data are printed.
const filename=process.argv[2];
if(!filename||!process.env.DATABASE_URL)throw new Error('Usage: node --env-file=.env.local scripts/configure-merchandise.mjs inventory.json');
const variants=JSON.parse(await readFile(filename,'utf8'));
if(!Array.isArray(variants)||!variants.length)throw new Error('Expected an array of variant inventory records');
for(const v of variants)if(typeof v.id!=='string'||!Number.isInteger(v.pricePaise)||v.pricePaise<100||v.pricePaise>10000000
  ||!Number.isInteger(v.stock)||v.stock<0||typeof v.enabled!=='boolean')throw new Error('Each variant requires id, pricePaise (at least 100), stock and enabled');
const sql=neon(process.env.DATABASE_URL);
for(const v of variants){const rows=await sql`UPDATE boundary_product_variants SET price_paise=${v.pricePaise},stock=${v.stock},enabled=${v.enabled}
  WHERE id=${v.id} RETURNING id`;if(!rows.length)throw new Error('Unknown variant '+v.id);console.log('Configured '+v.id);}
