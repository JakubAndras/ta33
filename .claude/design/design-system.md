# TA33 — Design System (distilát pro implementaci)

Zdroj: Claude Design projekt „TA33 Design System" (`README.md`, `colors_and_type.css`, `ui_kits/ta33-app/`).
URL: https://claude.ai/design/p/5105d303-ffec-4143-864d-a61d529f3c9d

> **Pozor:** design brief je psaný pro Flutter; náš projekt je **KMP → Compose Multiplatform (Android) + SwiftUI (iOS)**.
> Vizuální direktiva je 1:1 přenositelná, jen „production notes" pro Flutter mapujeme na Compose/SwiftUI.
> Tokeny patří do `androidApp/src/main/kotlin/com/example/ta33/ui/theme/{Color,Type,Dimens,Theme}.kt`
> a `iosApp/iosApp/Theme/{Color+TA33,Font+TA33,Ta33Metrics}.swift`.

## ⚠️ KLÍČOVÝ PRINCIP — jednotná značka, nativní vzhled per platforma

Design brief (a jeho `index.html` prototyp) vypadá jako **jedno univerzální UI pro obě platformy**. **NENÍ to tak, jak se to má postavit.** Náš stack je vědomě **Alza-style**: KMP se sdíleným jádrem/ViewModely, ale **nativní UI vrstva zvlášť** — Jetpack Compose (Material) na Androidu, SwiftUI na iOS (viz `project-stack.md` §3, §7). Cíl je, aby aplikace **působila jako nativní Android appka na Androidu a jako nativní iOS appka na iOS** — ne jako jeden mockup naklonovaný 1:1 na obě.

