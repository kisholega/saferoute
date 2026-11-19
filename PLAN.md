# SafeRoute AI v1.0: Product and Implementation Plan

> **Status (18 Nov 2026): implemented.** Every feature in §3 is built and verified:
> - **78 automated tests** pass.
> - **38 end-to-end checks** pass against the live services, including push alerts read back from ntfy.
> - A **headless browser walkthrough** of every screen showed no JavaScript errors.
>
> Changes made during implementation:
> - **City snapshot.** The public Overpass servers were at capacity (HTTP 502 and 90-second timeouts), so
>   Bengaluru street-safety data is bundled as an OpenStreetMap snapshot. Route scoring dropped from about
>   60 s to about 2 s and works offline. Live Overpass is still used outside the city.
> - **Map tiles.** CARTO tiles now require an API key, so the map uses OpenStreetMap tiles with a soft colour filter.
> - **Demo zones are opt-in** (`DEMO_ZONES=true`), so synthetic data never changes real route scores.
>
> **v1.1 additions:**
> - Dark mode.
> - Map pan/zoom fix and zoom buttons.
> - 30-day remember-me logins that survive server restarts.
> - Automatic Cloudflare tunnel, so tracking links open on any device.
> - Interactive email/SMS setup script.
> - **Best time to leave**.
> - USER_GUIDE.md.
>
> Tests: 83.

> Supersedes `IMPROVEMENT_PLAN.md` (kept for history). This is the full plan for turning SafeRoute AI into a finished, demo-ready product. Everything below is built in this release unless it is listed under **Out of scope**.

---

## 0. Why this release

User feedback on v0.2 (Phase 0):

| Feedback | Root cause | Fix in v1.0 |
|---|---|---|
| "Emergency contact is not getting saved" | A server started **before** the Phase 0 changes was still running on port 8080. It ran the old Java code (which needed `?userId=`) while serving the new pages from disk. | Restart on the new build. Add a version check: the UI calls `/api/app-info`, compares `apiVersion`, and shows a clear "server is out of date, restart it" banner if they don't match. |
| "The map is kinda useless" | The map only showed a line and three fake circles. | The map becomes the centre of the app: live GPS dot, click-to-pick places, **real** police stations, hospitals and pharmacies from OpenStreetMap, routes coloured **stretch by stretch** by risk, animated route drawing, live trip tracking, and community reports. |
| "Typing Whitefield should suggest Bangalore Whitefield" | Suggestions needed 3+ characters, went only to a remote service, and showed nothing until it answered. | A built-in **index of 3,899 real Bengaluru places** extracted from OpenStreetMap gives instant prefix matches from the first 2 characters (e.g. "Whi" → Whitefield suburb, Whitefield metro station, Nexus Whitefield mall). Photon results are merged in afterwards for everything else in India. |
| "Find an alternative to Telegram" | Telegram needs every contact to install it and press Start. | **ntfy push notifications**: free, open source, no account or API key needed, delivery tested working. Plus **email** (SMTP), **SMS** (Twilio), and **WhatsApp / SMS / call** buttons from the phone itself. |
| "Navbar shouldn't just link every page; the system should flow" | Multi-page site with a flat menu. | A **single-page app with a guided flow**: Welcome → Sign up → Set up contacts → Plan → Compare routes → Safe Trip → Arrived. Secondary screens sit in a slide-out menu. |
| "Better UI, animations, typography, women-safety theme, SVG icons" | Plain Bootstrap look. | A new design system: plum/violet + rose palette, *Fraunces* display type with *Plus Jakarta Sans* for the interface, about 50 hand-made SVG icons, and motion everywhere it helps (see §6). |
| "All non-working components must work" | Demo stubs: sample places, scoring from distance only, SOS without server alerts. | Every stub is replaced with a working implementation (see §3). Nothing is labelled "coming soon". |

