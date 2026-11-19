> **Superseded by [PLAN.md](PLAN.md)** (SafeRoute AI v1.0). This file is kept as the history of Phase 0.

# SafeRoute AI: Improvement Plan v2

> **Goal:** Turn the current demo into a working prototype. Emergency contacts should receive **real messages**, the map should be **live** (GPS, real roads, real nearby places), and the safety score should come from **what is actually on the route** instead of being estimated from distance alone.

---

## Progress

### ✅ Phase 0 done (18 Nov 2026)

**Fixed bugs** (numbers refer to §2):
- **#2 Biased fallback routes.** The hard-coded bonuses are gone. When routing is down, the app returns one labelled straight-line estimate.
- **#4 Silent wrong location.** Unknown places now return a clear error (HTTP 422).
- **#6 Seed data.** Seed SQL now works on both H2 and MySQL and can safely run on every start. It no longer adds fake contacts, and errors are no longer hidden.
- **#7 Stale model.** The model is fingerprinted and retrains automatically when the CSV changes.
- **#8 Nominatim policy.** Autocomplete now uses Photon. Nominatim is throttled to 1 request per second and its results are cached.
- **#10 Contacts sync.** The server is the single source of truth. The browser keeps a read-only cache, and contacts from the old local-only storage can be imported.
- **#11 Risk zones.** Zones have realistic radii and are drawn at their real size. Zone checks now run on the server along the whole route line.
- **#12 Labels.** A `RiskLevel` enum gives one consistent set of labels.
- **#13 Flaky test.** Tests run offline with mocks: 48 tests.
- **#14 Leaking errors.** Server errors no longer expose internal details, and every error has the same JSON shape.
- **#15 README.** Rewritten.

**Partial progress:**
- **#1 Scoring ignores the route.** Scores now depend only on real route facts: length, hour, and the zones the route actually passes through. Full OpenStreetMap features come in Phase 3.
- **#5 SOS sends nothing.** The SOS dialog now offers a real call to 112 and the women helplines (1091 / 181), and can text all contacts the user's location from the phone. Automatic server-sent alerts come in Phase 1.

**Other changes:**
- The frontend was rewritten as ES modules, with XSS-safe templating.
- The planner layout is map-first, with a "why this score" panel, a comparison sentence, help points near the route and recent searches.
- The Safe places and Risk zones pages now have maps.
- Phone numbers are stored in E.164 format, ready for SMS.
- There is exactly one primary contact, with a limit of 5 contacts.
- H2 now uses a file database, so data survives restarts.
- `CurrentUserService` is the single place to plug in login (Phase 5).

**Next: Phase 1, real alerts.**

---

## 1. What exists today (audit summary)

**Stack:** Spring Boot 3.3 (Java 17), Spring Data JPA, H2 (MySQL optional), Weka Random Forest, vanilla JS + Bootstrap + Leaflet/OpenStreetMap.

| Area | What it does now | Status |
|---|---|---|
| Route search | Nominatim geocoding, OSRM routes (`alternatives=true`), saves search history | ✅ Live OSRM works (tested: 2 real road routes Bengaluru) |
| Risk model | Weka RandomForest (100 trees) trained on `safety_risk_training.csv` | ⚠️ Only **14 training rows** |
| Safety score | `100 × (1 − weighted risk probability)` per route | ❌ Inputs are invented from distance + hour (see §2) |
| Risk zones | 3 hard-coded zones near Bengaluru centre, drawn as fixed-size dots | ⚠️ Demo only |
| Safe places | `SafeLocationService` returns **4 hard-coded sample points** | ❌ Same Bengaluru points for any city |
| Emergency contacts | CRUD API + page, with localStorage fallback | ⚠️ Works, but has sync bugs |
| SOS | Shows a red text box saying "Demo SOS activated" | ❌ **No message is sent** |
| Location | Typed text only | ❌ No GPS, no "use my location", no live tracking |
| Auth | `userId = 1` hard-coded everywhere | ❌ No login |
| Tests | 5 tests pass; one depends on live internet | ⚠️ Flaky |

---

## 2. Problems found (verified by running the app)