**Co je sdílené (kontrakt, drž 1:1 na obou):**
- **Značka a role barev** — orange = akce/live, slate = identita, green = úspěch; cream pozadí.
- **Typografie** (rodiny + škála), **ikonografie záměr** (Lucide styl / 2px), **spacing/radii tokeny**.
- **Microcopy a tón** (čeština, „ty", desetinná čárka, žádné emoji) — texty jsou identické.
- **Doménové koncepty** — stavy kontroly (locked/active/done/finish), progress „2 / 5", offline stav.

**Co je platform-specific (uzpůsob konvencím platformy, mockup je jen inspirace):**
- **Bottom navigation** — Android: Material3 `NavigationBar` (nebo náš plovoucí pill, drží-li Material feel); **iOS: nativní `TabView` / tab bar, na novém iOS „liquid glass"** vzhled. NEklonovat Android pill na iOS.
- **Scan FAB** — Android má FAB idiom; na iOS řešit nativněji (toolbar akce / prominentní tlačítko), ne Material FAB.
- **Modály / sheety** — iOS nativní `.sheet` (grabber, detents, gumové chování); Android `ModalBottomSheet`.
- **Navigace a přechody** — Android Compose Navigation + Material přechody + systémové zpět/predictive-back; iOS `NavigationStack` + swipe-back gesto.
- **Scroll fyzika, haptika, ripple vs. highlight, safe areas, status bar, Dynamic Type, systémové dark mode** — vždy nativní.
- **Systémové afordance** — datum/čas, sdílení, oprávnění dialogy = nativní komponenty.

**Pravidlo:** *Sdílená značka + obsah + tokeny; nativní chrome + interakce + navigace.* Když je konflikt mezi mockupem a platformní konvencí, **vyhrává platformní konvence** (s dodržením barevných/typo tokenů značky). Mockup ukazuje *co* a *jaká značka*, ne *jak se to má chovat na konkrétní platformě*.

## Brand & role barev

Tři role, nic víc:
- **Slate-dark** = identita / autorita (date card, scan modal, aktivní tab, „STARTOVNÍ ČÍSLO").
- **Orange** = akce / „next" / živý stav (CTA, aktivní kontrola, breadcrumb, scan rohy, scan FAB).
- **Green** = jednoznačný úspěch (splněno, fajfka). Jen pro completion.

Vše ostatní = parchment, paper, slate-grey neutrály. Mood: *rugged but kind* — cream pozadí (nikdy čistě bílé), orange co září, ne křičí.

## Barevné tokeny

| Token | Hex | Použití |
|---|---|---|
| `orange` | `#F76A0E` | **primární** — CTA, breadcrumb, aktivní kontrola |
| `orange-600` | `#C45607` | hover / press |
| `orange-100` | `#FCDEC1` | bublina download ikony |
| `orange-50` | `#FFF1E2` | glow pod velkým CTA |
| `slate-900` | `#15202B` | headlines, numerika (`fg-strong`) |
| `slate-800` | `#1C2A36` | tmavé karty, scan modal, aktivní tab (`fg-default`) |
| `slate-500` | `#5A6C7A` | captions, labels (`fg-muted`) |
| `slate-400` | `#8492A0` | hint / placeholder (`fg-faint`, jen ≥14sp) |
| `slate-300` | `#B6BFC8` | zamčený KP swatch |
| `slate-200` | `#D2D9DE` | locked chip bg |
| `slate-100` | `#E6EAEC` | dividery, stat-chip bg |
| `cream` | `#F7F2EA` | **hlavní pozadí appky** (nikdy bílá) |
| `cream-deep` | `#EFE7D7` | |
| `paper` | `#FFFFFF` | povrch karet |
| `map-tile` | `#DEE7DC` | sage offline dlaždice |
| `map-grid` | `#C9D3C5` | mřížka dlaždic |
| `success` | `#1FA85A` | splněno / fajfka (`success-tint` `#D9F3E3`) |
| `warning` | `#E8A92A` | ⚡ offline banner (`warning-tint` `#FBE9C2`) |
| `error` | `#D63A2F` | (TA50 route, error-tint `#F8D9D5`) |
| `info` | `#2E6FB5` | GPS tečka, mapový dot (`info-tint` `#D6E5F4`) |

**Stavy kontroly (KP):**
- locked: bg `slate-300`, fg `#fff` (+ řádek dim opacity 0.55)
- active: bg `orange`, fg `#fff`, glow `rgba(247,106,14,0.45)`
- done: bg `success`, fg `#fff` (fajfka)
- finish (locked): bg `slate-300`, bílá hvězda

**Foreground (WCAG AA na cream i white):** `fg-strong` slate-900, `fg-default` slate-800, `fg-muted` slate-500, `fg-on-dark` cream, `fg-on-orange` #fff (jen bold).

## Typografie

- **Display:** `Big Shoulders Display` (Black 900/800) — chunky condensed, UPPERCASE, tracking `0.01em`. Pro čísla-hrdiny a názvy míst.
- **Body/UI:** `Inter` (400–700) — čitelnost na slunci, dobré české diakritiky.
- *(Obě jsou Google Fonts substituce — brief je flaguje k potvrzení / nahrazení licencovanými soubory.)*

| Styl | Font | Size/Line/Weight | Použití |
|---|---|---|---|
| display-1 | display | 40/40/900 UPPER | „TEPLICE N. METUJÍ", „OFFLINE MAPA" |
| display-2 | display | 32/34/900 UPPER | „2 / 5", „310 m" |
| display-3 | display | 22/24/800 | numerické chipy |
| h1 | body | 24/30/700 | „Akce ještě není stažená" |
| h2 | body | 20/26/700 | „KP-02 · Sloní pramen" |
| h3 | body | 17/22/600 | tituly řádků v seznamu |
| body | body | 16/24/400 | běžný text |
| small | body | 14/20/400 | „Další · 310 m" |
| caption | body | 12/16/600 | |
| overline | body | 13/16/700, tracking `0.10em`, UPPER | „STARTOVNÍ ČÍSLO", „HOTOVO" |
| button | display | 16/20/800, tracking `0.04em`, UPPER | „STÁHNOUT DATA AKCE" |

## Spacing (4px base)

`space-1`=4 · `2`=8 · `3`=12 · `4`=16 (okraj karty) · `5`=20 (mezera mezi kartami) · `6`=24 (interiér karty) · `7`=32 · `8`=40 · `9`=56 · `10`=72.

## Radii

`xs`=6 · `sm`=10 · `md`=14 (malé chipy) · `lg`=20 (výchozí karta) · `xl`=24 (primární povrchy) · `2xl`=28 (mapový sheet) · `pill`=999 (tab toggle, bottom nav, CTA).

## Elevation / shadows

- `shadow-card` — výchozí zdvih karty, velmi jemný.
- `shadow-pop` — plovoucí bottom nav.
- `shadow-sheet` — vzhůru směřující stín mapového sheetu.
- `shadow-cta-glow` — oranžové CTA má teplý glow: `0 0 0 8px rgba(247,106,14,.16), 0 6px 18px rgba(247,106,14,.35)`.
- `shadow-scan-glow` — totéž na FAB a rozích scan rámečku.

## Motion

- `dur-base` 200ms `ease-standard` — tab swap, expanze sheetu, press stavy.
- `dur-slow` 320ms `ease-emph` — splnění screen (fajfka se kreslí 320ms, čas se odpočítává).
- Scan rohy pulzují 1.6s loop (0.7 → 1.0 opacity).
- **Žádné bounces, žádné springy.** Uživatel je zpocený a venku; překvapení = špatně.
- Press: scale 0.97 / 120ms; CTA při stisku ztrácí glow.
- Focus ring: 2px `orange-600` na tmavém, 2px `slate-800` na světlém, offset 2px. Nikdy neodstraňovat.

## Layout pravidla

- Mobile only, design width 390px, okraj 16–20px.
- Vertikální rytmus: karty odděleny `space-5` (20px); sekce uvnitř karty `space-4` (16px); řádky formuláře 1px `slate-100` divider (nikdy explicitní border).
- **Bez borderů** (2 výjimky: outline CTA „Stáhnout dlaždice" = orange 2px; stat chip = solid slate-100 fill).
- Dole plovoucí **pill nav** (`shadow-pop`, 16px boční okraj). V režimu „Na trase" oranžový kruhový **scan FAB** vpravo od pillu, s vlastním glow.
- One-handed: primární CTA v dolní třetině, hit targets ≥ 48dp, FAB v dosahu palce.

## Komponenty (atomy)

| Komponenta | Popis |
|---|---|
| `IdentityCard` | slate-800 karta, radius 20, bílý text: datum (overline slate-300) + místo (display 34) + podtitul (slate-200). „Kdo a kdy". |
| `PaperCard` | bílá karta, radius 20, `shadow-card`, padding 18. Vše operační. |
| `PrimaryButton` | orange pill, min-height 56, display 800 UPPER, `shadow-cta-glow`. Full-width. |
| `OutlineButton` | orange 2px border, transparent fill, orange text — „druhá priorita" akce. |
| `Overline` | uppercase tracked label (slate-500 default). |
| `OfflineBanner` | `warning-tint` bg, ⚡ (`zap`) ikona, „Offline režim — záznamy se uloží lokálně". |
| `StatChip` | slate-100 chip radius 14: velké display číslo + malý overline label. Do flex řady. |
| `KPRow` | bílá karta radius 20: 56px swatch (stav-barevný) + titul (body 700) + sub. Locked řádek dim 0.55. |
| `TabToggle` | bílý pill: „Před akcí / Na trase", aktivní = slate-800. |
| `BottomNav` | plovoucí bílý pill: Deník / Mapa / Profil (`book-text`/`map`/`user`), aktivní = slate-800; volitelný scan FAB. |
| `MarkBadge` | KČT turistická značka (modrá/zelená/žlutá/červená) + `vl` (orange kosočtverec) + `cyklo` (žlutá plaketa s číslem). |

## Ikonografie

- Knihovna **Lucide**, stroke 2px, rounded joins, 24px grid, `currentColor`.
- Velikosti: 24 v seznamech, 20 v chipech, 28 v kartách, 32 hero.
- Použité glyphy: `book-text` (Deník), `map` (Mapa), `user` (Profil), `download`, `scan`, `check` (splněno), `star` (cíl), `zap` (offline), `chevron-right`, `x`, `lock`.
- Sémantické unicode: `·` (separátor hodnot v chipech), `—` (důsledek v microcopy), `↑` (směr na „DALŠÍ KONTROLA").
- **Žádné emoji.**

## Obsah / tón (jen čeština)

- Oslovení **ty** (nikdy Vy). „Stáhni si trasy…", „Namiř na QR kontroly".
- Imperativ v CTA; akce + důsledek vedle sebe: „STÁHNOUT DATA AKCE · 84 MB".
- Metrické chipy s **desetinnou čárkou**: `2/5 KONTROL`, `14,1 KM UJITO`, `01:44 ČAS`. Vzdálenost <1 km v metrech (`310 m`), >1 km s čárkou (`14,1 km`). Čas elapsed `HH:MM`.
- Stavová slova jednoslovná, definitní, lowercase: *Zamčeno, Vypnuto, Zapnuto*.
- Overline UPPER pro labely (`STARTOVNÍ ČÍSLO`, `HOTOVO`, `ZBÝVÁ`), display UPPER pro místa (`TEPLICE N. METUJÍ`).
- Em-dash a middle-dot místo závorek. Bez „prosím", bez marketingu, „ještě" místo shame („Akce ještě není stažená").

## Substituce k potvrzení (z briefu)

1. **Fonty** Big Shoulders Display + Inter jsou substituce — potvrdit / dodat licencované `.ttf`/`.woff2`.
2. **Logo** `assets/ta33-monogram.svg` je placeholder z type systému, ne oficiální mark.
3. **Orange** `#F76A0E` naměřeno z banneru; pravá saturace možná `#FF6900` — ověřit brand sheet.
4. **Ikony** Lucide substituce (screenshoty žádné nedodaly).
