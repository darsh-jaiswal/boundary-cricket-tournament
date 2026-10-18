# Boundary 1.0.5

18 October 2026.

## Android

- Removed the device PIN implementation, unlock prompts and lock action. Workspaces still determine editable versus read-only controls.
- New teams add players by a registered phone lookup. Browser registration uses a verified Clerk phone; approving a short-lived request links the Android app for thirty days. The private app token is excluded from the declared backup set.
- New match squads require distinct registered accounts across both sides and an online check before opening-batter setup. Existing historical matches are preserved. Scoring after setup still works offline.
- Store navigation opens the merchandise shop, order history and policy pages. Tools remain accessible from Home and Store.
- Insights now compare both main innings on shared Manhattan, worm and run-rate axes, with wicket markers. Includes opening/middle/death phases, run distributions, batter/bowler face-offs and wagon-wheel sector totals.
- Player profiles include form bars, metric tiles, bowling average/strike rate, dot percentages and boundary-run percentage. No-balls count as a batter ball faced but do not advance a standard over. Wides do not count as faced. Corrections rebuild all figures.

## Shop and payments

Real catalog and order tables were added to the existing Neon database. T-shirts, full-sleeve jerseys, caps and bottles have material/size variants. Razorpay uses the existing Aspire Quest merchant credentials through server-only environment variables. No credentials are committed. There were no live charges or refunds during this release.

Prices come from the server catalog, never the browser. Order snapshots and stock reservations are durable. A checkout request ID prevents duplicate local orders, receipt lookup recovers gateway responses, and an unpaid cancellation releases stock once. Captured payments must match order, currency and amount. A captured payment detected after a released cancellation uses Razorpay's idempotent refund API; pending refunds are not labelled completed.

Run `node --env-file=.env.local scripts/reconcile-merchandise.mjs` to reconcile saved orders, including abandoned browser sessions. This is an operator command, not an active scheduled task. It may submit an idempotent refund for a captured payment on an already cancelled order.

Terms and Privacy are available at `/terms.html` and `/privacy.html`. Registration and checkout require the current terms version; server records retain that version.

## Configuration still required

- Live Clerk settings currently report `phone_number.enabled=false`. Enable phone sign-up/sign-in, SMS verification and the intended country availability in Clerk. The instance is currently development mode; real SMS/production account acceptance has not been proved.
- Sales intentionally remain disabled. Confirm supplier, actual material specification, prices, inventory, delivery, refund/exchange policy and merchant support before selling. Product artwork is a design illustration, not a supplier photograph or quality certification.
- Set `MERCH_SALES_ENABLED=true`, `MERCH_SHIPPING_PAISE` (integer, including `0` for free delivery), `MERCH_DISPATCH_DAYS`, `MERCH_SUPPORT_EMAIL` and the two Razorpay keys in the deployment environment.
- Use `scripts/configure-merchandise.mjs inventory.json` with records containing `id`, `pricePaise`, `stock`, `enabled`. Price is in paise. Query real variant IDs from `boundary_product_variants`; there are no invented default prices or in-stock quantities.
- Fulfilment, tax setup and supplier fulfilment APIs have not been provisioned. Paid-order dispatch is an operator responsibility. Support and supplier-specific policy text must be finalised before sales open.

## Statistics methods and sources

Batting average = runs / dismissals. Batting strike rate = runs × 100 / balls faced. Bowling average = charged runs / credited wickets. Bowling strike rate = legal balls / credited wickets. Economy = charged runs / actual overs, using each match's balls-per-over. Zero denominators display an unavailable value. Super Overs are excluded from career totals. Live figures remain labelled as including ongoing matches. Identity for historical stats still uses team and player names; cross-team global identity aggregation is not implemented.

These are standard cricket measures, not a claim of BCCI certification, an official BCCI ranking formula or exact CricHeroes parity. Boundary MVP and win predictions remain app-specific estimates.

References: [BCCI statistical categories](https://documents.bcci.tv/documents/bcci/document/2020/03/02/6c3d1d01-0369-4d34-9e6e-edadf2ea6ec4/BCCI-Annual-Report.pdf), [NZC scorer guidance](https://www.nzc.nz/media/12873/scorer-companion-final-2016.pdf), [MCC scoring runs](https://www.lords.org/MCC/The-Laws-of-Cricket/Scoring-runs), [Razorpay Standard Checkout](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/), [Razorpay idempotent refunds](https://github.com/razorpay/markdown-docs/blob/master/api/refunds/normal-refunds-idempotent.md).

## Verification boundaries

JavaScript syntax checks, Android build and Java unit tests are checked during packaging. Targeted no-ball, extras and rate regressions were added. An independent code review covered account eligibility and payments. Full device UX matching, successful SMS registration, real checkout capture, refunds and physical fulfilment remain unverified.