I ran the built jar and called the APIs. These are the issues, with the most important first.

### 🔴 Critical: these undermine the core idea

1. **The safety score does not look at the route.**
   `RouteRecommendationService.buildRiskRequest()` derives every ML feature from **distance and hour only**: lighting = `isNight ? 2 : 4`, police distance = `distanceKm / 4`, and so on. Two real OSRM routes at 21:30 scored **61 vs 58**, which only reflects that one is shorter. The **shorter route nearly always wins**, so the app behaves like ordinary navigation. Risk zones are **never used** in server-side scoring.
2. **Fallback routes have hard-coded winners.** `buildFallbackRoutes()` adds `+9` to the "Main-road route" and `-5` to the "Direct route", so the demo always recommends the same option.
3. **Safe places are fake for every location.** Calling `/api/safe-locations?latitude=28.61&longitude=77.21` (Delhi) returned the Bengaluru sample police station.
4. **Unknown places silently become Bengaluru.** Searching `zzqqxx_nowhere` returns coordinates `12.9716, 77.5946` with no error, so a typo produces a wrong route without any warning.
5. **SOS sends nothing.** `triggerSosDemo()` only changes text on the page.

### 🟠 Major

6. **Seed data does not load.** The "Mother" and "Friend" contacts in `data.sql` are missing after startup. `spring.sql.init.continue-on-error=true` hides the error. The likely cause is MySQL-only syntax (`ON DUPLICATE KEY UPDATE … VALUES()`) running against H2. Set it to `false` once to see the actual error.
7. **The ML model never retrains.** The model is cached at `target/ml/random_forest_model.model`. Editing the CSV has no effect until someone deletes that file. 14 rows is also too few to train a meaningful Random Forest.
8. **Nominatim usage-policy violation.** Search-as-you-type autocomplete is **not allowed** on the public Nominatim server, and the User-Agent contains a fake email. The server may start blocking requests (HTTP 403), which would send the app into offline fallback.
9. **The driving profile only.** The public OSRM demo server routes cars only. 7.5 km in 12 min is a car ETA, but the target users often walk or take autos.
10. **Contacts get out of sync.** `contacts.js` only overwrites local storage when the server list is non-empty, so contacts deleted on the server reappear. Locally saved contacts have no `id` and never sync. Contact logic is also duplicated between `app.js` and `contacts.js`.

### 🟡 Minor

11. Risk-zone check: zones have a 1–1.3 km radius, so nearly every central route gets a "Risk zone ahead" warning. Zones are drawn as fixed 12 px dots instead of their real radius.
12. Labels are inconsistent: CSV uses `Medium Risk`, while `data.sql` and the README use `Moderate Risk`.
13. `RouteControllerTest.searchLocations_returnsRealGeocodingResults` needs internet access and is flaky.
14. `GlobalExceptionHandler` sends internal exception messages to the client.
15. The README contains another machine's path (`c:/Users/kisho/...`).

---

## 3. The improved vision

> **SafeRoute AI v2: a preventive safety companion.**
> **Before the trip:** choose the safest practical route, scored segment by segment from real map data.
> **During the trip:** live GPS tracking, warnings before risky stretches, and automatic check-ins.
> **If something goes wrong:** one tap, or no tap at all, sends real alerts with a live location link to trusted contacts.

The project already argues that prevention is better than reacting after the fact. v2 keeps that argument and adds **automatic escalation**: if the user stops responding, the system alerts contacts itself. Most student projects stop at a manual SOS button, so this sets the project apart.

### Feature map

| # | Feature | Priority | Why it matters |
|---|---|---|---|
| F1 | **Real emergency alerts** (Telegram + SMS + Email) with a live-location link | P0 | Requested |
| F2 | **Working live map**: GPS, click-to-pick, road-following routes, real safe places | P0 | Requested |
| F3 | **Segment-level safety scoring** from real OpenStreetMap data | P0 | Makes the "AI" real |
| F4 | **Safe Trip mode**: live tracking, route-deviation detection, check-ins, auto-alert | P1 | Biggest differentiator |
| F5 | **"Risk zone ahead" live warnings** with voice and vibration, plus nearest safe place | P1 | In the original problem statement |
| F6 | Login / register (Spring Security + BCrypt) | P1 | In the original plan; needed for per-user contacts |
| F7 | **Community incident reports** that feed the model | P1 | Gives the model real input data over time |
| F8 | Model evaluation page (RF vs Decision Tree vs Logistic Regression) | P1 | Strong material for the viva/presentation |
| F9 | "Best time to leave" and "Safety vs time" trade-off | P2 | Nice to have, cheap to build |
| F10 | Fake incoming call, shake-to-SOS, PWA install | P2 | Optional extras |

