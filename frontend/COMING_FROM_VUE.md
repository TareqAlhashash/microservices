# React + TypeScript + Tailwind, for a Vue/JS/CSS developer

You already know how to build UIs — components, props, state, reactivity, routing, forms. None
of that is new. What's different is *how* React expresses those same ideas, plus two tools
(TypeScript, Tailwind) that aren't part of the language itself. This guide maps what you know
onto what's actually sitting in `frontend/src/` in this repo, so every concept has a real file
next to it, not a toy example.

Read it in order once, then use it as a reference while you read the actual source files.

---

## 1. The one mental shift that explains everything else

Vue's reactivity is **automatic and fine-grained**: `ref`/`reactive` wrap your data in a Proxy,
and the framework tracks exactly which parts of the DOM read which reactive values. Change
`count.value`, and only the DOM nodes that actually read `count` update.

React has no Proxy tracking. A component is a **plain function that returns what the UI should
look like right now**. When state changes, React re-runs the *entire function* and compares the
result to what was there before (this comparison is called "reconciliation" — it's why React
needs a `key` on list items, see §5). There's no dependency tracking to opt into; the function
just re-runs, and you get a fresh set of variables every time.

This explains a lot of things that otherwise feel arbitrary:
- Why state updates go through a setter function (`setCount(n)`) instead of direct assignment
  (`count.value = n`) — React needs to be *told* something changed, so it knows to re-run the
  function. Mutating a plain JS variable wouldn't trigger anything.
- Why `useEffect` needs an explicit dependency array (§6) — React can't automatically know which
  values your side-effect code "depends on" the way Vue's Proxy can; you have to say so.
- Why derived values often get wrapped in `useMemo` — recomputing on every re-run is the default,
  so expensive derivations get memoized explicitly instead of automatically.

Keep this "the function just re-runs" model in your head and the rest of React stops feeling
magic.

---

## 2. JSX: templates, but it's just JavaScript

Vue's `<template>` is a separate syntax the compiler understands (directives, `{{ }}`
interpolation). JSX is different: it's HTML-*shaped* syntax that compiles straight to
`React.createElement(...)` calls. Because of that, **anything that's valid inside `{ }` is just
JavaScript** — no special directive syntax to learn.

```tsx
// frontend/src/pages/CatalogPage.tsx
{loading && <p className="px-16 text-slate-500">Loading products…</p>}
{error && <p className="px-16 text-red-600">Couldn't load products: {error}</p>}
```

- `className` instead of `class` — `class` is a reserved word in JS, so JSX had to rename it.
- `{loading && <p>...</p>}` is the whole story on conditional rendering. There is no `v-if`. It's
  JS's `&&` short-circuit: if `loading` is falsy, the expression evaluates to `false`, and React
  renders nothing for `false`/`null`/`undefined`. For an if/else, use a ternary (see §5's
  `Header.tsx` example) instead of a directive.
- `{error}` interpolates a JS expression, same job as Vue's `{{ error }}`.

---

## 3. Components and props

Vue's `defineProps` (with a `<script setup>` type argument) becomes a plain TypeScript function
parameter:

```tsx
// frontend/src/components/ProductCard.tsx
export function ProductCard({ product }: { product: Product }) {
  const { addItem } = useCart()
  ...
  return (
    <div className="...">
      <h3 className="...">{product.name}</h3>
      <button onClick={() => addItem(product)}>Add to Cart</button>
    </div>
  )
}
```

