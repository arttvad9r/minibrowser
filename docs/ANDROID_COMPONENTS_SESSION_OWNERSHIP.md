# Android Components session ownership cutover

This document defines the ownership boundary for the migration from MiniBrowser's raw `GeckoSession`
management to Mozilla Android Components (A-C). It is intentionally narrower than the general browser
migration: the goal is to prevent two owners from opening, closing, restoring, observing, or replacing
the same live Gecko session.

The A-C behavior referenced below is from Firefox / Android Components `154.0.1`.

## Current ownership

`TabManager` is still the live-session owner. It currently:

- constructs every raw `GeckoSession`;
- installs MiniBrowser navigation, progress, content, history, prompt, permission, download and context-menu delegates;
- opens sessions against the application `GeckoRuntime`;
- closes sessions when tabs are removed, hibernated, browser data is cleared, the host is destroyed, or a crashed session is replaced;
- keeps raw `GeckoSession.SessionState` for restore/persistence;
- applies the hot-tab budget and manually reopens cold tabs.

`BrowserStore` is shadow state only. `EngineMiddleware` is installed, but MiniBrowser does not dispatch
`CreateEngineSessionAction` and does not link the render-only sidecar into `BrowserStore`.

`GeckoEngineView` currently renders a temporary `GeckoEngineSession` facade around the existing raw
session. The facade is created with `openGeckoSession=false`, all delegates it temporarily installs are
restored, and MiniBrowser never calls `close()` on that borrowed facade.

## A-C lifecycle behavior that constrains the cutover

A-C 154.0.1 makes linked `EngineSession` lifetime an explicit BrowserStore concern:

- `CreateEngineSessionMiddleware` calls `engine.createSession(...)`, applies the tab's `private` and
  `desktopMode` state, restores `engineSessionState` when present, and dispatches
  `LinkEngineSessionAction`.
- `TabsRemovedMiddleware` dispatches `UnlinkEngineSessionAction` and then calls `close()` on the linked
  `EngineSession` whenever a tab is removed.
- `SuspendMiddleware` also unlinks and closes the `EngineSession`; a later
  `CreateEngineSessionAction` creates a new session and restores the saved `engineSessionState`.
- `LinkingMiddleware` unregisters the engine observer on `UnlinkEngineSessionAction`; unlinking by
  itself is not a second close operation.

Therefore a linked session must never remain owned by `TabManager.closeIfOpen()` at the same time.

Relevant A-C sources:

- `browser/state/engine/middleware/CreateEngineSessionMiddleware.kt`
- `browser/state/engine/middleware/TabsRemovedMiddleware.kt`
- `browser/state/engine/middleware/SuspendMiddleware.kt`
- `browser/state/engine/middleware/LinkingMiddleware.kt`
- `browser/state/reducer/EngineStateReducer.kt`

## Preconditions

The live cutover must not start until all of these are true.

### 1. BrowserStore has creation-critical state

Before A-C creates a session, BrowserStore must already contain the values used by
`CreateEngineSessionMiddleware`:

- tab id and URL;
- private mode;
- desktop mode;
- selected tab;
- restorable engine-session state when available.

The migration bridge now mirrors desktop mode through the public
`ContentAction.UpdateTabDesktopMode` action and passes `desktopMode` when creating shadow tabs.

### 2. Raw delegate behavior has an A-C destination or an explicit retained policy

The cutover cannot simply replace `GeckoSession` with `GeckoEngineSession`, because A-C installs the
Gecko delegates that currently belong to MiniBrowser. Every current delegate responsibility needs one
of these outcomes first:

- move to the corresponding A-C feature/delegate;
- keep a small MiniBrowser policy adapter behind that A-C feature;
- remain intentionally custom with a documented parity reason.

Current known blockers include prompt host integration and ISO `WEEK` boundary/range parity,
authenticated download/header semantics, history error filtering, and link+media context-menu parity.

### 3. Session-state persistence is versioned before ownership moves

Current `TabStore` persists `GeckoSession.SessionState.toString()` directly. A-C exposes persistence
through the `EngineSessionState` interface instead.

`GeckoEngineSessionState.actualState` is internal to `browser-engine-gecko`; MiniBrowser must not
reach into it or depend on the private `GECKO_STATE` JSON key. The ownership migration should instead
introduce a versioned persisted engine-state payload using the public `EngineSessionState.writeTo(...)`
contract and restore it through `Engine.createSessionStateFrom(...)`.

