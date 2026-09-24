# frontend

React storefront UI for InvestorBook - a catalog, cart, login, and order-status experience built
on top of the existing microservices backend. Not a Maven module and not registered with Eureka:
it's a plain Vite dev server that calls `api-gateway` directly from the browser over CORS, the
same way any external client of this system would.

Design reference (Figma): https://www.figma.com/design/WskdCUCFg3FfpHQtq40uZu/InvestorBook-Store-UI

## Stack

- Vite + React 19 + TypeScript
- Tailwind CSS v4 (via `@tailwindcss/vite`, no separate config file needed)
- React Router for client-side routing
- No state-management library - cart and auth are plain React Context (`src/context/`), which is
  all a demo storefront's state needs

## Backend it talks to

Every request goes through `api-gateway` (`VITE_API_BASE_URL`, defaults to `http://localhost:8765`
in `.env`) - never directly to a downstream service:

- `GET /order-service/products`, `GET /order-service/products/{id}` - public, no token (new
  catalog endpoints on `order-service`, see its `ProductController`)
- `POST /login` - `application/x-www-form-urlencoded` with `username`/`password`, returns the
  OAuth2 token response (`common`'s `AuthResponse`) - see `src/api/client.ts`
- `POST /order-service/orders` - `Authorization: Bearer <token>`, places an order for the cart
  total
- `GET /order-service/orders/{id}` - polled every 3s by the order-status page to show the saga's
  `PLACED → PAID → INVOICED → COMPLETED` progression (or `PAYMENT_FAILED`/`CANCELLED`) live
- `GET /order-service/orders` - every order (not just the caller's own - see `pages/DashboardPage.tsx`)
- `GET /order-service/orders/{id}/events` - an order's persisted event timeline, fetched lazily
  when its row is expanded on the dashboard
- `GET /dashboard/services` - this app's own aggregated health check across every service, public
  (see `api-gateway`'s `DashboardController`)

## The saga flow diagram

`components/ServiceFlowDiagram.tsx` renders the order-status page's live diagram: one node per
step of the real saga (`order-service` → `payment-service` → `invoice-service` →
`notification-service` → `order-service`), each with its own icon, colored by state (gray
pending, pulsing indigo active, green done). `PAYMENT_FAILED` and `CANCELLED` render distinct
short branches (declined / refunded) instead of the happy-path chain, rather than trying to force
every outcome through one fixed set of steps - see `buildNodes()` for the status→node mapping.
Product and service icons are hand-written inline SVGs under `src/icons/` (no icon library
dependency); `icons/tints.ts` gives each product a deterministic color pairing by id, reused
between the catalog grid and cart line items.

## The dashboard

`pages/DashboardPage.tsx` (behind a "Dashboard" link in the header once logged in) has two live,
polling sections:

- **Services**: every service in the system with an icon, colored green/gray by UP/DOWN, from
  `GET /dashboard/services` (polled every 5s). That endpoint lives on `api-gateway`, not here -
  a browser can't reach nine different origins/ports directly, so `api-gateway`'s
  `DashboardController` checks each one server-to-server (via Eureka's own `DiscoveryClient`, not
  hardcoded ports - see its Javadoc for the two exceptions that couldn't avoid one).
- **Orders**: every order across every customer (an ops view, not a "my orders" page), polled
  every 4s, filterable by the search field (client-side substring match on order id). Each row
  expands to its event timeline - not scraped console log text, but a real persisted audit trail
  (`order-service`'s `OrderEventLogEntity`/`OrderEventLogRecorder`), fetched lazily on first
  expand and cached per order id.

## Running it

Needs `api-gateway` (and everything it depends on - see the root `README.md`) running first for
anything beyond the empty-state UI to work.

```bash
npm install
npm run dev      # http://localhost:5173
```

`.env.example` documents the one environment variable (`VITE_API_BASE_URL`); copy it to `.env` to
override the default.

## What's deliberately not here

- No state library, no CSS-in-JS, no component library - Tailwind utility classes directly, per the
  design in Figma.
- No product write/admin UI - the catalog is seeded server-side by `order-service`'s
  `ProductCatalogSeeder`, not managed from this app.
- No token refresh - the access token is kept in `sessionStorage` for the session only; logging in
  again is the "refresh" story here, same as this repo's backend doesn't implement refresh-token
  rotation either.