A React component **is** the function. `{ product }: { product: Product }` destructures the
props object and types it inline (you'll usually pull that type out into an `interface` once it
grows past one field — see `CartContext.tsx`'s `CartContextValue` for that pattern). No
`defineProps`/`defineEmits` macros, no `.vue` single-file-component split into
`<template>`/`<script>`/`<style>` blocks — markup, logic, and (via Tailwind) styling all live in
the one function, in one `.tsx` file.

**Events don't "emit" upward the way Vue's `$emit`/`defineEmits` do.** A child just calls a
function prop the parent passed down — `onClick={() => addItem(product)}` is calling a regular
JS function, not dispatching a named event. If a child needs to notify a parent of something,
the parent passes a callback prop, same shape as any other prop.

---

## 4. State: `useState`

```tsx
// frontend/src/pages/CatalogPage.tsx
const [products, setProducts] = useState<Product[]>([])
const [error, setError] = useState<string | null>(null)
const [loading, setLoading] = useState(true)
```

`useState<Product[]>([])` is Vue's `ref<Product[]>([])`, minus the `.value`. Instead you get back
a `[value, setter]` pair (array destructuring — the pair is literally a 2-element array; the
names are yours to choose, `useState` doesn't know or care what you call them).

The important difference from `ref.value = x`: **you must call the setter, and you must not
mutate state in place.** This bites people coming from Vue/plain JS constantly — see §12.

```tsx
// frontend/src/context/CartContext.tsx — update via a new array, never lines.push(...)
function addItem(product: Product) {
  setLines((current) => {
    const existing = current.find((line) => line.product.id === product.id)
    if (existing) {
      return current.map((line) =>
        line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line,
      )
    }
    return [...current, { product, quantity: 1 }]
  })
}
```

`setLines((current) => ...)` — the **updater-function form** — is the React idiom for "new state
based on old state," used here instead of reading `lines` from closure and computing a next value
outside the setter (both work, but the function form avoids stale-value bugs if two updates
happen close together). `current.map(...)` returning a *new* object (`{ ...line, quantity: ... }`)
for the changed line, and leaving the rest as-is, is the standard "immutable update" pattern:
never `line.quantity++`, never `lines.push(x)`. Same idea as Vuex/Pinia mutations being disciplined
about not reaching into nested reactive state and hand-mutating it — except in React it's not a
style preference, it's required for the change to register at all.

---

## 5. Lists and conditionals: no directives, just JS

**Lists** (`v-for` → `.map()`):

```tsx
// frontend/src/pages/CatalogPage.tsx
{products.map((product) => (
  <ProductCard key={product.id} product={product} />
))}
```

`.map()` returns an array of JSX elements; JSX renders an array of elements just fine. `key` is
**not optional** — it's how React's diffing (§1) matches old elements to new ones across
re-renders without a Proxy watching identity for you; use a stable unique id, never the array
index if the list can reorder or items can be removed from the middle.

**Conditionals** (`v-if`/`v-else` → ternary or `&&`):

```tsx
// frontend/src/components/Header.tsx
{user ? (
  <div className="flex items-center gap-4">
    <span className="text-sm font-medium text-slate-500">{user.email}</span>
    <button onClick={logout}>Log Out</button>
  </div>
) : (
  <Link to="/login">Log In</Link>
)}
```

if/else → ternary. A single-branch if (no else) → `&&`, as in §2's loading/error example. There's
no `v-show` equivalent baked in either — toggle a Tailwind class conditionally (or just don't
render the element) instead of a framework-level display toggle.

---

## 6. Side effects: `useEffect`

This is the concept with the most genuine friction coming from Vue, because Vue gives you three
different, purpose-built hooks (`onMounted`, `watch`, `watchEffect`) where React gives you **one**
general-purpose hook and makes you express "when" via a dependency array.

```tsx
// frontend/src/pages/CatalogPage.tsx — the onMounted-shaped case
useEffect(() => {
  fetchProducts()
    .then(setProducts)
    .catch((err: Error) => setError(err.message))
    .finally(() => setLoading(false))
}, [])
```

The second argument, `[]`, is the dependency array. **Empty array = run once, after the first
render** (the `onMounted` case). Leave it off entirely and the effect runs after *every*
re-render. List values in it (`[orderId, user]`, next example) and the effect re-runs whenever
any of those values changes — that's the `watch([orderId, user], ...)` case, except React can't
infer the dependency list from what your code reads the way Vue's Proxy can (§1), so you write it
by hand. (ESLint's `react-hooks/exhaustive-deps` rule — not wired into this project, but standard
in most — flags a dependency you read but forgot to list, since a forgotten dependency is a real
class of bug: the effect closes over a stale value from the render it was created in.)

The richer, real-world case — polling with cleanup, `watchEffect` + `onUnmounted` combined:

```tsx
// frontend/src/pages/OrderStatusPage.tsx
useEffect(() => {
  if (!orderId || !user) return

  let cancelled = false
  let intervalId: ReturnType<typeof setInterval> | undefined

  async function poll() {
    try {
      const latest = await fetchOrder(orderId!, user!.accessToken)
      if (cancelled) return
      setOrder(latest)
      if (TERMINAL_STATUSES.includes(latest.status) && intervalId) {
        clearInterval(intervalId)
      }
    } catch (err) {
      if (!cancelled) setError(err instanceof Error ? err.message : 'Could not load the order')
    }
  }

  poll()
  intervalId = setInterval(poll, POLL_INTERVAL_MS)
  return () => {
    cancelled = true
    if (intervalId) clearInterval(intervalId)
  }
}, [orderId, user])
```

**The function your effect returns is the cleanup** — React calls it right before the effect
re-runs (because a dependency changed) and once more when the component unmounts. That's your
`onUnmounted`, expressed as "the last thing this effect itself hands back," not a separate hook.
The `cancelled` flag guards against a very real race: if the component unmounts (or `orderId`
changes) while a `fetch` is in flight, the promise still resolves later and would otherwise call
`setOrder` on a component that's gone — cleanup flips `cancelled`, the stale response is a no-op.
Vue's `watchEffect` cleanup (the function you can pass to `onCleanup`) solves the identical
problem; this is React's version of the same discipline, just manual instead of automatic.

---

## 7. Cross-component state: Context, instead of Pinia/provide-inject

No store library here (deliberately — see `frontend/README.md`). Global-ish state (cart, auth)
uses React's built-in Context, which is closer to Vue's `provide`/`inject` than to Pinia: no
separate store file with actions/getters, just a value threaded down the tree.

```tsx
// frontend/src/context/CartContext.tsx
interface CartContextValue {
  lines: CartLine[]
  itemCount: number
  total: number
  addItem: (product: Product) => void
  removeItem: (productId: number) => void
  updateQuantity: (productId: number, quantity: number) => void
  clear: () => void
}

const CartContext = createContext<CartContextValue | undefined>(undefined)

export function CartProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<CartLine[]>([])
  // ...addItem/removeItem/updateQuantity/clear defined here, closing over setLines...

  const itemCount = useMemo(() => lines.reduce((sum, line) => sum + line.quantity, 0), [lines])
  const total = useMemo(
    () => lines.reduce((sum, line) => sum + line.product.price * line.quantity, 0),
    [lines],
  )

  return (
    <CartContext.Provider value={{ lines, itemCount, total, addItem, removeItem, updateQuantity, clear }}>
      {children}
    </CartContext.Provider>
  )
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error('useCart must be used within a CartProvider')
  }
  return context
}
```

Three pieces, all in one file:
1. `createContext` — the "channel," typed as `CartContextValue | undefined` (undefined is the
   value *before* any Provider has supplied one — see point 3).
2. `CartProvider` — a component that owns the actual `useState`, and hands the current value (plus
   the functions that update it) to everything nested inside it via `<CartContext.Provider
   value={...}>`. This wraps the app in `App.tsx` (`<CartProvider><BrowserRouter>...`) — same
   shape as Vue's `app.provide()` at the root, or wrapping your tree in a Pinia-store-backed
   component, except the "store" here is just `useState` living inside a component.
3. `useCart()` — a **custom hook**, the React idiom for "reusable logic that itself uses other
   hooks." Any function whose name starts with `use` and that calls hooks internally (`useContext`
   here) is a custom hook; React's rule-of-hooks tooling treats that prefix as significant. This
   one wraps `useContext(CartContext)` and throws if it's `undefined` — i.e., "you used `useCart()`
   somewhere not wrapped in `<CartProvider>`" — a deliberate fail-fast instead of returning
   `undefined` silently. `useAuth()` in `context/AuthContext.tsx` is the same three-piece shape.

Consuming it is just calling the hook — no `inject('cart')` string key to typo, no separate
Pinia `useCartStore()` import from a store file:

```tsx
// frontend/src/components/Header.tsx
const { itemCount } = useCart()
```

---

## 8. Forms: controlled inputs, because there's no `v-model`

This is the sharpest edge coming from Vue. `v-model="email"` is sugar for binding `:value` and
listening for an input event in one directive. **React has no directive sugar for this at all** —
every form field's value has to be explicitly wired to state and back, every time:

```tsx
// frontend/src/pages/LoginPage.tsx
const [email, setEmail] = useState('')
...
<input
  type="email"
  value={email}
  onChange={(event) => setEmail(event.target.value)}
  placeholder="you@example.com"
/>
```

`value={email}` makes this a **controlled input**: the DOM input's displayed value is *driven by*
React state, not the other way around. Without `onChange` wired up, the field would be read-only
— typing wouldn't visibly do anything, because nothing would update `email`, so React would keep
re-rendering the input back to its old value. `onChange` fires on every keystroke (it's really
listening to the `input` event, not `change`, despite the name) and pushes the new value into
state; that state change triggers a re-render, and the input redisplays with what you just typed.
It looks like typing "just works," but it's a full read-write round-trip through state on every
character — `v-model`'s two-way binding is doing the identical round-trip, just hidden behind the
directive.

Submission is a plain DOM form event, handled with `preventDefault()` exactly like vanilla JS
(no `@submit.prevent` shorthand):

```tsx
async function handleSubmit(event: FormEvent) {
  event.preventDefault()
  setError(null)
  setSubmitting(true)
  try {
    await login(email, password)
    navigate('/cart')
  } catch (err) {
    setError(err instanceof Error ? err.message : 'Log in failed')
  } finally {
    setSubmitting(false)
  }
}

<form onSubmit={handleSubmit}>
```

---

## 9. Routing: `react-router-dom` vs `vue-router`

```tsx
// frontend/src/App.tsx
<BrowserRouter>
  <Header />
  <Routes>
    <Route path="/" element={<CatalogPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/orders/:orderId" element={<OrderStatusPage />} />
    <Route path="/dashboard" element={<DashboardPage />} />
  </Routes>
</BrowserRouter>
```

| Vue Router | React Router | Where |
|---|---|---|
| `createRouter({ routes: [...] })` config object | `<Routes><Route path=... element={...} /></Routes>` written as JSX, inline in the tree | `App.tsx` |
| `<router-view />` | `<Routes>` itself (it renders whichever `<Route>` matches) | `App.tsx` |
| `<router-link to="/cart">` | `<Link to="/cart">` | `Header.tsx` |
| `this.$router.push('/cart')` / `router.push(...)` | `const navigate = useNavigate(); navigate('/cart')` — a hook, called inside a component | `LoginPage.tsx` |
| `route.params.id` | `const { orderId } = useParams<{ orderId: string }>()` — a hook | `OrderStatusPage.tsx` |
| navigation guards (`beforeEnter`) | no direct equivalent — you gate rendering yourself, e.g. `OrderStatusPage`'s `if (!user) return <p>Log in...</p>` | `OrderStatusPage.tsx` |

The pattern throughout: Vue Router bakes navigation concerns into dedicated APIs and config;
React Router exposes the same capabilities as **hooks you call from inside a component**, and
route matching itself is just more JSX (`<Route>` elements), not a separate config file.

---

## 10. TypeScript, if you haven't used it beyond a few `.d.ts` glances

You already think in terms of shapes — Java made you fluent in "what fields does this object have
and what are their types" long before you saw a line of JS. TypeScript is that same instinct,
checked at compile time, erased entirely by the time it reaches the browser (there's no runtime
type-checking cost or behavior — it's purely a build-time tool, unlike, say, Java's reflection-
visible generics).

**Basic annotations** — a colon after the name:

```ts
let email: string = ''
function getEmail(user: AuthUser): string { ... }
```

**`interface`** describes an object shape — the closest thing to a Java interface, but purely
structural: a value satisfies `CartContextValue` if it *has the right shape*, with no `implements`
declaration needed anywhere (this is called "structural typing," vs. Java's "nominal typing" where
a class has to explicitly declare what it implements):

```ts
// frontend/src/types.ts
export interface Product {
  id: number
  name: string
  description: string
  price: number
  imageUrl: string
}
```

**Generics** work like Java's `<T>`, same intent, different syntax in a couple of spots:

```ts
useState<Product[]>([])        // like a Java List<Product> living in a variable
Promise<Order>                 // like a Java Future<Order>
Record<string, string>         // like a Java Map<String, String>
```

**Union types** are the one construct with no direct Java equivalent (short of a sealed interface
with a fixed set of implementations) — a value restricted to an exact, enumerated set of options:

```ts
// frontend/src/types.ts
export type OrderStatus = 'PLACED' | 'PAID' | 'INVOICED' | 'COMPLETED' | 'PAYMENT_FAILED' | 'CANCELLED'
```

This is checked at compile time like a Java `enum`, but it's just string literals under the hood
— no `OrderStatus.PLACED.name()` ceremony, you compare directly against `'PLACED'`.

**`type` vs `interface`**: mostly interchangeable for object shapes; `interface` can be
re-opened/extended later (closer to how a Java interface can be implemented by multiple things),
`type` is needed for unions (`OrderStatus` above) and other non-object shapes. This codebase uses
`interface` for object shapes and `type` for unions — pick one convention and stay consistent
within a file, the way you'd pick a consistent style in Java.

**`T | null`** is how "this might not have a value" is expressed — TypeScript has no checked-
exceptions equivalent of Java's `Optional<T>` built in; `| null` (or `| undefined`) *is* the
idiomatic optional, and the compiler forces you to narrow it (an `if` check, `??`, `?.`) before
you can use it as a `T`:

```ts
const [error, setError] = useState<string | null>(null)
{error && <p>{error}</p>}   // the && here is also doing null-narrowing
```

**How TypeScript plugs into React specifically**: component props are just a typed function
parameter (§3); state is a typed generic (§4); event handlers get typed event parameters instead
of an untyped `e` (`(event: FormEvent) => ...`, `(event: React.ChangeEvent<HTMLInputElement>) =>
...` for an input's `onChange`). None of it is React-specific syntax — it's ordinary TypeScript,
applied to ordinary JavaScript functions that happen to return JSX.

---

## 11. Tailwind CSS: utility classes instead of a stylesheet

Vue components typically carry a `<style scoped>` block — you name a class, write its CSS once,
reference the name in the template. Tailwind skips the naming step entirely: instead of inventing
`.product-card` and defining its rules in CSS, you compose pre-defined utility classes directly
in `className`, each one doing exactly one thing:

```tsx
// frontend/src/components/ProductCard.tsx
<div className="flex flex-1 flex-col gap-3 rounded-xl bg-white p-4 shadow-sm">
```

Reading it left to right: `flex flex-1 flex-col` (flexbox, grow, stack vertically) `gap-3`
(spacing between children) `rounded-xl` (border radius) `bg-white` (background) `p-4` (padding)
`shadow-sm` (drop shadow). Every one of these is a fixed, predefined value from Tailwind's design
scale (spacing steps, a fixed color palette, etc.) — the same discipline a good BEM/design-token
CSS setup gives you, except enforced by the class names existing or not existing at all, rather
than by convention you have to maintain by hand.

**Responsive and state variants are prefixes**, not media queries or `:hover` blocks you write
yourself:

```tsx
// frontend/src/pages/CatalogPage.tsx
<div className="grid grid-cols-1 gap-6 px-16 pb-16 sm:grid-cols-2 lg:grid-cols-4">
```
1 column by default, 2 columns from the `sm` breakpoint up, 4 from `lg` up — read left to right as
"mobile-first, then override at wider breakpoints." Same idea, inline:
```tsx
// frontend/src/components/Header.tsx
className="rounded-lg bg-indigo-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-indigo-700"
```
`hover:bg-indigo-700` is the entire hover state — no separate `:hover` rule to write or find later.

**Conditional/computed classes** are just template literals or conditional expressions, since
`className` is an ordinary JS string:

```tsx
// frontend/src/components/ProductCard.tsx
className={`flex h-40 w-full items-center justify-center rounded-lg ${tint.bg}`}
```

**Where the styling actually "lives"**: nowhere separate. There's no `.css` file to go hunting
through for `.product-card`'s rules — the styling for a piece of UI sits right next to (in fact,
inside) the markup that uses it. The tradeoff for that convenience is that `className` strings get
long and a repeated combination (a button's classes, say) has no free "extract to a CSS class"
move — you extract a **React component** instead (this project doesn't have a `<Button>`
component yet; if you find yourself repeating the same class string across pages, that's the sign
to make one, same instinct as extracting a repeated Vue component).

---

## 12. Vue/JS habits that will actually bite you

1. **Mutating state in place.** `products.push(x)` or `line.quantity++` on a piece of `useState`
   does *not* trigger a re-render — React only reacts to the setter being called with a new
   value, and it compares by reference for objects/arrays, not deep equality. Always build a new
   array/object (`[...current, x]`, `{ ...line, quantity: n }`) as in §4's `CartContext` example.
2. **Forgetting a `useEffect` dependency.** The effect closes over whatever variables existed at
   the render it was created in. Miss one in the array and the effect keeps using a stale value
   from render #1 forever, instead of re-running when that value changes on render #5. If you
   read a value inside an effect, it almost always belongs in the dependency array.
3. **Expecting `v-model`-style two-way binding for free.** There isn't one. Every form field is a
   controlled input (§8) you wire by hand, every time.
4. **Missing or index-based `key` props on lists.** React can't diff a list correctly without a
   stable identity per item (§5) — an index works only if the list never reorders, never has
   items removed from the middle, and never has items inserted at the front.
5. **Expecting automatic dependency tracking anywhere.** Nothing in React watches what a function
   reads the way Vue's Proxy does. `useMemo`/`useCallback`'s dependency arrays have the exact same
   "list every value you actually depend on" requirement as `useEffect`'s.

---

## 13. Quick reference

| Vue | React (this repo) |
|---|---|
| `<script setup>` SFC | a function component in a `.tsx` file |
| `defineProps<{ product: Product }>()` | `({ product }: { product: Product })` as the function's parameter |
| `ref(x)` / `reactive(x)` | `useState(x)` |
| `computed(() => ...)` | `useMemo(() => ..., [deps])` |
| `watch`/`watchEffect` | `useEffect(() => ..., [deps])` |
| `onMounted` | `useEffect(() => ..., [])` |
| `onUnmounted` / `watchEffect` cleanup | the function returned from `useEffect` |
| `provide`/`inject`, or a Pinia store | `createContext` + a `Provider` + a custom `useX()` hook |
| `v-if` / `v-else` | `{cond ? <A/> : <B/>}` |
| `v-if` alone | `{cond && <A/>}` |
| `v-for="x in xs" :key="x.id"` | `{xs.map(x => <X key={x.id} />)}` |
| `v-model="x"` | `value={x} onChange={e => setX(e.target.value)}` |
| `@click="fn"` | `onClick={fn}` |
| `router-link` / `router.push` | `<Link>` / `useNavigate()` |
| `route.params.id` | `useParams()` |
| `<style scoped>` | Tailwind utility classes in `className` |
| TS (if used) `defineProps` generic | the same generics, just plain TypeScript |

## 14. If your Vue background is the Options API (`data()`/`created()`/`mounted()`/`methods`)

Everything above compares against `ref`/`reactive`/`<script setup>` (Vue 3's Composition API).
If what you actually used was Vue 2's Options API — a `data()` function, separate `created()` and
`mounted()` lifecycle hooks, a `methods: {}` object, maybe even the template kept in its own
`.html` file glued to the options object by a webpack loader, rather than a single-file
`.vue` — the mapping is still "one function," just from a different starting shape:

| Options API | React |
|---|---|
| `data() { return { a, b } }` | one `useState` **per field**, not one object — Vue's `data()` bundles unrelated state together because it has to return a single object; React has no such constraint |
| `created()` + `mounted()` | both fold into **one** `useEffect(() => {...}, [])` — React doesn't distinguish "before the DOM exists" from "after it's committed" the way two separate hooks do |
| `methods: { foo() {...} }` | plain functions declared in the component function body, nothing special |
| `this.$refs.x.someMethod()` | `useRef` — see below |
| `this.$root.$emit(...)` / `$on(...)` (global event bus, Vue 2 only) | no equivalent, on purpose — Context (this repo's `CartContext`/`AuthContext`) or lifting state up instead |
| `this.$store.state.x` / `dispatch(...)` (Vuex) | Context + `useState` here (or Redux/Zustand if a project actually wants a Vuex-shaped global store) |
| template kept in a separate `.html`, required into the options object | not a thing — JSX lives inline in the same function, always |

**`$refs` calling a child's method imperatively** is the one pattern above that needs a second
hook, `useRef`, paired with the child exposing what it allows to be called:

```tsx
const sideMenuRef = useRef<SideMenuHandle>(null)
// ...
sideMenuRef.current?.toggleMobileMenu(event)   // this.$refs.sideMenu.toggleMobileMenu(event)
```

The child has to opt in to being controlled this way (via `useImperativeHandle`) — it's a
deliberately uncommon escape hatch in React, not the default way components talk to each other
the way `$refs` casually is in Vue. The more idiomatic React instinct, most of the time a `$refs`
call like this shows up, is to lift the state up into the parent and pass it down as a prop
instead of reaching into the child at all.

**Three patterns from Options-API-era Vue apps that have no built-in React equivalent at all** —
you reach for a library or a different approach, not a hook:
- `<transition>` — no built-in animation system; `framer-motion` or `react-transition-group`
  (the latter a direct spiritual port) fill the gap.
- `v-show` — no "stay mounted, just hide" toggle. Keep the element in the JSX unconditionally and
  flip a class/style yourself instead of `v-if`'s unmount/remount.
- the global `$root.$on`/`$emit` event bus — removed from Vue 3 for good reason (untraceable
  coupling); Context is the replacement, not a hook-based bus.

## 15. How to actually get fluent with it

Don't read this guide twice — go build with it. The fastest path from here:
1. Open `frontend/src/pages/CartPage.tsx` (not quoted above) and read it cold, resolving every
   line against §1–9 above.
2. Add a small, real feature to the storefront — a "clear cart" confirmation, a toast on
   checkout success, a product search box on the catalog page — and notice which section of this
   guide you reach for.
3. Once §6 (`useEffect`) feels natural, that's the signal you've actually internalized the
   re-render model in §1, not just memorized the hook's syntax. That's the one concept in this
   list with no clean Vue analogy, and the one worth the most deliberate practice.
