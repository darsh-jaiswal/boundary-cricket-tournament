# Owner-confirmed live-store launch

The owner confirmed live Razorpay activation, supplied the support email and confirmed that
they will ship the products represented by the launch inventory.

Launch configuration:

- 32 variants: 2 cap material variants and 15 variants each for T-shirts and jerseys.
- 20 units per variant, totalling 640 units.
- Caps ₹399; T-shirts ₹899; full-sleeve jerseys ₹1,200.
- Shipping ₹50 per order; dispatch estimate 3–5 days.
- Support: darshjaiswal009.xi@gmail.com.
- Production uses live payments. Preview checkout remains disabled.

`launch-2026-10-19.json` is the initial stock file, not an automatically reapplied seed.
`store-2026-10-19.json` records the launch environment configuration without credentials.
Neither file runs during deployment or migrations. Do not reapply launch quantities after orders:
stock is reserved/decremented by checkout and replenishment must use actual counted inventory.

Initial activation must reject nonzero/enabled launch stock or pending checkout/review/reserving
orders, validate all 32 IDs and use one transaction so partial activation cannot occur.
The disabled examples under `database/seeds` remain development examples.
