# SafeRoute AI 🛡️

**A women's-safety route planner: choose the safest route, travel with a live safety net, and reach help in one press.**

SafeRoute AI scores every route **stretch by stretch** from real OpenStreetMap street data (street lamps,
shops and people around, isolation, police and hospitals nearby, community reports, time of day) with a
**machine-learning model**, then recommends the safest practical route and explains why. During the trip, trusted
contacts follow live. The app warns before risky stretches and checks on the user if something looks wrong. If she
doesn't answer, or presses SOS, every contact is alerted instantly with her live location.

> SafeRoute AI is a safety aid, not a guarantee. The model is trained on synthetic data (see [Limitations](#15-limitations)).
> In an emergency always call **112**.

📘 **How to use the app, step by step:** see **[USER_GUIDE.md](USER_GUIDE.md)**.
🗺️ **Full design plan and rationale:** see **[PLAN.md](PLAN.md)**.

---

## Contents
1. [The problem and our approach](#1-the-problem-and-our-approach)
2. [Features implemented](#2-features-implemented)
3. [Technology stack](#3-technology-stack)
4. [Architecture](#4-architecture)
5. [How a route is scored (the AI)](#5-how-a-route-is-scored-the-ai)
6. [Machine-learning model and results](#6-machine-learning-model-and-results)
7. [Alerts: how contacts are reached](#7-alerts-how-contacts-are-reached)
8. [Safe Trip: live protection](#8-safe-trip-live-protection)
9. [Data storage and persistence](#9-data-storage-and-persistence)
10. [Security and privacy](#10-security-and-privacy)
11. [Setup and running](#11-setup-and-running)
12. [Configuration: email, SMS, public links](#12-configuration-email-sms-public-links)
13. [Testing and verification](#13-testing-and-verification)
14. [API reference](#14-api-reference)
15. [Limitations](#15-limitations)
16. [Future scope](#16-future-scope)
17. [Project structure](#17-project-structure)
18. [Review / viva quick answers](#18-review--viva-quick-answers)

---

## 1. The problem and our approach

Navigation apps optimise for **distance and time**. They do not consider whether a road is dark, deserted, far from
help, or known for incidents, and they do nothing once you start walking. Most women's-safety apps do the opposite:
they offer an SOS button, which only helps **after** something has gone wrong.

SafeRoute AI covers the whole journey:

| | What it does |
|---|---|
| **Prevent** | Compares real routes by safety, not just speed, and explains the risk of every stretch. |
| **Protect** | Safe Trip shares your location live, warns before risky stretches, and checks in automatically. |
| **Respond** | Hold SOS: every contact is alerted over push, email and SMS with a live map, and 112 is one tap away. |

Design research showed women most want **one-tap SOS** and **safe places on a map** (Frabe UX survey: 91% and 84%).
It also showed that the best apps act when the user **stops responding** (Noonlight) and let trusted people follow a
walk home ("Safe Streets" concept). All three ideas are built in.

---

## 2. Features implemented

Everything below is implemented, tested and working.

### Accounts and onboarding
- Register / sign in. Passwords are stored only as **BCrypt hashes**.
- **Stay signed in for 30 days**, even across server restarts (secure "remember me" cookie).
- Brute-force protection: 5 wrong passwords lock the account for 60 seconds.
- Guided first-run setup: **add contacts → send them alert setup → allow location**.

### Place search
- **Instant prefix suggestions** from a built-in index of **3,899 real Bengaluru places** (suburbs, neighbourhoods,
  metro and rail stations, malls, colleges, hospitals, parks). For example "whi" gives Whitefield and "mg ro" gives
  the MG Road metro station.
- Online results from Photon merged in for the rest of India, with duplicates removed.
- "Current location" (GPS) suggestion, **tap-the-map** to drop a pin, and reverse geocoding ("Doctor B R Ambedkar Veedhi").

### Route comparison (the core feature)
- Real **walking** and **cab/auto** routes from OSRM (FOSSGIS servers), plus generated **safe detours** so there is a
  choice even when the router returns only one route.
- Every route gets a **0–100 safety score**, a **risk-coloured route line** (green / amber / red per stretch), a risk strip, and:
  - **Highlights**, e.g. "Well lit for 71% of the way", "10 police stations within 500 m", "Passes 1,418 shops", "2.3 km stretch with few people around".
  - **Weakest stretch**, e.g. "840 m near Saraswathipuram: poorly lit · few people around".
  - **Help points along the route**: police, hospitals, clinics, pharmacies, fuel, flagged when open 24/7.
- A plain-language comparison: *"Route B takes 3 min longer but is 21 points safer."*
- 🆕 **Best time to leave**: the same route scored for each of the next 12 hours, e.g. *"Leaving at 7 AM instead of 9 PM raises safety from 48 to 75."*

### Safe Trip (live protection)
- Live position on the map with a trail, progress bar, ETA and distance left.
- **"Riskier stretch ahead"** warning 200 m before a risky stretch, with **voice**, vibration and the nearest help point.
- **Automatic check-ins** when you leave the route or run late, with a countdown ring: *"Are you OK?"*.
- **Automatic escalation**: missed check-in or a silent phone means contacts are alerted.
- **Arrival detection**: contacts are told you arrived safely.
- **Share live link** (copy / WhatsApp / system share sheet) and **fake incoming call**.
- **Trip simulator** (▶ button, 4×/8×/16× speed, "wrong turn") to demonstrate everything without walking.

### SOS
- **Hold for 1 second** (prevents pocket presses), then a **5-second cancellable countdown**.
- Alerts every contact over **push, email and SMS** in parallel, with **live per-contact delivery status**.
- Streams your location to the tracking page every 10 seconds; *"I'm safe now"* sends an all-clear.
- Always-available fallbacks: **Call 112, 1091, 181**, and **WhatsApp / SMS each contact from your own phone**.

### Live tracking page for contacts
- `/track/{token}`, no login needed: live map, route, trail, status (on the way / needs help / arrived),
  battery, last update, ETA, and **Call her / Call 112** buttons. Updates arrive **instantly** (Server-Sent Events).
- Works on **any phone or computer** through the built-in Cloudflare tunnel. Links expire 2 h after the trip ends.

### Emergency contacts
- Up to 5 contacts (international phone format, optional email, relationship, primary contact).
- A private **push topic per contact**, a **QR code / WhatsApp invite** to set up alerts, and a **Test** button with delivery results.

### Safety map and community
- Live help points around any area, with filters (police / hospital / pharmacy / fuel) and "Search this area".
- **Report a spot** (poor lighting, harassment, isolated, unsafe crowd, other, or ✅ felt safe). Reports **feed the
  model's incident feature** for everyone and fade out over 90 days.
- After every trip: *"How did this route feel?"*, which also becomes a report.

### Transparency
- **How the AI works** screen: an animated pipeline, model comparison chart, feature importance, confusion matrix, data description and limitations.
- **Alert history**: every alert and exactly who it reached, by which channel, or why it failed.

### User interface
- Single-page app with a **guided flow**: Plan → Compare → Trip → Arrived.
- Women-safety theme (plum, violet, rose), *Fraunces* + *Plus Jakarta Sans* typography, **~60 hand-drawn SVG icons**.
- 🆕 **Dark mode**: top-bar toggle plus Profile → Appearance (System / Light / Dark), including a dark map.
- Animations: screen transitions, self-drawing routes, counting score rings, ripples, toasts, radar SOS, confetti on arrival.
  Everything respects *reduced motion*.
- Responsive: floating panels on desktop, bottom sheets on phones. Keyboard and screen-reader friendly.

---

## 3. Technology stack

| Layer | Technology |
|---|---|
| Backend | **Java 17, Spring Boot 3.3** (Web, Data JPA, Validation, Mail), Server-Sent Events |
| Machine learning | **Weka 3.8**: Random Forest (in use), J48 decision tree and Logistic Regression (compared) |
| Database | **H2** file database (default, zero setup), **MySQL** supported |
| Security | BCrypt (spring-security-crypto), HTTP-only SameSite cookies |
| Frontend | HTML, CSS, **vanilla JavaScript ES modules** (no build step), **Leaflet** maps |
| Map data | **OpenStreetMap** tiles, a bundled OSM snapshot, the Overpass API |
| Routing | **OSRM** (FOSSGIS walking and car servers) |
| Geocoding | Built-in place index, **Photon**, **Nominatim** |
| Alerts | **ntfy** push (free), **SMTP** email (Gmail), **Twilio** SMS |
| Public links | **Cloudflare quick tunnel** (`cloudflared`) |
| Build / tests | Maven, JUnit 5, Spring MockMvc, Mockito |

---

## 4. Architecture

```
┌──────────────────────────── Browser (single-page app) ────────────────────────────┐
│ Hash router + view transitions · one shared Leaflet map · GPS / trip simulator     │
│ Screens: welcome, auth, setup, plan, routes, trip, arrived, contacts, explore,     │
│          alerts, insights, profile · SOS overlay · fake call · dark/light theme    │
└──────────────┬─────────────────────────────────────────────────▲──────────────────┘
               │ JSON REST + session cookie                       │ Server-Sent Events
┌──────────────▼─────────────────────────────────────────────────┴──────────────────┐
│ Spring Boot                                                                        │
│  controller/  Auth · Contacts · Locations · Routes · Places · Zones · Reports ·    │
│               Trips · Alerts · Tracking · AppInfo/Model                            │
│  service/     Auth, CurrentUser (session + remember-me), Geocoding (+PlaceIndex),  │
│               Routing (+safe detours), Reports, SafeLocations, Tunnel              │
│    safety/    CitySnapshot, OsmSafetyData, SegmentFeatureExtractor, RouteAnalyzer  │
│    ml/        RiskPrediction (Random Forest), ModelEvaluation (CV, importance)     │
│    alert/     AlertService, channels (ntfy / SMTP / Twilio), messages, links       │
│    trip/      TripService + Watchdog (every 5 s), TrackingHub (SSE), Tracking      │
│  repository/  Spring Data JPA                                                      │
└───────┬───────────────┬───────────────┬────────────────┬───────────────┬──────────┘
     H2 / MySQL     OSRM routing    Overpass / Photon   ntfy · SMTP ·    cloudflared
                                    / Nominatim         Twilio           tunnel
```

Each request flows **controller → service → repository**. Outside services sit behind `client/` classes that
have timeouts and fallbacks, and the tests mock them.

---

## 5. How a route is scored (the AI)

```
from/to ─► OSRM routes (+ safe detours) ─► street data along all routes (one lookup)
       ─► every 120 m: 8 features ─► Random Forest ─► risk 0..1 for each stretch
       ─► route score = 100 × (1 − (0.6 × average risk + 0.4 × 90th-percentile risk))
       ─► neighbouring stretches with the same risk level merge into coloured segments
       ─► the safest route is recommended (ties go to the faster one), with highlights and the weakest stretch
```

The **90th-percentile term** stops one dangerous stretch from being hidden by a long safe average.

**The 8 features, computed for every stretch from real data:**

| Feature | How it is measured |
|---|---|
| Time of travel | Departure hour (0–23) |
| Lighting (1–5) | Street lamps within 60 m, lit roads within 80 m, lit shopfronts. Daylight counts as fully lit |
| People around (1–5) | Shops, eateries, banks and transit nearby, scaled by hour. Late at night only 24/7 places count fully |
| Road isolation (1–5) | Inverse of activity and transit, plus a night penalty |
| Reported incidents (0–10) | Community reports within 250 m (fading over 90 days), plus flagged zones |
| Distance to police (km) | Nearest police station |
| Distance to hospital (km) | Nearest hospital or clinic |
| Open public places (0–10) | Places open at that hour nearby |

**Street data.** For Bengaluru, a **bundled OpenStreetMap snapshot** is used (`geo/bengaluru-safety.json.gz`), so
scoring takes about 2 seconds and works offline. It contains 4,078 police, hospital, clinic, pharmacy and fuel
points, 6,119 street lamps, 2,778 lit roads, 30,719 shops and active places, and 3,949 transit stops. Elsewhere the
live Overpass API is used. If no street data is available, the score is clearly labelled **estimated** and makes no
claims about lighting or crowds.

---

## 6. Machine-learning model and results

- **Algorithm in use:** Weka **Random Forest** (100 trees, fixed seed for reproducibility).
- **Training data:** 3,000 examples generated by `tools/TrainingDataGenerator.java`:
  - It samples realistic *area types* (busy commercial, transit hub, residential, park edge, isolated outskirts) and *hours*.
  - It derives correlated features.
  - It labels each example with a documented risk function based on safety-audit parameters (lighting, openness,
    people, security, transit, incidents, time). A night × darkness interaction, noise and 4% label noise make the
    models generalise rather than memorise.
- **Evaluation:** 10-fold cross-validation, shown live on the *How the AI works* screen.

| Model | Accuracy | Macro F1 |
|---|---|---|
| **Random Forest (used)** | **84.6%** | **0.836** |
| Decision Tree (J48) | 82.1% | 0.808 |
| Logistic Regression | 84.9% | 0.840 |

- **Feature importance** (permutation: accuracy drop when a feature is shuffled): lighting matters most, then time of
  travel, then people around. This matches published safety-audit findings.
- **Why Random Forest:** it is as accurate as logistic regression here, it captures non-linear interactions such as
  "dark *and* late *and* isolated", it is robust to noisy features, and it gives class probabilities, which we turn
  into a continuous risk score.
- **Auto-retrain:** the model is cached with a fingerprint of the CSV and retrains automatically when the data changes.

---

## 7. Alerts: how contacts are reached

| Channel | Cost | Setup | Contact needs |
|---|---|---|---|
| **Push (ntfy)** | Free | None. On by default | The free **ntfy** app (or a web browser). Subscribe once via the QR / link from *Set up alerts* |
| **Email** | Free | Gmail address + App Password | Nothing |
| **SMS** | Twilio trial credit | Account SID, token, number | Nothing (trial: number must be verified in Twilio) |
| **From your phone** | Free | None | WhatsApp / SMS / call buttons on the SOS screen |

**Alert types:**
- 🚨 SOS
- ⚠️ Missed check-in
- ⚠️ Lost contact
- 🧭 Trip started
- ✅ Arrived safely
- 💚 All clear
- 🔔 Test

Every message includes a **Google Maps link** (works anywhere) and, when public links are on, the **live tracking link**.

Sending runs in the background, in parallel. Each attempt is stored (`alert_deliveries`) with SENT / FAILED / SKIPPED
and the reason, and the UI shows it live. A repeated SOS within 2 minutes reuses the active alert instead of spamming.

---

## 8. Safe Trip: live protection

| Situation | Detection | Response |
|---|---|---|
| Risky stretch ahead | Within 200 m of an amber/red stretch (client side) | Banner, voice, vibration, nearest help point |
| Off route | > 150 m off the route for 45 s | "Are you OK?" check-in with a 60 s countdown |
| Running late | Past ETA + max(10 min, 30%) | Check-in |
| Check-in unanswered | Deadline passed | **Contacts alerted** (missed check-in) |
| "I need help" in a check-in | User choice | **SOS** to all contacts |
| Phone silent | No position for 3 min | **Contacts alerted** (lost contact) |
| Arrived | Within 60 m of the destination | Contacts told "arrived safely" |

A server-side **watchdog** runs every 5 seconds, so escalation works even if the phone dies or is taken.
**Simulated trips** use shorter timers (15 s / 30 s / 90 s) so a demo takes about a minute.

---

## 9. Data storage and persistence

**Yes, everything persists across server restarts.** Data is stored in an **H2 database file**:
`data/saferoute.mv.db` (in the project folder).

| Stored | Survives restart? |
|---|---|
| Accounts (name, email, phone, **BCrypt password hash**) | ✅ Yes |
| Emergency contacts (+ their private push topics) | ✅ Yes |
| Trips, live position trail, alerts and delivery results | ✅ Yes |
| Community reports, route search history | ✅ Yes |
| **Being signed in** | ✅ Yes, the 30-day "remember me" token is stored (hashed) in the database. Sessions are re-created automatically after a restart |
| Theme choice (light/dark) | Saved in your browser |
| ML model and evaluation results | Cached in `data/ml/` (rebuilt automatically if missing) |

- `mvn clean` does **not** delete data, because it only cleans `target/`.
- To **reset everything**, stop the app and delete the `data/` folder.
- To use **MySQL** instead, set `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` and `_DRIVER_CLASS_NAME`
  (`com.mysql.cj.jdbc.Driver`). Tables are created automatically.

---

## 10. Security and privacy

- **Passwords:** BCrypt hashed. Never returned by any API. Login lockout after 5 failures.
- **Sessions:** HTTP-only, `SameSite=Lax` cookies. The session id changes at login (prevents session fixation).
  "Remember me" tokens are 256-bit random values stored only as SHA-256 hashes, and revoked on logout.
- **Authorisation:** every record is checked for ownership; another user's contact, trip or alert returns 404.
- **Tracking links:** 128-bit unguessable tokens that expire 2 hours after the trip or alert ends.
- **XSS:** all server text is escaped through an `html` tagged template before it reaches the page.
- **CSRF:** JSON-only APIs plus SameSite cookies block cross-site form posts.
- **Secrets:** stored only in `.env`, which is git-ignored.
- **Consent and privacy:** contacts opt in by subscribing. Reports are anonymous to other users. Location is
  shared only during a trip or SOS.

---

## 11. Setup and running

**Prerequisites:** Java 17+ and Maven 3.9+. Optional: `cloudflared` for public links.

```bash
cd C:\Saferoute\saferoute
mvn spring-boot:run
```

Open **http://localhost:8080**. The first start takes about 30 s (it trains the model and loads the city data).

- Stop the app: **Ctrl+C** in its terminal.
- Run the tests: `mvn test`.
- Build a single runnable file: `mvn package`, then `java -jar target/saferoute-ai-1.0.0.jar`.

> **Something behaves like an old version?** An older server is still running on port 8080. Stop it and start again.
> The app shows a red banner when it detects an out-of-date server.

---

## 12. Configuration: email, SMS, public links

All settings live in **`.env`** in the project folder (already created). The easiest way to fill it in is the
interactive script:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\configure.ps1
```

Then restart the app. Each option, in detail:

### Live tracking links that open on any device ✅ already enabled
`.env` contains `PUBLIC_TUNNEL=true`, and `cloudflared` is installed. On startup the app opens a free Cloudflare
tunnel and prints:

```
==> Public address: https://<random-words>.trycloudflare.com  (tracking links in alerts now work on any device)
```

Every tracking link in alerts then uses that address, so it opens on any phone or computer. The address **changes on
every start**. Anyone with the address can reach the app while it runs. For a permanent address, set
`PUBLIC_BASE_URL=https://your-domain` instead. Bonus: open the public address on your phone to use the app there
with real GPS (phones require https for location).

### Email (Gmail, free)
1. Turn on **2-Step Verification**: <https://myaccount.google.com/security>.
2. Create an **App Password** (name it "SafeRoute"): <https://myaccount.google.com/apppasswords>.
3. In `.env`:
   ```
   MAIL_USERNAME=your.address@gmail.com
   MAIL_APP_PASSWORD=abcdefghijklmnop
   ```
4. Restart, add an **email address** to a contact, and press **Test**.

### SMS (Twilio)
1. Sign up at <https://www.twilio.com/try-twilio> (trial credit is free).
2. Console → *Account Info*: copy the **Account SID** and **Auth Token**.
3. Console → *Phone Numbers* → get a Twilio number.
4. **Trial accounts can only text verified numbers**: Console → *Phone Numbers → Verified Caller IDs* → add each
   contact's number.
5. In `.env`:
   ```
   TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   TWILIO_FROM_NUMBER=+1xxxxxxxxxx
   ```
6. Restart and press **Test** on a contact. Indian carriers may filter international promotional SMS. Delivery
   status and any errors appear in **Alert history**.

### All settings

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | 8080 | HTTP port |
| `PUBLIC_TUNNEL` | false (`true` in your `.env`) | Open a Cloudflare tunnel for public tracking links |
| `PUBLIC_BASE_URL` | `http://localhost:{port}` | Fixed public address instead of a tunnel |
| `CLOUDFLARED_PATH` | auto-detected | Location of `cloudflared` if not standard |
| `MAIL_USERNAME`, `MAIL_APP_PASSWORD` | empty | Email alerts |
| `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER` | empty | SMS alerts |
| `PUSH_ENABLED`, `NTFY_URL` | true, `https://ntfy.sh` | Push alerts (a self-hosted ntfy also works) |
| `DEMO_ZONES` | false | Three synthetic flagged zones in central Bengaluru, for demos |
| `SPRING_DATASOURCE_*` | H2 file | Use MySQL |

---

## 13. Testing and verification

- **83 automated tests** (`mvn test`), all passing, all offline (outside services are mocked):
  - accounts and lockout; remember-me across a lost session; logout revokes it
  - contacts: phone formats, primary, limit, ownership; test alerts
  - place index prefix search; reverse geocoding fallbacks
  - routing: detours, server fallback, straight-line estimate, unknown places
  - **scoring with synthetic street data**: the lit, busy route with police beats the dark, empty one at night
  - trips: progress, deviation → check-in → missed check-in alert, lost contact, arrival, tracking links and expiry
  - SOS: delivery, reuse within 2 min, resolve / all-clear
  - model: sensible predictions, 3-model cross-validation, feature importance
  - best time to leave; error handling; bundled snapshot loading
- **End-to-end against live services** (38 checks):
  - real routes and street data in Bengaluru
  - push alerts read back from ntfy (trip started, missed check-in, SOS, all clear, arrived)
  - real police stations returned
  - the full escalation chain
- **Browser walkthrough** of every screen (desktop and phone sizes, light and dark) in headless Chrome, with no
  JavaScript errors.
- **Public tunnel** verified: tracking page and API reachable from the internet over https.

---

## 14. API reference

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` | – | Account |
| GET / PUT | `/api/auth/me` | ✔ | Profile |
| GET / POST | `/api/emergency-contacts` | ✔ | List / add |
| PUT / DELETE | `/api/emergency-contacts/{id}` | ✔ | Edit / remove |
| POST | `/api/emergency-contacts/{id}/test` | ✔ | Send a test alert |
| GET | `/api/locations/search?query=&lat=&lon=&quick=` | – | Place suggestions |
| GET | `/api/locations/reverse?lat=&lon=` | – | Name a point |
| POST | `/api/routes/recommend` | ✔ | Compare and score routes |
| POST | `/api/routes/time-profile` | ✔ | Best time to leave |
| GET | `/api/routes/history` | ✔ | Recent searches |
| GET | `/api/safe-locations?latitude=&longitude=&radius=` | – | Help points |
| GET / POST | `/api/reports` · GET `/api/reports/categories` | GET –, POST ✔ | Community reports |
| GET | `/api/safety-zones` | – | Flagged zones (demo) |
| POST | `/api/trips` · GET `/api/trips`, `/api/trips/active`, `/api/trips/{id}` | ✔ | Safe Trip |
| POST | `/api/trips/{id}/location`, `/checkin`, `/end` | ✔ | Live trip |
| POST | `/api/alerts/sos`, `/api/alerts/{id}/location`, `/api/alerts/{id}/resolve` | ✔ | SOS |
| GET | `/api/alerts`, `/api/alerts/{id}` | ✔ | Alert history / status |
| GET | `/track/{token}`, `/api/track/{token}`, `/api/track/{token}/stream` (SSE) | token | Live tracking |
| GET | `/api/model/metrics` · POST `/api/risk/predict` | – | Model |
| GET | `/api/app-info` | – | Version, channels, public link status |

All errors share one shape: `{"message": "...", "fieldErrors": {...}, "timestamp": "..."}`.

---

## 15. Limitations
- The ML model is trained on **synthetic** data. It demonstrates the method but is not measured crime data.
- OpenStreetMap coverage varies. Where lamps aren't mapped, lighting is inferred from activity.
- The bundled street-data snapshot covers **Bengaluru**. Other cities use live Overpass, which can be slow when busy.
- The free tunnel address changes on each start. Twilio trial SMS reaches verified numbers only.
- Browser GPS stops when the phone locks the screen. A native app would be needed for background tracking.

## 16. Future scope
- Train on real labelled data (police open data, Safetipin audits) and keep retraining from community reports.
- Native Android/iOS app: background location, power-button SOS, offline maps.
- Integration with ERSS-112 and state police apps. Multi-language UI (Kannada, Hindi).
- Street-view image analysis to estimate lighting automatically. Snapshots for more cities.

---

## 17. Project structure

```
saferoute/
├─ pom.xml · .env (your settings) · .env.example · README.md · USER_GUIDE.md · PLAN.md
├─ scripts/configure.ps1               interactive alert / link setup
├─ tools/TrainingDataGenerator.java    regenerates the 3,000-row training set
└─ src/
   ├─ main/java/com/saferoute/ai/
   │  ├─ config/       settings, HTTP clients, security interceptor, error handling
   │  ├─ controller/   REST endpoints
   │  ├─ client/       OSRM, Overpass, Photon, Nominatim, ntfy, Twilio
   │  ├─ service/      auth, geocoding, routing, reports, tunnel
   │  │  ├─ safety/    city snapshot, feature extraction, route analysis
   │  │  ├─ ml/        Random Forest, model evaluation
   │  │  ├─ alert/     alert service, channels, messages
   │  │  └─ trip/      trips, watchdog, live tracking (SSE)
   │  ├─ model/ · repository/ · dto/ · geo/ · exception/
   ├─ main/resources/
   │  ├─ application.properties · sql/data.sql
   │  ├─ ml/safety_risk_training.csv
   │  ├─ geo/bengaluru-places.json · geo/bengaluru-safety.json.gz
   │  └─ static/  index.html · track.html · css/app.css · js/ (router, map, theme, SOS, views/…)
   └─ test/java/…  83 tests
```

---

## 18. Review / viva quick answers

**Where is the AI?**
A Random Forest predicts Low/Medium/High risk for every 120 m of a route from 8 features measured from real
OpenStreetMap data. Its probabilities become a 0–100 score.

**Why not just use crime data?**
Public crime data in India is district-level and not real-time. Street-level environmental factors (lighting,
activity, isolation, access to help) are what safety audits measure, and OpenStreetMap provides them openly.
Community reports add an incident signal.

**How do you know the model works?**
10-fold cross-validation: Random Forest 84.6% accuracy, macro-F1 0.836. Compared against J48 and logistic regression.
Permutation importance shows lighting and time matter most, which matches safety-audit research.

**What happens if the internet or a service is down?**
- Routing falls back to a server mirror, then to a labelled straight-line estimate.
- Street data comes from the bundled snapshot.
- Search falls back to the built-in place index.
- The SOS screen always offers 112 and messaging from the phone.

**How do contacts actually get alerts?**
Instant ntfy push notifications (free, no account), plus email and SMS when configured. Every delivery is logged and
shown live.

**What if she can't press SOS?**
Safe Trip's watchdog alerts contacts automatically on a missed check-in or when the phone goes silent.

**Is data kept after a restart?**
Yes. Everything is in the H2 database file, and logins persist for 30 days via hashed remember-me tokens.

**How is privacy handled?**
- Contacts opt in.
- Tracking links are unguessable and expire.
- Reports are anonymous.
- Passwords are hashed.
- Location is shared only during a trip or SOS.
