import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {neon} from '@neondatabase/serverless';
import {PRODUCT_PHOTOS,photoFor} from '../web/store-catalog.js';

test('the collection maps only the four supplied original photos', async()=>{
  assert.deepEqual(Object.keys(PRODUCT_PHOTOS),['black-full-sleeve','blue-jersey','white-jersey','maroon-cap']);
  for(const id of Object.keys(PRODUCT_PHOTOS)) {
    const bytes=await readFile(new URL('../web'+photoFor(id),import.meta.url));
    assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
  }
  for(const id of ['club-tee','toString','__proto__','../secret',null]) assert.equal(photoFor(id),null);
});

const enabled=process.env.BOUNDARY_DATABASE_TESTS==='1'&&Boolean(process.env.DATABASE_URL);
test('photo catalog transfers stock and cancelled legacy orders return it exactly once', {skip:!enabled}, async()=>{
  const sql=neon(process.env.DATABASE_URL);
  const fixture=async name=>(await readFile(new URL('../database/migrations/'+name,import.meta.url),'utf8'))
    .replaceAll('boundary_','photo_test_')
    .replaceAll('CREATE TABLE ','CREATE TEMP TABLE ')
    .replace(/(CREATE TEMP TABLE [\s\S]*?\n\));/g,'$1 ON COMMIT DROP;')
    .replace(/(CREATE (?:OR REPLACE )?FUNCTION )photo_test_/g,'$1pg_temp.photo_test_')
    .replaceAll('EXECUTE FUNCTION photo_test_','EXECUTE FUNCTION pg_temp.photo_test_');
  const wrap=source=>sql.query('DO $test$ BEGIN\n'+source+'\nEND $test$');
  const migration=await fixture('014_four_photo_products.sql');
  const result=await sql.transaction([
    wrap(await fixture('003_merchandise.sql')),
    wrap(await fixture('005_shop_recovery.sql')),
    sql`UPDATE photo_test_product_variants SET stock=20,enabled=true,price_paise=89900`,
    sql`SELECT * FROM pg_temp.photo_test_reserve_shop_order('10000000-0000-4000-8000-000000000001','user_test',
      '10000000-0000-4000-8000-000000000002','[{"variantId":"club-tee-performance-m","quantity":2}]'::jsonb,'{}'::jsonb,5000)`,
    wrap(migration),
    sql`SELECT id,stock FROM photo_test_product_variants WHERE id='blue-jersey-m'`,
    sql`SELECT id FROM photo_test_products WHERE active ORDER BY id`,
    sql`SELECT count(*)::int AS n FROM photo_test_product_variants WHERE enabled`,
    sql`SELECT * FROM pg_temp.photo_test_release_shop_reservation('10000000-0000-4000-8000-000000000001','user_test')`,
    sql`SELECT * FROM pg_temp.photo_test_release_shop_reservation('10000000-0000-4000-8000-000000000001','user_test')`,
    sql`SELECT id,stock FROM photo_test_product_variants WHERE id IN ('blue-jersey-m','club-tee-performance-m') ORDER BY id`,
    sql`SELECT items,status FROM photo_test_shop_orders`,
    sql`SELECT variant_id,quantity FROM photo_test_stock_reservations`,
  ]);
  assert.equal(result[5][0].stock,18);
  assert.deepEqual(result[6].map(p=>p.id),Object.keys(PRODUCT_PHOTOS).sort());
  assert.equal(result[7][0].n,16);
  assert.deepEqual(result[10],[{id:'blue-jersey-m',stock:20},{id:'club-tee-performance-m',stock:0}]);
  assert.equal(result[11][0].status,'cancelled');
  assert.equal(result[11][0].items[0].variantId,'club-tee-performance-m');
  assert.deepEqual(result[12],[{variant_id:'club-tee-performance-m',quantity:2}]);
});