### Design research that shaped this plan
- **What women ask for most:** one-tap SOS and safe places on a map. In the Frabe UX survey, women ranked one-tap SOS first (91%) and nearby safe places on a map second (84%). The same study criticised bSafe for **poor discoverability**: features hidden behind menus.
  - So SOS is a **permanent floating button on every screen** (hold to activate), and help points are **always visible on the map**.
- **Noonlight's no-response alerting:** Noonlight alerts help **when the user stops responding**, rather than relying only on a button press.
  - So Safe Trip mode has **automatic check-ins and escalation**.
- **"Walk me home" sharing:** "Safe Streets"-style concepts let a trusted person watch your walk live. 18 of 20 testers said this made them feel safer.
  - So contacts get a **live tracking link**.
- **bSafe's extra tools** (fake call, timer alarm) are popular.
  - So a **fake incoming call** is included.
- **Privacy is a recurring concern** in research on safety apps.
  - So contacts opt in, tracking links expire, and the app shows what it shares.

---

## 1. Product overview

**SafeRoute AI** helps a woman choose the safest practical route, travel it with a live safety net, and reach help instantly if something goes wrong.

- **Prevent:** routes are scored stretch by stretch from real street data (lighting, activity, isolation, help nearby, reported incidents, time of day).
- **Protect:** in Safe Trip mode, trusted contacts follow live. The app warns before risky stretches and checks in automatically when something looks wrong.
- **Respond:** hold SOS. Alerts go to every contact over push, email and SMS, with a live location link. Calls to 112 and women's helplines are one tap away.

### User flow
```
Welcome ─► Sign up / Log in ─► Set up contacts (skippable) ─► Plan a trip
                                                                │
          ┌─────────────────────────────────────────────────────┘
          ▼
   Compare routes ─► Start Safe Trip ─► (live: warnings, check-ins, alerts) ─► Arrived ─► Plan again
          ▲                                                   │
          └──────────────────── back / edit ◄─────────────────┘

Menu (any time): Safety map · Emergency contacts · Alert history · How it works · Profile · Sign out
SOS button: on every signed-in screen
Contacts receive: push / email / SMS ─► live tracking page (/track/{token}, no login needed)
```

---

## 2. Architecture

```
┌─────────────────────────── Browser (single-page app) ──────────────────────────────┐
│ Router + view transitions · Shared Leaflet map stage · GPS / simulation engine      │
│ Views: welcome, auth, setup, plan, routes, trip, arrived, contacts, explore,        │
│        alerts, insights, profile      ·   SOS overlay   ·   Toasts                   │
└───────────────┬─────────────────────────────────────────────────────▲──────────────┘
                │ JSON REST (session cookie)                            │ SSE (tracking page)
┌───────────────▼─────────────────────────────────────────────────────┴──────────────┐
│ Spring Boot 3.3 (Java 17)                                                           │
│  auth        register/login (BCrypt), session, AuthInterceptor, login rate limit    │
│  geocoding   Bengaluru place index (prefix) + Photon (India) + reverse geocoding    │
│  routing     FOSSGIS OSRM walk/car profiles, alternatives + safe-detour generation  │
│  safety      Overpass corridor query → per-stretch features → Random Forest         │
│              → route score, risky stretches, highlights, help points                │
│  ml          3,000-row training set, RF vs J48 vs Logistic (10-fold CV),            │
│              permutation feature importance, auto-retrain on data change            │
│  alerts      ntfy push · SMTP email · Twilio SMS, async fan-out, delivery log       │
│  trips       live pings, deviation/progress/arrival, watchdog (@Scheduled),         │
│              check-ins, escalation, tracking hub (SSE)                              │
│  reports     community incident reports → feed the incident feature + map layer     │
└───────┬───────────────┬──────────────────┬──────────────────┬──────────────────────┘
        │               │                  │                  │
   H2 file / MySQL   OSRM (FOSSGIS)   Overpass / Photon     ntfy.sh · SMTP · Twilio
```

