# TA33 - Mobilní aplikace
### Návrh řešení a rozsah prací

**Pro:** Teplicko-Adršpašská 33
**Datum:** 3. 7. 2026
**Cílová akce:** sobota 19. 9. 2026

---

## O co jde

Mobilní aplikace pro účastníky pochodu a běhu TA33. Účastník si před akcí do telefonu stáhne vše potřebné (trasa, kontroly, mapa), na trase pak sbírá kontroly a měří se mu čas, a v cíli se prokáže splněním. Vše je navržené tak, aby **fungovalo i tam, kde není signál**, což je na trase mezi skalami zásadní.

Aplikaci stavím ve dvou etapách. **Etapa 1** je samostatně funkční produkt pro letošní ročník (kontroly, čas, mapa, deník). **Etapa 2** aplikaci propojí s vaším rezervačním systémem (přihlášení, ověření platby, odbavení na prezentaci) a rozjede se, jakmile bude rezervační systém připravený.

---

## Etapa 1 - pro letošní ročník

Funkčně kompletní aplikace pro akci 19. 9. Letos poběží jako promo/zkušební verze bez přihlašování, účastník aplikaci používá anonymně a v cíli se prokáže „zelenou" obrazovkou o splnění. Přihlášení a napojení na rezervační systém přidám v Etapě 2.

### Offline základ
Aplikace je postavená tak, aby všechno podstatné fungovalo bez internetu. Sebrané kontroly i naměřené časy se ukládají přímo do telefonu a v cíli se výsledek prokáže ukázáním aplikace, kterou pořadatel potvrdí. Automatická synchronizace na server přichází až v Etapě 2. Součástí je i vzhled a struktura aplikace dle odsouhlaseného prototypu, připravená pro iOS i Android. *(FR-01, FR-02)*

### Mapa a trasa
Vlastní offline mapa přímo v aplikaci, na trase uvidíte podkladovou mapu, svoji aktuální GPS polohu, projitou stopu za sebou i vyznačenou trasu a kontroly, to vše bez potřeby signálu. Trasa (např. Trasa A · 33 km · 5 kontrol) se zobrazí i s detailem. Pro ty, kdo chtějí klasické navádění, je připraveno tlačítko pro otevření trasy v mapy.cz. *(FR-03, FR-05, FR-06, FR-07)*

### Kontroly a měření času
Kontroly se sbírají podle polohy: jakmile účastník dojde do prostoru kontroly (okruh cca 50 m), aplikace nabídne kontrolu sebrat a účastník ji odkliknutím uloží. Funguje offline a odpadá riziko ztráty nebo zneužití vytištěného QR kódu na kontrole. Sebrání potvrdí zelená obrazovka. Čas se měří přes QR kódy na startu a v cíli (prostor startu i cíle je hlídaný): naskenování startovního QR spustí stopky, cílového je zastaví, u jednotlivých kontrol se ukládají mezičasy podle jejich sebrání. Deník kontrol přehledně ukazuje, co je splněno, co je další na řadě a celkový postup (např. 2 z 5). V cíli deník slouží jako důkaz projití celé trasy. *(FR-04, FR-08, FR-09)*

### Příprava před akcí
Aplikace účastníka provede stažením celého offline balíčku (trasy, kontroly, mapa) s přehledem postupu a volbou, zda stahovat přes Wi-Fi nebo mobilní data. Po dokončení je aplikace ve stavu „připraveno offline" a účastník může vyrazit i tam, kde signál nebude. *(FR-11)*

### Přehled a nastavení
Třetí obrazovka aplikace shrnuje aktuální stav, aktivní trasu, postup a stav synchronizace, a obsahuje základní nastavení (notifikace, kontakt na pořadatele, časté dotazy). *(FR-10)*

---

## Etapa 2 - propojení s rezervačním systémem

Rozjede se po spuštění a doladění rezervačního systému (od 15. 7.). Přidává vše, co potřebuje znát konkrétního účastníka a jeho objednávku.

### Přihlášení a data účastníka
Přihlášení propojené s rezervačním systémem, po přihlášení aplikace načte profil účastníka, jeho startovní číslo, stav platby a objednávky. Tím se z anonymní verze stává plnohodnotný účet. *(FR-14, FR-15)*

### Platba a odbavení na prezentaci
Zobrazení stavu startovného i platební QR kód přímo v aplikaci. Účastník se na prezentaci prokáže odbavovacím QR kódem, který pořadatel naskenuje a účastníka rovnou odbaví. *(FR-12, FR-13)*

---

## Možná budoucí rozšíření

Nejsou součástí Etap 1 a 2, ale nabízejí se jako další vývoj podle potřeby:

- **Objednávky přímo v aplikaci**, možnost objednat přes aplikaci, navázané na rezervační systém. *(FR-16)*

---

## Rozsah a cena

Nabízím **fixní cenu za etapu** s jasně vymezeným rozsahem. Uvedené odhady zahrnují vývoj, testování (včetně terénního testu na reálné trase bez signálu) a nasazení do App Store a Google Play; reflektují můj AI-akcelerovaný způsob práce. Vycházím z hodinové sazby 600 Kč, ze které a z předpokládaného rozsahu je odvozená fixní cena za etapu.

| Etapa | Předpokládaný rozsah | Cena |
|---|--:|--:|
| **Etapa 1** (pro letošní ročník) | ~289 hodin | 173 400 Kč |
| **Etapa 2** (propojení s rezervačním systémem) | ~109 hodin | 65 400 Kč |