This persistence change may reuse the existing tab metadata file, but it must be distinguishable from
the current raw Gecko state string so old installs can be migrated safely.

### 4. Render binding no longer borrows raw sessions

The render-only `GeckoEngineSession` sidecar is a transition tool, not the owned-session model. Before
A-C becomes session owner, rendering must consume the `EngineSession` linked to the selected
BrowserStore tab (for example through the A-C session/engine-view presenter path). Once that happens,
the borrowed sidecar and delegate snapshot/restore logic can be removed.

## Cutover sequence

The ownership change should be one directional boundary, not a long-lived mixed mode.

1. **Persist A-C-compatible state.** Teach restore/persistence to round-trip versioned
   `EngineSessionState` while still retaining the ability to read the existing raw Gecko payload.
2. **Finish delegate/feature seams.** Resolve or explicitly retain the remaining prompt, download,
   history, context-menu and permission/navigation policies before A-C installs live delegates.
3. **Make BrowserStore the tab/session creation source.** Restore tab metadata into BrowserStore and
   let `CreateEngineSessionAction` create only the sessions that need to be hot.
4. **Switch rendering to the linked EngineSession.** Remove the borrowed raw-session sidecar from the
   selected-tab render path.
5. **Switch navigation/reload/back/forward/desktop operations to A-C use cases/actions.** After this
   point raw `GeckoSession` navigation calls must not be used for BrowserStore-owned tabs.
6. **Switch hibernation to `SuspendEngineSessionAction`.** Keep MiniBrowser's hot-tab selection policy
   if desired, but let A-C perform unlink/close/state restore.
7. **Switch tab removal to BrowserStore actions.** `TabsRemovedMiddleware` becomes the only close owner;
   remove the matching raw `closeIfOpen()` path for BrowserStore-owned tabs.
8. **Switch crash recovery to A-C engine state.** Stop replacing `Tab.session` manually once A-C owns
   creation and restore.
9. **Delete raw session ownership infrastructure.** Remove the obsolete session field/lifecycle paths,
   sidecar helper, duplicate delegate wiring and shadow-state bridge pieces that are no longer needed.

## Hard invariant during implementation

For any tab, exactly one of these modes is allowed:

- **Raw-owned:** `TabManager` may open/close/replace its raw `GeckoSession`; BrowserStore must not hold
  an owned linked `EngineSession` for that tab.
- **A-C-owned:** BrowserStore/EngineMiddleware may create/link/suspend/close its `EngineSession`;
  `TabManager` must not close or replace the underlying Gecko session.

There is no supported third mode where a borrowed raw session is linked into BrowserStore while both
`TabManager` and A-C retain close authority.

## Prompt-specific parity before PromptFeature

The current prompt seam remains intentionally non-live:

- MiniBrowser's production file picker now uses an engine-neutral MIME normalization policy that can
  be reused from `PromptRequest.File`.
- popup target filtering already lives outside raw prompt UI plumbing in navigation policy.
- current HTML date/time formatting, including the ISO week-based year for `WEEK`, is locked by unit
  tests.
- A-C's Gecko prompt adapter does handle raw Gecko `WEEK` prompts: it exposes them as
  `PromptRequest.TimeSelection` and parses/confirms values with
  `SimpleDateFormat("yyyy-'W'ww", Locale.ROOT)`. The absence of a separate `WEEK` value in
  `PromptRequest.TimeSelection.Type` is therefore not itself a parity blocker.
- the formatter semantics are still different. MiniBrowser uses `WeekFields.ISO`, while A-C's
  `SimpleDateFormat` pattern uses calendar year `yyyy` plus locale calendar week rules. For example,
  MiniBrowser intentionally formats 2021-01-01 as `2020-W53`, whereas that A-C formatter produces
  `2021-W01`. Default/min/max and confirmation behavior around ISO week-year boundaries must be
  bridged or explicitly accepted before `PromptFeature` owns these prompts.
- `PromptFeature` uses a `FragmentManager`, while MiniBrowser currently hosts Compose in
  `ComponentActivity`; the host integration must be chosen deliberately rather than changing the
  Activity base class as an incidental side effect of the session cutover.