### Packages
```
config/      SafeRouteProperties, AppConfig (HTTP clients, async, scheduling), WebConfig, GlobalExceptionHandler
controller/  Auth, Contact, Location, Route, Place, Zone, Report, Trip, Alert, Tracking, Model, AppInfo
dto/         request/response records
exception/   NotFound, Unauthorized, LocationNotFound, ExternalService, FieldValidation, TooManyRequests
geo/         GeoPoint, GeoUtils, Polyline (resampling, progress along route)
client/      PhotonClient, NominatimClient, OsrmClient, OverpassClient, NtfyClient, TwilioClient
model/       User, EmergencyContact, SafetyZone, RouteSearchHistory, IncidentReport, Trip, TripPoint,
             Alert, AlertDelivery, enums
repository/  Spring Data repositories
service/     AuthService, CurrentUserService, ContactService, GeocodingService (+PlaceIndex),
             RoutingService, ReportService, SafeLocationService
service/safety/  OsmSafetyDataService, SafetyData, SegmentFeatureExtractor, RouteSafetyAnalyzer
service/ml/      RiskPredictionService, ModelEvaluationService
service/alert/   AlertService, AlertChannel, PushChannel, EmailChannel, SmsChannel, AlertMessages
service/trip/    TripService, TripWatchdog, TrackingHub
```

---

## 3. Feature specifications

### F1 Accounts and session
- Register (name, email, mobile, password ≥ 8 characters) and log in/out. Passwords are hashed with **BCrypt** (`spring-security-crypto`).
- Server-side session with an HTTP-only, `SameSite=Lax` cookie. `AuthInterceptor` protects private APIs and returns 401 JSON.
- **Brute-force protection:** 5 failed logins lock that email for 60 seconds.
- `PUT /api/auth/me` updates name and phone. The name appears in alert messages ("SOS from Priya").

### F2 Emergency contacts
- Up to 5 contacts, each with a name, mobile (stored as E.164), optional email, relationship and a primary flag.
- Each contact gets a private, random **push topic** (`sr-` plus 20 random characters).
- **Setup sharing:** a WhatsApp message, a copyable link, or a **QR code** lets the contact subscribe to push in the free ntfy app or in a browser.
- **"Send test alert"** button per contact, with a live delivery result per channel.
- Channels per contact: Push (always), Email (if an address is given and SMTP is configured), SMS (if Twilio is configured).

### F3 Place search
- **PlaceIndex:** 3,899 real Bengaluru places (suburbs, neighbourhoods, metro and rail stations, malls, universities, colleges, hospitals, parks, attractions) extracted from OpenStreetMap.
  - Prefix matching runs on the whole name, each word, and alternate names (e.g. "MG Road" → Mahatma Gandhi Road metro).
  - Ranking: exact prefix, then type importance, then distance from the map centre.
  - Results are instant (served from memory).
- **Photon** merges in results for anything else (India-filtered, biased to the map centre). Near-duplicates (same name within 1 km) are removed.
- The UI fires an instant **quick search** (index only) and then a **full search**, so the first suggestions appear within about 100 ms.
- **Reverse geocoding** names map taps and GPS fixes ("Near Whitefield Main Road").
- "📍 Current location" is always the first suggestion in the From field.

### F4 Routing
- **Modes:** 🚶 Walk (FOSSGIS `routed-foot`) and 🛺 Cab/Auto (FOSSGIS `routed-car`, falling back to `router.project-osrm.org`).
- Up to 3 alternatives come from OSRM. If fewer come back, **safe detours** are generated by routing via points offset either side of the midpoint. Each detour is kept only if it is ≤ 1.6× the shortest and at least 25% different.
- If routing is down, a single straight-line estimate is returned, clearly labelled.

### F5 Street-level safety scoring
1. **One Overpass corridor query** per search, covering all candidate routes:
   - help points (police, hospital, clinic, pharmacy, fuel) within 2.5 km
   - street lamps and `lit=yes` roads within 60–120 m
   - shops, eateries, banks, ATMs, transit stops and other places with people within 150–300 m
   
   Results are cached (LRU, 30 minutes), retried with backoff, and fail over between mirrors.