---

## 4. F1: Real-time emergency alerts (detailed design)

### 4.1 Channel choice

| Channel | Cost | Real-time | Setup effort | Notes |
|---|---|---|---|---|
| **Telegram Bot** ⭐ | Free | Instant | 10 min | **Recommended primary channel.** Supports **native live location** (`sendLocation` with `live_period`, updated via `editMessageLiveLocation`). The contact must press **Start** on the bot once. |
| **SMS via Twilio** | Free trial credit | Seconds | 20 min | Trial accounts can only text **verified** numbers, and messages are prefixed with "Sent from your Twilio trial account". This is fine for a demo. Delivery to Indian numbers without DLT registration can be unreliable, so treat SMS as best-effort. |
| **Email (Gmail SMTP)** | Free | ~Seconds | 10 min | Needs a Gmail **App Password** (2FA must be on). Useful as a reliable fallback. |
| **WhatsApp (Twilio Sandbox)** | Free sandbox | Seconds | 15 min | Each recipient must first send `join <code>` to the sandbox number. Optional. |
| **`sms:` link on the phone** | Free | Uses the phone | 0 | **Offline fallback.** If the server is unreachable, open the phone's SMS app pre-filled with all contacts and the message. Works with no internet data. |
| **`tel:112`** | Free | Instant | 0 | One-tap call to India's ERSS emergency number 112. Also show 1091 / 181 women helplines. |

**Recommendation:** Telegram as primary, SMS and Email in parallel, and the `sms:` link as the offline last resort. Send through **all available channels at once** rather than one after another. In an emergency, a duplicate message is better than a missing one.

### 4.2 How a contact is linked (consent built in)
1. The user adds a contact (name, phone, optional email).
2. The app generates an invite link `https://t.me/<YourBot>?start=<inviteToken>` and a QR code, which the user sends to the contact.
3. When the contact presses Start, the bot webhook receives `/start <inviteToken>`. The server stores that contact's `telegram_chat_id` and marks them `CONFIRMED`.
4. The bot replies: *"You are now an emergency contact for Priya on SafeRoute AI. You'll only be messaged in an emergency or when she shares a trip."*

This also handles **consent**: nobody receives alerts they didn't agree to. It's a good point to raise in the ethics section of the viva.

### 4.3 SOS flow
```
[SOS button / shake / missed check-in]
        │
        ▼
 5-second countdown with big CANCEL button  (prevents false alarms)
        │
        ▼
 POST /api/alerts/sos {lat, lon, accuracy, battery, tripId?}
        │
        ▼
 AlertService
   ├─ create Alert row + public tracking token
   ├─ for each CONFIRMED contact, in parallel (@Async):
   │     TelegramChannel → text + live location pin
   │     SmsChannel      → text with maps link + tracking link
   │     EmailChannel    → text + static map link
   └─ record AlertDelivery rows (SENT / FAILED + error)
        │
        ▼
 UI shows per-contact delivery status:  ✓ Mom (Telegram, SMS)  ✗ Riya (SMS failed)
 Phone keeps posting GPS every 5 s → contacts' tracking page updates live
        │
        ▼
 "I'm safe now" button → sends all-clear to everyone, closes tracking link
```

**Message template:**
```
🚨 SOS from Priya (SafeRoute AI)
Time: 21:42, 18 Nov
Location: https://maps.google.com/?q=12.97151,77.59452 (±12 m)
Live tracking: https://<your-host>/track/8f3k2a
Battery: 23%
Last route: College → Home (planned arrival 21:55)
Nearest police: Cubbon Park PS, 650 m
If you can't reach her, call 112.
```

