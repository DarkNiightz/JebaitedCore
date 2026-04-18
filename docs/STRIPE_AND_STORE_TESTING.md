# Stripe store (`/donate`) — configuration and testing

This document covers the **Paper plugin** store (Stripe Checkout), the **HTTP webhook** endpoint, and how to verify end-to-end before production.

## Deferred: Stripe account & business details (do when ready)

If you **have not** finished Stripe onboarding (business name, **VAT** if UK-registered, **public website or social URL** that matches your brand, etc.), you can still develop with **test mode** keys once the account allows API access. Add this checklist and return when the storefront goes live:

- Complete Stripe **business verification** (Dashboard prompts).
- **VAT:** real GB VAT if registered; otherwise leave optional / as Stripe allows.
- **Website / public URL:** live, not password-only; aligns with business name Stripe has.
- **Live mode:** switch to `sk_live_…` + live webhook signing secret when accepting real money.
- **Success/cancel URLs** in config: replace placeholders with your real site or policy pages.

Technical plugin steps (keys, webhooks, CLI) remain below.

## Prerequisites

1. **Database**: Migration `V010__store_stripe.sql` applied (included in `migrations.index`).
2. **Stripe account**: Test mode keys for development.
3. **Network**: Stripe must reach your webhook URL. For local dev, use **Stripe CLI** forwarding (below).

## Docker Compose

Production uses `**Vibe Code/MC Server/docker-compose.yml`**. Put `**STRIPE_SECRET_KEY**` and `**STRIPE_WEBHOOK_SECRET**` in `**MC Server/.env**` — they are passed into the `**minecraft**` (jebaited-mc) container. See **[docs/DOCKER.md](DOCKER.md)**.

## Plugin configuration (`config.yml`)

- `store.enabled`: master switch.
- `store.stripe.enabled`: enables Checkout + webhook handling paths in code.
- **Secrets** (recommended):
  - `STRIPE_SECRET_KEY` — API key for creating Checkout sessions (live or test).
  - `STRIPE_WEBHOOK_SECRET` — signing secret for the webhook endpoint (per Stripe CLI session or Dashboard endpoint).
- **Or** in config (less ideal for production):
  - `store.stripe.secret_key`
  - `store.stripe.webhook_secret`
- **Checkout redirects** (required):
  - `store.stripe.success_url` — e.g. your site or `https://example.com/thanks?session_id={CHECKOUT_SESSION_ID}` (Stripe replaces the placeholder).
  - `store.stripe.cancel_url` — shown if the user cancels.
- **HTTP bind** (`store.http.`*): used **only** when Discord inbound is **off** but the store webhook is on. If Discord inbound is enabled, the **same** server and port host both Discord routes and `/integrations/stripe/webhook`.
- **Packages**: `store.packages` — YAML list. Each entry needs `id`, `display_name`, `amount_cents`, `currency`, and a `grants` block (see comments in `config.yml`).

### Rank gates

- `min_rank_to_purchase` (optional): minimum **primary** rank on the ladder required to **see and buy** the package. Leave empty for no gate.

### Grants

- `grants.donor_rank`: one of `gold`, `diamond`, `legend`, `grandmaster` (donor ladder).
- `grants.primary_rank`: optional; must exist on `ranks.ladder` in config.
- `grants.cosmetic_coins`: integer.
- `grants.economy`: SMP balance amount (double).

## HTTP endpoints

- **Stripe webhook**: `POST /integrations/stripe/webhook`
  - Header: `Stripe-Signature` (set by Stripe or Stripe CLI).
  - Body: **raw** JSON (Paper handler reads bytes as-is; do not parse JSON before verification).

Production: put **nginx**, **Caddy**, or a cloud load balancer in front and `proxy_pass` to `127.0.0.1:<bind_port>`.

## In-game testing

1. Grant `jebaited.donate.use` (default **true** in `plugin.yml`).
2. Set `store.enabled`, `store.stripe.enabled`, keys, URLs, and at least one package.
3. Run `/donate` — pick a package; you should get a **clickable chat link** (Stripe Checkout).
4. Complete payment in Stripe **test mode** with card `4242 4242 4242 4242`.
5. Confirm: in-game message, profile grants (rank/coins/balance), and row in `store_orders` (status fulfilled).

## Webhook testing with Stripe CLI

1. Install [Stripe CLI](https://stripe.com/docs/stripe-cli).
2. Login: `stripe login`
3. Forward to your Paper bind (default `127.0.0.1:8789` if using Discord inbound, or `store.http.`*):
  ```bash
   stripe listen --forward-to http://127.0.0.1:8789/integrations/stripe/webhook
  ```
4. Copy the **webhook signing secret** printed by the CLI (starts with `whsec_`) into `STRIPE_WEBHOOK_SECRET` or `store.stripe.webhook_secret`.
5. Trigger a test event after a real Checkout session exists, or complete a test Checkout from `/donate`.

## Dashboard webhook (staging/production)

1. Stripe Dashboard → Developers → Webhooks → Add endpoint.
2. URL: `https://your-domain/integrations/stripe/webhook` (must match your reverse proxy → Paper).
3. Select event: `checkout.session.completed`.
4. Copy the endpoint **signing secret** into env/config as above.

## Discord bot (link gating)

- **Linked chat** (bridge channels): only users with an active row in `discord_links` can forward messages to Minecraft. Others get a short reply with instructions (rate-limited per user).
- **Slash** `/ping`, `/server`, `/player`, `/activity`: require a linked account (ephemeral message if not).
- `**/link`** and `**/status`** remain usable without a link so players can complete onboarding.

## Troubleshooting


| Symptom                          | Check                                                                                                       |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `/donate` says store unavailable | `store.enabled`, `store.stripe.enabled`, `STRIPE_SECRET_KEY` / `secret_key`, catalog not empty              |
| No clickable link                | `success_url` / `cancel_url` set; Stripe API not failing (check server log)                                 |
| Webhook 400 `bad_signature`      | `Stripe-Signature` header present; body not altered; `STRIPE_WEBHOOK_SECRET` matches CLI/Dashboard endpoint |
| Webhook 503 `store_disabled`     | Plugin store/Stripe toggles off or missing secret key                                                       |
| Grants not applied               | Same amount/currency as package; migration applied; DB connectivity                                         |