2. Each route is resampled every **~120 m**. For each sample:
   - **lighting** (1–5): lamps and lit roads nearby, plus lit shopfronts. Daytime counts as fully lit.
   - **crowd** (1–5): active places and transit nearby, scaled by the hour. Late at night only 24/7 places count fully.
   - **isolation** (1–5): the inverse of activity and transit, plus a night penalty.
   - **incidents** (0–10): community reports within 250 m (decayed over 90 days) plus flagged zones covering the point.
   - **police_km, hospital_km:** distance to the nearest one.
   - **public places** (0–10): open places nearby.
   - **hour**.
3. The **Random Forest** scores every sample.
   - Route score = `100 × (1 − (0.6·mean risk + 0.4·90th-percentile risk))`. The 90th percentile is there so one dark stretch can't be averaged away.
   - Neighbouring samples with the same risk level merge into **coloured stretches** on the map.
4. Each route also gets:
   - **Highlights**, e.g. "Well lit for 82% of the way", "3 police stations within 500 m", "Passes 46 shops and public places", "600 m stretch with few people around".
   - **Weakest stretch**, with its reasons.
   - **Help points along the route.**
5. If Overpass is unreachable, an estimated-features fallback is used and the route is marked `dataQuality: "estimated"`. This is never silent.

### F6 Recommendation
- The safest route wins. Ties go to the faster route.
- A comparison sentence explains the choice: *"Route B takes 3 min longer but is 21 points safer and avoids a 400 m poorly lit stretch."*

### F7 Safe Trip mode
- **Start:** the route is stored and a share token is created. Contacts optionally get a push or email: *"Priya started a trip to Indiranagar, arriving about 21:48. Follow live: …"*.
- **Live position:**
  - With real GPS, the browser's `watchPosition` sends a ping every ≤ 5 s.
  - With **Simulate**, the route is played back at 8× speed. A "wrong turn" button tests deviation detection.
- **Server, on each ping:** progress along the route, remaining distance, ETA, deviation from the route, and arrival detection (≤ 60 m from the destination).
- **Client:** a **"risky stretch ahead"** warning 200 m before a high-risk stretch, with a voice message (`speechSynthesis`), vibration, and the nearest help point.
- **Watchdog** (every 10 s):

  | Condition | Action |
  |---|---|
  | Off route > 45 s | Check-in requested |
  | Overdue (ETA + max(10 min, 30%)) | Check-in requested |
  | Check-in not answered in 60 s | Contacts alerted with last location (**CHECKIN_MISSED**) |
  | No ping for 3 min | Contacts alerted (**LOST_CONTACT**) |
  | "I need help" in check-in | Immediate SOS |
- **Arrival** sends contacts *"Priya reached Indiranagar safely ✅"* automatically and shows an arrival summary.

### F8 SOS
- **Hold** the SOS button for 1.2 s. A ring fills as you hold, which prevents pocket presses.
- A **5-second cancellable countdown** follows. Then `POST /api/alerts/sos` with GPS, accuracy and battery level.
- Push, email and SMS go to all contacts in parallel. **Delivery status per contact and channel** updates live.
- Location keeps streaming to the tracking page every 10 s.
- One-tap fallbacks are always visible: **Call 112**, women's helplines **1091** and **181**, and **WhatsApp / SMS each contact** from the phone (works even if the server can't send).
- **"I'm safe now"** resolves the alert and sends an all-clear to everyone.
- A repeat SOS within 2 minutes reuses the active alert instead of spamming.

### F9 Live tracking page for contacts
- `/track/{token}` is public (the token is ≥ 128 bits random) and needs no login.
- It shows a map, the planned route, the trail, a live marker with accuracy, status (On trip / SOS / Arrived / Lost contact), the time since the last update, battery and ETA.
- Updates arrive over **Server-Sent Events**, with a polling fallback.
- Links expire 2 hours after a trip ends or an alert is resolved.
- Buttons: **Call her** and **Call 112**.