### 4.4 Live tracking page for contacts
- `GET /track/{token}` is a public page (no login) showing a Leaflet map with the user's live marker, the planned route, a trail, and "last updated 4 s ago".
- Updates arrive via **Server-Sent Events** (`SseEmitter`), which is simpler than WebSockets and one-way is enough here.
- The token is random (≥128-bit), **expires** when the trip ends or 2 h after the alert, and can be revoked.

### 4.5 Backend classes
```
alert/
  AlertChannel.java            (interface: String name(); DeliveryResult send(Contact, AlertMessage))
  TelegramChannel.java         (RestTemplate → api.telegram.org/bot<TOKEN>/sendMessage, sendLocation)
  TwilioSmsChannel.java        (Twilio Java SDK or plain REST)
  EmailChannel.java            (spring-boot-starter-mail)
  AlertService.java            (fan-out, @Async, retries x2, persists deliveries)
  TelegramWebhookController.java (POST /api/telegram/webhook → handles /start <token>)
  TrackingController.java      (GET /track/{token}, GET /api/track/{token}/stream SSE)
```
Each channel is enabled only when its config is present, so the app still runs with zero keys and falls back to "demo mode" with a clear banner.

### 4.6 Configuration (never commit secrets)
```properties
# application.properties: values come from environment variables
saferoute.public-base-url=${PUBLIC_BASE_URL:http://localhost:8080}
saferoute.telegram.bot-token=${TELEGRAM_BOT_TOKEN:}
saferoute.telegram.bot-username=${TELEGRAM_BOT_USERNAME:}
saferoute.twilio.account-sid=${TWILIO_ACCOUNT_SID:}
saferoute.twilio.auth-token=${TWILIO_AUTH_TOKEN:}
saferoute.twilio.from-number=${TWILIO_FROM_NUMBER:}
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_APP_PASSWORD:}
spring.mail.properties.mail.smtp.starttls.enable=true
```
Add a `.env.example` listing these, and add `.env` to `.gitignore`.

**Telegram webhook needs a public HTTPS URL.** For a local demo, run `cloudflared tunnel --url http://localhost:8080` (or `ngrok http 8080`), then call `setWebhook` with that URL. Alternatively, use **long polling** (`getUpdates` on a `@Scheduled` task), which needs no public URL. Long polling is the simpler option for a classroom demo.

---

## 5. F2: A working live map

| Capability | How |
|---|---|
| Current location | `navigator.geolocation.getCurrentPosition` behind a "📍 Use my location" button, followed by reverse geocoding for a readable name |
| Pick on map | Click to set the start, then the destination. **Draggable markers** re-route on drop |
| Road-following routes for walking | **FOSSGIS OSRM** profiles: `https://routing.openstreetmap.de/routed-foot/route/v1/driving/{lon,lat;lon,lat}` (walking) and `routed-car` (cab/auto). Add a **mode selector: 🚶 Walk / 🛺 Auto-Cab** |
| Autocomplete | Switch to **Photon** (`https://photon.komoot.io/api/?q=...&lat=..&lon=..`), which is built for search-as-you-type. Bias results to the user's location. Keep Nominatim only for the final "search" button and reverse geocoding (≤1 req/s, real contact in User-Agent) |
| Risk-coloured routes | Draw each route as **segments coloured green / amber / red** by segment risk (F3). This makes the result clear at a glance |
| Real safe places | **Overpass API** query around the route (see §6.2): police, hospitals, clinics, pharmacies, fuel stations, 24×7 shops, metro/bus stops. One icon per type. Each popup has "Navigate here" and "Call" buttons |
| Risk zones | `L.circle` with its **real radius in metres**, plus a heat layer of community reports (`leaflet.heat`) |
| Live position | `watchPosition` → blue dot with an accuracy circle, map follows the user (toggleable) |
| Next turn (optional) | OSRM `steps=true` → banner with the next instruction, e.g. "In 200 m turn left onto MG Road" |
| Errors | Unknown place → **clear error message**, never a silent Bengaluru pin. Straight-line routes are only allowed in labelled offline demo mode |

