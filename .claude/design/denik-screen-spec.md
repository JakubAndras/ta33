# Deník / Itinerář - design spec obrazovky

Výchozí tab appky. Zdroj vizuálu: `ui_kits/ta33-app/Screens.jsx` (`DenikBeforeScreen`, `DenikOnRouteScreen`, `DenikLockedScreen`) + `Components.jsx`.
Tokeny a komponenty viz [design-system.md](design-system.md).

## Účel

„Přehledná osa jen s kontrolami" - stav běhu závodu: kolik kontrol splněno, která je další, mezičasy. Detailní itinerář (všechny body trasy) sem nepatří - ten je na obrazovce Mapa.

## Napojení na existující sdílenou logiku (hotovo)

- **`RunLogViewModel.state: StateFlow<LogUiState>`** (FR-04) - hlavní zdroj obsahu. `bind(runId, routeId)`.
- **`AppViewModel.state: StateFlow<AppUiState>`** (FR-01) - gate stavu (readiness, activeRunId, activeRouteId).
- **`DownloadViewModel`** (FR-11) - pro CTA „Stáhnout data akce" (nebo navigace na Preparation obrazovku).

`LogUiState` (FR-04): `entries: List<RunLogEntry>` (řazeno dle ordinálu), `collectedCount`, `totalCount`, `nextControl: ControlPoint?`, `finishState`, `isComplete`, `isRunFinished`, `loading`. `progressLabel` = `"2 z 5"` (dev-only, finální pluralizace/jednotky jsou UI concern).
`RunLogEntry`: `control`, `state: ControlPointState {LOCKED, ACTIVE, DONE, FINISH}`, `collectedAtMillis?`, `splitMillis?`.
`AppUiState` (FR-01): `readiness {LOADING, NOT_READY, PREPARING, READY}`, `contentAvailability {UNKNOWN, ABSENT, PRESENT}`, `activeRouteId?`, `activeRunId?`.

## Stavy obrazovky (řízené AppUiState)

### 1. LOADING → splash
`readiness == LOADING` (`startDestination == null`). Nenavigovat, jen logo/spinner. (Splash řeší app shell, ne tato obrazovka.)

### 2. Obsah není stažený → `DenikBeforeScreen`
`readiness == NOT_READY` / `contentAvailability == ABSENT`.
- **`IdentityCard`** (slate-800, radius 20): datum „Sobota 19. 9. 2026" (overline slate-300) · místo „Teplice n. Metují" (display 34) · podtitul „Start 7:00-10:00 · prezentace u sokolovny" (slate-200).
- **`PaperCard`** centrovaná (padding 24/20):
  - kruh 56 `orange-100` s `download` ikonou (orange, stroke 2.4)
  - h1 700: „Akce ještě není stažená" (soft „ještě")
  - body slate-500: „Stáhni si trasy, kontroly a mapu, dokud máš signál. Na trase pak vše funguje offline."
  - **`PrimaryButton`**: „STÁHNOUT DATA AKCE · 84 MB" → spustí download (DownloadViewModel) / navigace na Preparation.
- *(Volitelná varianta `DenikLockedScreen` - když není zaplaceno: `lock` ikona, „Stažení uzamčeno", „Přejít na platbu". Mimo Etapu 1, jen zaznamenat.)*

### 3. Staženo / na trase → `DenikOnRouteScreen`
`readiness == READY` a je `activeRunId` + `activeRouteId`. Bind `RunLogViewModel`.
- Nahoře **`OfflineBanner`** (`warning-tint`, ⚡): „Offline režim - záznamy se uloží lokálně". *(Zobrazit dle skutečného offline stavu - ConnectivityMonitor.)*
- **Progress `PaperCard`** (padding 16/18):
  - řádek: vlevo „Trasa A · 33 km" (body 700), vpravo velké **display-2 „2 / 5"** (z `collectedCount`/`totalCount`)
  - progress bar výšky 10, radius pill, `slate-100` track, `orange` fill na `collectedCount/totalCount`
- **`Overline` „Příští checkpoint"** → `KPRow` pro `entries` kde `state != DONE` (ACTIVE oranžový glow swatch s číslem, LOCKED šedý dim 0.55, FINISH šedá hvězda). Sub: ACTIVE „Další · 310 m" *(vzdálenost z FR-08/live, jinak vynechat)*, LOCKED „Zamčeno", DONE „Splněno · HH:MM".
- **`Overline` „Hotovo"** → `KPRow` pro `entries` kde `state == DONE` (zelený fajfka swatch, sub „Splněno · HH:MM" z `collectedAtMillis`).
- Prázdný běh (0 sebráno): první KP ACTIVE, zbytek LOCKED, progress „0 / 5" - funguje bez úprav.

## Formátování (UI/lokalizace - čeština)

- Progress „2 / 5" jako **display číslo** (mezera kolem `/`). Label kontrol malý overline.
- Čas sběru/mezičas `HH:MM` (z `collectedAtMillis` přes TimeProvider zónu). Vzdálenost <1 km v m (`310 m`), >1 km s čárkou.
- KP titul: „KP-03 · Sloní pramen" (`·` separátor). Cíl: „Cíl · Adršpach".
- Stavová slova lowercase: „Zamčeno". Splněno s časem: „Splněno · 08:14".

## Komponenty k vytvoření (Compose / SwiftUI)

`IdentityCard`, `PaperCard`, `PrimaryButton`, `OutlineButton`, `Overline`, `OfflineBanner`, `StatChip`, `KPRow`, (`BottomNav`, `ScanFab` - patří app shellu). Namapovat 1:1 z `Components.jsx` na design tokeny.

## Mimo rozsah této obrazovky (závislosti)

- **App shell**: bottom nav (Deník/Mapa/Profil), scan FAB, tab routing, splash - patří do UI plánu app skeletonu (FR-01), řeší se zvlášť.
- **Fonty** Big Shoulders Display + Inter - přidat do projektu (potvrdit substituci).
- **Scan / Splnění** obrazovky - samostatné (FR-08/09 UI).
- Skutečná vzdálenost k další kontrole „310 m" - z FR-05/08 live location (v deníku volitelné).