### F10 Safety map (explore)
- Layers: live help points from OpenStreetMap, flagged zones, community reports (heat-style circles).
- "Search this area" button after panning.
- **Report a spot:** tap the map, then pick a category (poor lighting, harassment, isolated, unsafe crowd, other, or ✅ safe spot) and add an optional note. Reports feed the scoring model's incident feature for everyone.

### F11 How it works (insights)
- An animated pipeline diagram of the scoring steps.
- Model comparison chart: RF vs Decision Tree (J48) vs Logistic Regression, with accuracy, macro-F1 and Cohen's kappa from 10-fold cross-validation.
- Confusion matrix heatmap and permutation feature importance.
- Dataset description and an honest limitations section.

### F12 Alert history
- Every alert (SOS, missed check-in, lost contact, trip started, arrived, all-clear, test) with per-contact, per-channel delivery results and error reasons.

### F13 Extras
- **Fake incoming call:** a realistic ringing screen with a configurable caller name. Opened from the menu or from the trip screen.
- **Safety tips** that rotate on the plan screen.
- **Server version check** (see §0).

---

## 4. Machine learning

- **Training data:** `tools/TrainingDataGenerator.java` generates 3,000 synthetic rows, reproducible with a fixed seed.
  - It samples realistic *area types* (busy commercial, residential, transit hub, isolated outskirts, park) and *hours*, then derives correlated features.
  - Labels come from a documented risk function of the features. It adds a night × darkness interaction, Gaussian noise and 4% label noise, so the models have to generalise rather than memorise.
  - The risk function's weights follow **Safetipin-style audit parameters**: lighting, openness, visibility, people, security, walk path, public transport.
- **Models:**
  - Weka `RandomForest` (100 trees, seed 42) is used in production.
  - `J48` and `Logistic` are trained for comparison.
- **Evaluation:**
  - Runs in the background at startup and is cached in `data/ml/metrics.json` against a fingerprint of the data.
  - Covers 10-fold cross-validation, the confusion matrix, and permutation importance on a 25% hold-out set.
- **Auto-retrain:** the model retrains whenever the CSV fingerprint changes.

---

## 5. Alerts: channels and configuration

| Channel | Cost | Setup | What the contact needs |
|---|---|---|---|
| **Push (ntfy)** ⭐ default | Free | None. Works out of the box | Install the free *ntfy* app (Android/iOS) or keep the web page open, then subscribe via the link or QR code the app generates |
| **Email** | Free | `MAIL_USERNAME`, `MAIL_APP_PASSWORD` (Gmail App Password) | Nothing |
| **SMS** | Twilio trial credit | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | Nothing (trial accounts can only text verified numbers) |
| **WhatsApp / SMS / Call from the phone** | Free | None | Nothing |

- **Configuration:** put the variables in a `.env` file in the project folder (loaded with `spring.config.import=optional:file:.env[.properties]`) or set them as environment variables. A `.env.example` file is provided.
- **Public links:** set `PUBLIC_BASE_URL` (for example a `cloudflared` tunnel URL) so tracking links work on contacts' phones. Messages always include a Google Maps link as well, which works anywhere.

---

## 6. Design system

- **Theme:** calm, warm, empowering. The look should be protective rather than alarming.
  - Palette:

    | Role | Colour |
    |---|---|
    | Ink | `#1f1633` |
    | Primary (plum-violet) | `#6d28d9` |
    | Accent (rose-magenta) | `#e11d74` |
    | Brand gradient | `#6d28d9 → #c026d3 → #f43f5e` |
    | Background (lavender-white) | `#faf7fc` |
    | Safe | `#12a26a` |
    | Caution | `#e59a0b` |
    | Danger | `#e5383b` |