Cena za Etapu 2 je orientační a upřesní se po seznámení s rezervačním systémem, protože její rozsah závisí na jeho možnostech.

**Poznámka k odhadu a k AI.** Ve vývoji využívám nástroje s umělou inteligencí, které výrazně zrychlují psaní kódu a tvorbu obrazovek, to se v odhadu odráží. Řadu částí projektu ale AI nezrychlí prakticky vůbec: terénní testování na reálné 33km trase bez signálu, ladění GPS a spotřeby baterie na skutečných telefonech, schvalovací proces v obou obchodech i budoucí napojení na rezervační systém. Tyto části vyžadují reálný čas bez ohledu na nástroje, odhad s tím počítá.

**Proč je mobilní vývoj náročnější, než se zdá.** Na rozdíl od webu musí aplikace fungovat na široké škále telefonů iOS i Android a napříč různými verzemi operačních systémů (Android je kvůli množství výrobců typicky ještě členitější). U tohoto projektu se přidávají specifika: přesnost GPS se liší telefon od telefonu a ve skalních kaňonech Adršpachu/Teplic bývá zhoršená odrazy signálu; několikahodinový záznam trasy na 33 km musí odolat úsporným režimům baterie a omezením běhu na pozadí (které u Androidu umí měření přerušit); sken kontrol přes kameru musí zvládnout různý hardware i světlo; a schvalování v obou obchodech (zdůvodnění přístupu k poloze, prohlášení o soukromí) přináší čas navíc. Většina těchto věcí se nedá odsimulovat, ověřují se na reálných zařízeních a přímo v terénu na trase. Právě proto tvoří značnou část odhadu testování a vyladění.

---

## Co je a není součástí

Aby byla dohoda na fixní cenu férová pro obě strany, shrnuji klíčové předpoklady:

**Součástí je:**
- Aplikace pro iOS i Android.
- Offline funkčnost jádra (kontroly, čas, deník, mapa) dle popisu výše.
- Testování a nasazení do obou obchodů.

**Součástí není (řeší pořadatel nebo jde o samostatnou dohodu):**
- Vývojářské účty do obchodů (Apple Developer a Google Play), zřizuje a vlastní pořadatel na svoje jméno; roční/jednorázové poplatky obchodů jsou provozním nákladem pořadatele. Aplikace se publikuje pod účtem pořadatele.
- Úpravy rezervačního systému na jeho straně (řeší jeho autor); v Etapě 2 navrhnu, co je potřeba doplnit pro vzájemné propojení.
- Logo, organizační data, texty a mapové podklady dodá pořadatel.
- Změny nad rámec odsouhlaseného rozsahu (řeší se jako vícepráce dle dohody).

---

## Příloha - přehled funkčních požadavků (FR)

Reference k jednotlivým funkcím zmíněným v textu.

**Etapa 1**

- **FR-01 - Kostra aplikace:** základní struktura a vzhled aplikace dle prototypu, navigace, příprava pro iOS i Android.
- **FR-02 - Offline funkčnost:** ukládání dat (kontroly, časy, deník) do telefonu, funguje bez signálu. V Etapě 1 se výsledek prokáže ukázáním aplikace v cíli; automatická synchronizace na server je součástí Etapy 2.
- **FR-03 - Trasa:** zobrazení a detail vybrané trasy (např. Trasa A · 33 km · 5 kontrol).
- **FR-04 - Deník kontrol:** přehled kontrol se stavy a celkovým postupem; v cíli slouží jako důkaz splnění.
- **FR-05 - Poloha na mapě:** živá GPS poloha účastníka a projitá stopa (breadcrumb).
- **FR-06 - Offline mapa:** vlastní mapový podklad v telefonu, zobrazitelný bez signálu.
- **FR-07 - Odkaz na mapy.cz:** tlačítko pro otevření trasy v navigaci mapy.cz.
- **FR-08 - Sběr kontrol přes GPS:** kontrola se sebere po příchodu do jejího prostoru (okruh cca 50 m) potvrzením v aplikaci; funguje offline, s potvrzovací obrazovkou.
- **FR-09 - Měření času:** start a cíl přes QR kód (hlídaný prostor), mezičasy u jednotlivých kontrol podle jejich sebrání.
- **FR-10 - Přehled a nastavení:** shrnutí aktuálního stavu (trasa, postup, synchronizace) a základní nastavení.
- **FR-11 - Příprava před akcí:** stažení offline balíčku s přehledem postupu a volbou Wi-Fi / mobilní data.

**Etapa 2**

- **FR-12 - Platba startovného:** stav platby a platební QR kód v aplikaci.
- **FR-13 - Odbavovací QR:** QR kód pro prokázání a odbavení na prezentaci.
- **FR-14 - Analýza a návrh propojení:** analýza rezervačního systému a návrh datového propojení s aplikací.
- **FR-15 - Přihlášení a data účastníka:** přihlášení přes rezervační systém a načtení profilu, stavu platby a objednávek.

**Volitelné / budoucí**

- **FR-16 - Objednávky v aplikaci:** objednání přes aplikaci s napojením na rezervační systém.

---

*Rád cokoli z návrhu upravím. Nejlepší bude si nad tím zavolat a projít interaktivní prototyp.*