> ⚠️ **Geolocation only works on HTTPS or `localhost`.** To test on a phone, use the same `cloudflared`/`ngrok` HTTPS tunnel from §4.6.

---

## 6. F3: Real segment-level safety scoring (the AI part)

### 6.1 Pipeline
```
OSRM route geometry (hundreds of points)
   │  resample every ~150 m
   ▼
Segments S1..Sn ── for each segment centre:
   │   features from cached OSM data + community reports + time of day
   ▼
Weka model → P(High), P(Medium), P(Low) per segment
   │
   ▼
Route score = 100 × (1 − (0.6 × length-weighted mean risk + 0.4 × 90th-percentile risk))
   │           (so one very dark stretch can't be averaged away)
   ▼
Explanation: top 3 factors + "weakest stretch: 400 m near X (poor lighting, no open shops)"
```

### 6.2 Features (all from real, free data)

| Feature | Source | Overpass tag / logic |
|---|---|---|
| `hour`, `is_night`, `is_weekend` | Requested travel time | — |
| `lighting` (1–5) | OSM | Ways with `lit=yes/no`, nodes with `highway=street_lamp` within 50 m; main roads assumed better lit at night |
| `road_class` (1–5) | OSM / OSRM | `primary/secondary/trunk` = busy, `residential` = medium, `service/track/footway/path` = isolated |
| `poi_density` (crowd proxy) | OSM | Count of `shop=*`, `amenity=restaurant/cafe/fuel/atm` within 100 m. Bonus if `opening_hours` is `24/7` or covers the travel hour |
| `transit_nearby` | OSM | `highway=bus_stop`, `railway=station`, `station=subway` within 200 m |
| `police_km`, `hospital_km` | OSM | Nearest `amenity=police`, `amenity=hospital/clinic` |
| `incidents_90d` | **Community reports table** (F7) | Reports within 200 m, time-decayed, weighted by severity |
| `in_risk_zone` | `safety_zones` table | Segment inside an admin-defined zone |

**Caching (important):** Overpass is a shared free service, so don't query it once per segment. Fetch **one bounding-box query per route request** (all POIs at once) and cache results in a `geo_feature_cache` table keyed by geohash (precision 6, about 1.2 km cells) with a 7-day TTL. Run the per-segment calculations in memory.

Example Overpass query (one call covers the whole route bbox):
```
[out:json][timeout:25];
(
  node["amenity"~"police|hospital|clinic|pharmacy|fuel"]({{bbox}});
  node["highway"~"street_lamp|bus_stop"]({{bbox}});
  node["shop"]({{bbox}});
  way["highway"]["lit"]({{bbox}});
);
out center tags;
```

### 6.3 Better model training (honest and presentable)
- **Training data:** write `TrainingDataGenerator.java` (or a small Python script) that produces about **3,000 synthetic rows** from a documented rule set plus random noise and ~5 % label flips. Example rule: night + lit ≤ 2 + poi ≤ 1 + isolated road → mostly High. Be open in the report that the **data is synthetic and the rules are derived from published safety-audit criteria** (e.g. the Safetipin audit parameters: lighting, openness, visibility, crowd, security, walk path, public transport, gender diversity, feeling).
- **Compare three models**, as the problem statement asks: Weka `RandomForest`, `J48` (decision tree), `Logistic`. Use 10-fold cross-validation and report accuracy, macro-F1, a confusion matrix and **feature importance**.
- `GET /api/model/metrics` serves these numbers, and a **"How the AI works"** page shows them as charts. Present this page during the viva.
- **Model versioning:** store a SHA-256 of the CSV next to the `.model` file and retrain automatically when it changes. This fixes bug #7.
- **Continuous learning (stretch):** confirmed community reports become new labelled rows. Retrain nightly with `@Scheduled`.

### 6.4 Recommendation logic
Don't simply choose the highest score. Show the trade-off:
> 🟢 **Recommended: Route B** · Safety 82 · 4.6 km · 18 min
> *3 min longer than the fastest route, but 27 points safer. Avoids a 600 m unlit stretch on 5th Cross.*

Rule: recommend the safest route **unless** it's more than 40 % longer **and** less than 5 points safer. In that case, recommend the fastest and mention the alternative.