- **Typography:**
  - **Fraunces** (soft optical serif) for display headings, giving warmth and character.
  - **Plus Jakarta Sans** for the interface, a friendly and highly legible humanist sans.
  - Numbers use tabular figures.
- **Icons:** about 50 hand-drawn SVG icons on a 24 px grid, 1.8 px rounded stroke, in one inline sprite. The brand mark is a shield with a route-pin heart.
- **Map:** CARTO *Voyager* tiles (clean and low-contrast, so routes and risk colours stand out), with custom pin, help-point and pulsing-location markers.
- **Motion** (all turned off under `prefers-reduced-motion`):
  - **Pages and transitions:** View Transitions API slide/fade between screens (CSS fallback); staggered card entrances; slide-out menu drawer; toasts.
  - **Map:** routes **draw themselves** (stroke-dash animation); risk zones and the GPS dot pulse; markers drop in.
  - **Scores and loading:** score rings count up; skeleton shimmer and a **4-step search progress** ("Finding routes → Scanning street lighting → Locating help nearby → Scoring safety").
  - **Interaction:** ripple on buttons; draggable bottom sheet on mobile.
  - **SOS:** hold ring, countdown pulse, radar waves while sending.
  - **Arrival:** confetti burst.
- **Layout:**
  - Map screens: desktop has a 420 px floating panel over a full-bleed map; mobile has a bottom sheet over the map.
  - Content screens: centred column, maximum 1,100 px.
  - Spacing on an 8 px rhythm.
- **Accessibility:** AA contrast, visible focus rings, keyboard-operable lists and dialogs, `aria-live` status messages, 44 px touch targets.

---

## 7. API (v1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | – | Account |
| GET/PUT | `/api/auth/me` | ✔ | Profile |
| GET/POST | `/api/emergency-contacts` | ✔ | List / add |
| PUT/DELETE | `/api/emergency-contacts/{id}` | ✔ | Edit / remove |
| POST | `/api/emergency-contacts/{id}/test` | ✔ | Send a test alert |
| GET | `/api/locations/search?query=&lat=&lon=&quick=` | – | Suggestions |
| GET | `/api/locations/reverse?lat=&lon=` | – | Name for a point |
| POST | `/api/routes/recommend` | ✔ | Compare and score routes |
| GET | `/api/routes/history` | ✔ | Recent searches |
| GET | `/api/safe-locations?latitude=&longitude=&radius=` | – | Live help points |
| GET | `/api/safety-zones` | – | Flagged zones |
| GET/POST | `/api/reports` | GET –, POST ✔ | Community reports |
| POST | `/api/trips` · GET `/api/trips/{id}` · GET `/api/trips` | ✔ | Start / get / recent |
| POST | `/api/trips/{id}/location`, `/checkin`, `/end` | ✔ | Live trip |
| POST | `/api/alerts/sos` · `/api/alerts/{id}/location` · `/api/alerts/{id}/resolve` | ✔ | SOS |
| GET | `/api/alerts`, `/api/alerts/{id}` | ✔ | History / status |
| GET | `/track/{token}` · `/api/track/{token}` · `/api/track/{token}/stream` | token | Live tracking |
| GET | `/api/model/metrics` · POST `/api/risk/predict` | – | ML |
| GET | `/api/app-info` | – | Config, channels, version |

---

## 8. Data model

```
users(id, name, email UNIQUE, phone, password_hash, created_at)
emergency_contacts(id, user_id, name, phone, email, relation, is_primary, push_topic UNIQUE, created_at)
safety_zones(id, name, description, latitude, longitude, radius_km, risk_level, active, created_at)
route_search_history(id, user_id, source_*, destination_*, route_label, safety_score, risk_level, ...)
incident_reports(id, user_id, latitude, longitude, category, note, created_at)
trips(id, user_id, status, mode, origin_*, destination_*, route_geometry, distance_km, expected_minutes,
      safety_score, started_at, eta, ended_at, share_token UNIQUE, last_* (lat, lon, accuracy, battery, ping_at),
      progress, deviation_meters, off_route_since, check_in_reason, check_in_deadline, notify_contacts, simulated)
trip_points(id, trip_id, latitude, longitude, recorded_at)
alerts(id, user_id, trip_id, type, status, latitude, longitude, accuracy, battery, share_token UNIQUE,
       created_at, resolved_at)
alert_deliveries(id, alert_id, contact_id, contact_name, channel, status, detail, sent_at)
```

