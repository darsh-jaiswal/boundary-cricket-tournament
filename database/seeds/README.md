# Merchandise seed data

These are sample settings for the existing Boundary catalog, not confirmed physical stock.

| Product | Variants | Sample price | Sample quantity |
| --- | --- | --- | --- |
| Boundary Club Cap | Cotton twill / performance polyester, adjustable | ₹399 | 20 per variant |
| Boundary Club T-shirt | Cotton / performance / premium blend, S–XXL | ₹899 | 20 per variant |
| Boundary Full-sleeve Jersey | Cotton / performance / premium blend, S–XXL | ₹1,200 | 20 per variant |

`merchandise-inventory.seed.json` contains 32 existing variant IDs with sample quantities.
All variants have `enabled: false`. Reapplying this file overwrites stock quantities;
do not run it over an operating store or after orders have reserved stock.

`merchandise-store.seed.json` proposes ₹50 shipping and a 3–5 day dispatch estimate.
The support email is intentionally empty, and sales stay disabled. This file is a reference
for configuration, not a script that updates Vercel environment variables.

Before opening real-money checkout, the owner must provide a working support email and confirm
that these quantities and delivery settings represent orders they can fulfil. Demo items should
use Razorpay test-mode keys instead. The user's Razorpay live activation is confirmed;
these files do not change keys, enable checkout or create a payment.

To apply the disabled inventory sample to a development database:

```powershell
node --env-file=.env.local scripts/configure-merchandise.mjs database/seeds/merchandise-inventory.seed.json
```

Check that `.env.local` points to the intended development database first. For real stock,
create a separate owner-confirmed inventory file for the existing configuration script.