**Safe-detour generation:** OSRM often returns only 1–2 alternatives. To get more, take the riskiest segment, find a nearby main road, police station or 24×7 place, and request a route **via that point**. This produces options like "Via MG Road (main road)" with a real reason attached.

---

## 7. F4 + F5: Safe Trip mode (the strongest differentiator)

Press **"Start Safe Trip"** on the chosen route:

| Trigger | Detection | Response |
|---|---|---|
| Trip start | — | Contacts get: *"Priya started a trip College → Home, arriving ~21:55. Track: link"* (user can turn this off) |
| **Approaching a risky segment** | GPS within 250 m of a red segment | 🔊 Voice alert (`speechSynthesis`) + vibration + banner: *"High-risk stretch ahead (unlit, 400 m). Nearest safe place: Apollo Pharmacy 180 m. [Reroute] [Continue]"* |
| **Route deviation** | > 150 m off route for > 60 s | *"You've left your route. Are you OK?"* No answer within 30 s → auto-alert contacts |
| **Not moving** | Speed ≈ 0 for > 4 min outside a safe place | Same check-in, then auto-alert |
| **Late arrival** | Now > ETA + 15 min | Same check-in, then auto-alert |
| Phone goes silent | No GPS ping for > 3 min (server-side `@Scheduled` check) | Server alerts contacts with the last known location. This works **even if the phone is switched off or snatched** |
| Arrival | Within 75 m of destination | Contacts get *"Priya reached home safely ✅"*, tracking link expires |

Every check-in prompt needs a **PIN or a "slide to confirm"** response, not a single tap, so it can't be dismissed by accident. A **duress PIN** (e.g. reversed PIN) can look like "I'm OK" on screen while sending a silent alert. This is a well-known safety-app pattern, and examiners tend to notice it.

**Simulation mode for the demo:** you can't walk 4 km during the viva, so add a "▶ Simulate trip" button that plays the route geometry as fake GPS (×10 speed) through the same code path. During the demo you can show the risk warning, then a deviation, then the auto-alert arriving on a real phone, all within about 2 minutes.

---

## 8. F6–F10: Remaining features

**F6 Auth:** Spring Security, register/login, BCrypt (the `password_hash` column already exists), session cookie or JWT. All `userId=1` usages become `@AuthenticationPrincipal`. Each user sees only their own contacts, trips and alerts.

**F7 Community reports:** long-press on the map, choose a category (poor lighting / harassment / isolated / unsafe crowd / broken streetlight / **safe place, open late**), set severity, add an optional note. Reports are time-decayed (half-life about 60 days), rate-limited per user, and an admin can hide abusive ones. They feed `incidents_90d` (§6.2) and the heat layer.

**F8 Model page:** see §6.3.

**F9 Best time to leave:** score the same route for the next 4 hours in 30-min steps and show a small chart: *"Leaving at 20:30 instead of 22:00 raises safety from 58 → 77."*

**F10 Optional extras:**
- **Fake incoming call:** a realistic ringing screen with a configurable caller name, to help leave an uncomfortable situation. Easy to build and popular in demos.
- **Shake-to-SOS:** `DeviceMotionEvent`, three strong shakes start the SOS countdown.
- **PWA:** `manifest.json` + service worker so the app is installable on the home screen and the shell and SOS `sms:` fallback work offline.
- **Battery-aware mode:** when battery < 15 %, the app automatically sends contacts the last location and planned route.
- **Audio evidence (careful):** record 30 s of audio on SOS and attach it for contacts. Mention privacy/consent if you add this.

---

## 9. Target architecture