---

## 9. Quality

- **Tests**, all offline with mocked outside services:
  - auth and session protection
  - contacts and phone normalisation
  - place-index prefix ranking
  - routing, detours and fallback
  - segment scoring with synthetic OSM data (lit, busy, near police scores higher than dark, empty, far)
  - trips: progress, deviation, arrival, watchdog escalation with a controllable clock
  - alerts: fan-out, delivery records, SOS reuse, resolve
  - tracking snapshots and expiry
  - reports
  - model metrics
  - error contract
- **Security:**
  - BCrypt hashing and login lockout
  - HTTP-only SameSite session cookies
  - JSON-only APIs, which block simple cross-site form posts
  - ownership checks on every record
  - unguessable tracking tokens with expiry
  - escaping of all server text in the UI (`html` tagged template)
  - no secrets in git (`.env` is ignored)
- **Resilience:**
  - every outside service has a timeout, a fallback and an honest UI message
  - Overpass retries and mirrors
  - the Nominatim rate limit is respected
  - the UI keeps working without map tiles
- **Fair use:** results are cached, the User-Agent identifies the app, and OSM/CARTO attribution is shown on every map.

---

## 10. Execution order (this release)

1. Data: Bengaluru place index (done: 3,899 places) and the training data generator with a 3,000-row CSV.
2. Backend:
   1. config and dependencies
   2. auth
   3. models and repositories
   4. clients
   5. geocoding, routing and safety scoring
   6. ML evaluation
   7. alerts
   8. trips, watchdog and tracking
   9. reports
   10. controllers
3. Tests: update the existing suite for auth and add suites for the new features. Everything must pass.
4. Frontend: design system, icons, router and shell, then each view, the SOS overlay, and the tracking page.
5. End-to-end verification on the running app:
   1. register and add a contact
   2. live route scoring in Bengaluru
   3. a simulated trip with deviation, check-in and alert
   4. SOS, with the push delivery read back from ntfy
   5. the tracking stream
6. Docs: README, `.env.example`, and restarting the server on the new build.

## 11. Out of scope (future)
- Native Android app with background location and a hardware-button SOS.
- Integration with ERSS-112 and state police apps.
- Real labelled incident data (police open data or Safetipin audits) to replace the synthetic training data.
- Street-view image analysis for lighting.
- Multi-language UI (Kannada, Hindi).

## References
- [Frabe: UX case study on women's safety apps (survey: one-tap SOS 91%, safe places map 84%; review of bSafe, Himmat Plus)](https://hank0811.github.io/Portfolio/Frabe.html)
- [Pocket-sized protection: features of bSafe and Noonlight](https://www.diggitmagazine.com/node/6061)
- [Safe Streets concept: user test, walking-home sharing](https://medium.com/52-weeks-of-design-for-women/1-as-a-woman-i-am-always-afraid-of-my-safety-8a14f677242b)
- [Safely: competitor analysis of Life360, Noonlight, Im Safe](https://catelynbu.github.io/catelyn-portfolio/projects/safely/safely.html)
- [ntfy publishing: title, priority, tags, click URL](https://github.com/binwiederhier/ntfy/blob/v2.6.2/docs/publish.md)
- [FOSSGIS routing server (car, bike, foot profiles)](https://routing.openstreetmap.de/about.html) · [OSRM API usage policy](https://github.com/Project-OSRM/osrm-backend/wiki/API%20Usage%20Policy)