```
┌──────────────────────── Browser / Phone (PWA) ─────────────────────────┐
│ Leaflet map · GPS watchPosition · Trip engine · Voice alerts · SOS UI  │
└──────────────┬───────────────────────────────────────▲─────────────────┘
               │ REST (JSON)                           │ SSE (live updates)
┌──────────────▼───────────────────────────────────────┴─────────────────┐
│ Spring Boot                                                            │
│  security/   auth, BCrypt, sessions                                    │
│  routing/    OsrmClient (foot/car), PhotonClient, NominatimClient      │
│  geo/        OverpassClient + GeoFeatureCache, segmenter               │
│  scoring/    SegmentFeatureExtractor → RiskModel (Weka) → RouteScorer  │
│  trip/       TripService, deviation/inactivity watchdog (@Scheduled)   │
│  alert/      AlertService → Telegram / Twilio SMS / Email channels     │
│  report/     community incident reports                                │
│  tracking/   public /track/{token} + SseEmitter hub                    │
└───────┬───────────────┬──────────────────┬─────────────────┬───────────┘
        │               │                  │                 │
     H2/MySQL      OSRM (FOSSGIS)     Overpass / Photon   Telegram / Twilio / SMTP
```

### New/changed tables
```sql
users                 -- existing; now actually used for login
emergency_contacts    -- + email, telegram_chat_id, invite_token, status (PENDING/CONFIRMED), channels
trips                 -- id, user_id, mode, route_geometry (JSON), start_at, eta, status, share_token, share_expires_at
trip_locations        -- trip_id, lat, lon, accuracy_m, speed, battery, recorded_at
alerts                -- id, user_id, trip_id, type (SOS/DEVIATION/INACTIVE/LATE/SILENT/ARRIVED/ALL_CLEAR), lat, lon, created_at, resolved_at
alert_deliveries      -- alert_id, contact_id, channel, status (SENT/FAILED), provider_message_id, error, sent_at
incident_reports      -- user_id, lat, lon, category, severity, note, occurred_at, status, created_at
geo_feature_cache     -- geohash, payload (JSON), fetched_at
```
Fix `data.sql`: use plain `INSERT` (or `MERGE INTO … KEY(id)` for H2), drop the explicit IDs, and set `continue-on-error=false`.

### New/changed endpoints
```
POST /api/auth/register | /api/auth/login | /api/auth/logout | GET /api/auth/me
GET  /api/geocode/autocomplete?q=&lat=&lon=     (Photon)
GET  /api/geocode/reverse?lat=&lon=
POST /api/routes/recommend                       (+ mode: WALK|CAR; returns segments[] with risk)
GET  /api/routes/best-time?...                   (F9)
GET  /api/safe-locations?lat=&lon=&radius=       (real Overpass data)
GET  /api/safe-locations/along-route  (POST geometry)
POST /api/contacts/{id}/invite                   → Telegram link + QR
POST /api/telegram/webhook                       (or long-polling worker)
POST /api/trips                → start trip      POST /api/trips/{id}/location  (GPS ping)
POST /api/trips/{id}/checkin   POST /api/trips/{id}/end
POST /api/alerts/sos           POST /api/alerts/{id}/all-clear   GET /api/alerts/{id}
GET  /track/{token}            GET /api/track/{token}/stream (SSE)
POST /api/reports              GET /api/reports?bbox=
GET  /api/model/metrics
```

---

## 10. Implementation schedule

The work is ordered by priority. **If you only have 5 days, do Phases 0–3 and the simulation part of Phase 4.** That covers everything you asked for, plus a strong demo.

| Phase | Duration | Deliverables | Done when… |
|---|---|---|---|
| **0. Fix the base** | 0.5 day | Bugs #2, #4, #6, #7, #10–15 from §2. Unify contacts JS into one module. Make the internet-dependent test use a mock | `mvn test` green offline; seed data loads; a typo shows an error |
| **1. Real alerts (F1)** | 1.5 days | Telegram bot (long polling) + contact invite flow, Twilio SMS, Gmail email, `AlertService` fan-out, delivery status UI, 5 s countdown, `sms:` + `tel:112` fallback, all-clear | Pressing SOS makes a **real phone** buzz within 5 s with a location link |
| **2. Live map (F2)** | 1.5 days | GPS "use my location", click/drag pins, walk/car modes via FOSSGIS OSRM, Photon autocomplete, real safe places via Overpass, real-radius zones | Works in any Indian city; on a phone through an HTTPS tunnel |
| **3. Real scoring (F3, F8)** | 2 days | Segmenter, Overpass feature extraction + cache, 3,000-row generator, RF/J48/Logistic comparison, segment-coloured routes, "why" explanation, metrics page | Two routes in the same area at night get **meaningfully different** scores with reasons tied to real places |
| **4. Safe Trip (F4, F5)** | 1.5 days | Trip start/end, GPS pings, live tracking page (SSE), risk-ahead voice warnings, deviation/inactivity/late/silent watchdogs, arrival message, **simulation mode** | The simulated trip triggers a warning, then a deviation, then a real Telegram alert with a live map |
| **5. Auth + reports (F6, F7)** | 1 day | Login/register, per-user data, community reports + heat layer + admin hide | Two accounts see separate contacts |
| **6. Polish + presentation** | 1 day | F9 best-time chart, PWA manifest, fake call (optional), README rewrite, screenshots, demo rehearsal | Rehearsed 5-minute demo |

**Total: about 9 working days** for everything, or 5 days for the core.

---

## 11. Demo script (5 minutes)

1. **Login**, then show the emergency contacts list. One contact is a classmate's phone **linked to Telegram**, shown as ✅ Confirmed.
2. Press **"Use my location"**, pick a destination by clicking the map, set the time to **21:30** and the mode to **Walk**.
3. Show three routes coloured by segment. Point out: *"The fastest route has a red 400 m unlit stretch, so we recommend Route B: 3 min longer, 27 points safer."* Open the "why" panel.
4. Change the time to **10:00** and show how the scores change. Open the **best-time** chart.
5. Press **Start Safe Trip → Simulate**. The classmate's phone gets *"Trip started + tracking link"*. Open the link on the projector: the dot is moving.
6. The simulated walker approaches a red segment. A **voice warning** plays, and the nearest safe place is suggested.
7. Simulate a **deviation** and ignore the check-in. After 30 s, the **auto-alert arrives on the classmate's phone with live location.** This is the high point of the demo.
8. Press **SOS** on screen. Show the countdown, then delivery ticks ✓ Telegram ✓ SMS ✓ Email. Then press **"I'm safe"** to send the all-clear.
9. Open the **"How the AI works"** page: model comparison, confusion matrix, feature importance.
10. Close with limitations (synthetic labels, OSM coverage gaps) and future scope.

---

## 12. Ethics, privacy and limitations (include in the report)

- **Not a guarantee of safety:** scores are estimates. Keep the disclaimer on screen.
- **Avoid stigmatising areas:** show *reasons* (e.g. lighting, isolation) rather than "dangerous neighbourhood" labels. Reports decay over time and can be moderated.
- **Consent:** contacts opt in through the Telegram link. Tracking links expire, and the user can stop sharing at any time.
- **Data minimisation:** delete GPS trails 7 days after a trip unless an alert occurred.
- **Secrets:** API keys live in environment variables only, never in git.
- **OSM coverage:** lighting tags are sparse in many Indian areas, so fall back to road class as a proxy. Say so openly.
- **Fair use of free services:** cache Overpass results, rate-limit Nominatim to 1 req/s, and use a real contact address in the User-Agent.

## 13. Future scope (for the final slide)
- Train on real, labelled data (e.g. partnering with city police open data or Safetipin-style audits).
- Native Android app with background location and a hardware-button SOS.
- Integration with the ERSS-112 / state police apps.
- Crowd "walk-together" matching for people on similar routes at night.
- Street-view image analysis (CNN) to estimate lighting and openness automatically.

---

## 14. Setup checklist (accounts and keys to create)

- [ ] **Telegram:** message `@BotFather` → `/newbot` → copy the token into `TELEGRAM_BOT_TOKEN`
- [ ] **Twilio:** sign up for a trial → get a number → **verify each demo recipient's phone** → SID / auth token / from-number
- [ ] **Gmail:** turn on 2-Step Verification → create an App Password → `MAIL_USERNAME`, `MAIL_APP_PASSWORD`
- [ ] **HTTPS tunnel:** install `cloudflared` (or ngrok) to test GPS on a phone and to use the Telegram webhook (if not using long polling)
- [ ] Create `.env.example`; add `.env` to `.gitignore`
- [ ] Ask 2 classmates to act as emergency contacts and press Start on the bot **before the demo day**
