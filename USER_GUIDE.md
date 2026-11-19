# SafeRoute AI: User Guide

A practical, step-by-step guide to using every part of SafeRoute AI, plus a **5-minute demo script** for the project
review. For what is implemented and how it works inside, see [README.md](README.md).

---

## Contents
1. [Start the app](#1-start-the-app)
2. [Create your account](#2-create-your-account)
3. [Add emergency contacts](#3-add-emergency-contacts)
4. [Make sure contacts receive alerts](#4-make-sure-contacts-receive-alerts)
5. [Plan a trip](#5-plan-a-trip)
6. [Compare routes](#6-compare-routes)
7. [Travel with Safe Trip](#7-travel-with-safe-trip)
8. [SOS: when you need help now](#8-sos-when-you-need-help-now)
9. [What your contacts see](#9-what-your-contacts-see)
10. [Safety map and reporting a spot](#10-safety-map-and-reporting-a-spot)
11. [Other screens](#11-other-screens)
12. [Dark mode](#12-dark-mode)
13. [Using it on your phone](#13-using-it-on-your-phone)
14. [5-minute demo script for the review](#14-5-minute-demo-script-for-the-review)
15. [Troubleshooting](#15-troubleshooting)

---

## 1. Start the app

1. Open a terminal in the project folder (`C:\Saferoute\saferoute`).
2. Run:
   ```
   mvn spring-boot:run
   ```
3. Wait for `Started SafeRouteAiApplication` (about 30 s). If public links are on, it also prints:
   ```
   ==> Public address: https://<words>.trycloudflare.com
   ```
4. Open **http://localhost:8080** in Chrome or Edge.

To stop the app, press **Ctrl+C** in that terminal. Your data stays saved in the `data/` folder, and you remain
signed in for 30 days.

---

## 2. Create your account

1. On the welcome screen press **Get started, it's free**.
2. Enter your **name**, **email**, **mobile number** and a **password** (8+ characters), then press **Create account**.
   - Your first name and number appear in alerts so contacts can call you back.
3. You are taken to a 3-step setup. You can skip any step and come back later.

*Already registered?* Choose **Sign in**. After 5 wrong passwords, sign-in pauses for 60 seconds.

---

## 3. Add emergency contacts

1. In setup step 1 (or later via **Menu → Emergency contacts**) press **Add your first contact**.
2. Fill in:
   - **Name** and **Mobile number**: `98765 43210` is fine, +91 is added automatically.
   - **Email** (optional), used when email alerts are configured.
   - **Relationship**: tap a chip (Mother, Friend…) or type your own.
   - **Primary contact**: the person called first. Only one contact can be primary.
3. Press **Save contact**.

You can save up to **5** contacts. Each card has **Set up alerts**, **Test**, **Make primary**, **Edit** (✏️) and
**Remove** (🗑️, asks to confirm).

---

## 4. Make sure contacts receive alerts

Alerts reach contacts as **instant push notifications** through the free **ntfy** app. Do this once per contact:

1. On the contact's card press **Set up alerts**. A QR code and instructions appear.
2. Send the setup to them:
   - press **Send via WhatsApp** to send a ready-made invite with the link, or
   - let them **scan the QR code** with their phone, or
   - press **Copy invite** and paste it anywhere.
3. On **their** phone they:
   1. install **ntfy** from the Play Store / App Store (or just open the link in a browser);
   2. open the link (or in the app tap **+** and type the topic shown, e.g. `sr-AbC…`) and tap **Subscribe**.
4. Back on your card, press **Test**. They should get *"🔔 Test alert from SafeRoute AI"* within seconds, and you see
   *"Test sent to … by push"*.

**Email and SMS** work too once the server is configured (see README §12). The badges on each card show which
channels are active: green means on, grey means off.

---

## 5. Plan a trip

The **Plan** screen shows a map of where you are, with nearby police stations, hospitals, pharmacies and fuel
stations as icons.

1. **From**: tap the field and choose **Current location** (needs location permission), type a place, or press the
   ⌖ button. The start can also be a pin you drop on the map.
2. **To**: start typing. Suggestions appear immediately: "whi" shows **Whitefield**, "kora" shows **Koramangala**,
   "mg ro" shows **MG Road metro**. Use ↑ ↓ and Enter, or click.
   - Or **tap anywhere on the map** and choose **Go here** (or **Start here**).
   - The ⇅ button swaps start and destination. **Recent destinations** appear as chips below.
3. **When**: **Now**, **In 30 min**, **Tonight 9 PM**, or **Pick a time**. Safety depends heavily on the hour.
4. **How**: **Walking** or **Cab / auto**.
5. Press **Find safe routes**.

**Map controls:** drag to move, scroll or pinch to zoom, use the **+ / −** buttons (bottom right), and ⌖ to return
to your position.

---

## 6. Compare routes

A checklist animates while SafeRoute finds routes, scans street data and scores every stretch (a few seconds). Then:

- **The comparison line at the top**, e.g. *"Route B takes 3 min longer but is 21 points safer."*
- **Route cards**, each with:
  - a **safety score ring** (0–100) and a risk badge (Low / Medium / High risk);
  - travel time and distance;
  - **Recommended** and **Fastest** tags;
  - a **risk strip** showing which parts of the route are green, amber or red.
- **The selected route's details**:
  - ✅/⚠️ **highlights**: lighting %, police stations nearby, number of shops and public places, quiet stretches;
  - the **weakest stretch** and why it's weak;
  - **help along this route**: tap one to fly to it on the map;
  - 🆕 **Best time to leave**: a small chart of the safety score for each of the next 12 departure hours. **You**
    marks your chosen time and **Best** marks the safest. Hover over a bar for its score.
- **On the map**, the selected route is coloured stretch by stretch and the others are grey. Tap a grey route (or its
  card) to compare it.

At the bottom:
- **Share this trip live with N contacts**: when on, contacts are told when you start and when you arrive.
- **Start Safe Trip on Route X**: begins live protection with your real GPS.
- **▶**: starts a **simulated** trip (for demos and testing, no walking needed).

---

## 7. Travel with Safe Trip

The trip screen follows you on the map.

- **The top banner** shows where you're heading and the distance left. It changes to:
  - 🟠/🔴 **Riskier / quieter stretch ahead**, about 200 m before a risky part, with a voice message, vibration and the
    nearest help point;
  - 🟠 **You're off your route**, when you leave the planned path;
  - 🔴 **Your contacts have been alerted**, with an **I'm safe** button.
- **The bottom panel** shows your arrival time, time and distance left, a progress bar, and **Contacts following live**.
- **Buttons:**
  - **Share live**: copy the tracking link, or send it on WhatsApp.
  - **Fake call**: an incoming call rings in 5 s. Answer it to leave an uncomfortable situation.
  - **End trip**: stops sharing (asks to confirm).
- ⌖ re-centres the map on you if you've moved it.

**Automatic check-ins.** If you leave the route for about 45 s or run much later than expected, a full-screen
**"Are you OK?"** appears with a countdown:
- **I'm OK**: everything continues as normal.
- **I need help**: an SOS goes to all contacts immediately.
- **No answer before the countdown ends**: contacts are alerted automatically with your last location.

If your phone stops sending its position for 3 minutes, contacts are alerted too.

**Arrival.** Within about 60 m of the destination, the trip ends automatically. Contacts get *"✅ … arrived
safely"* and you see a summary with confetti. Rate how the route felt; your answer helps score routes for everyone.

**Simulated trips (demo).** The demo bar has **Pause/Play**, speed **4× / 8× / 16×**, and **Wrong turn**, which
walks you off the route to trigger the check-in. Timers are shorter in simulation: about 15 s to check-in, then 30 s
to answer.

---

## 8. SOS: when you need help now

The red **SOS** button is on every screen once you're signed in.

1. **Press and hold it for 1 second.** A ring fills around the button.
   - A quick tap only shows a hint, which prevents accidental alerts. Keyboard users can press Enter on it.
2. A **5-second countdown** starts. Press **Cancel** if it was a mistake, or **Send now** to skip the wait.
3. SOS is sent. Every contact gets *"🚨 SOS from …"* with your location and live tracking link.
4. The SOS screen shows:
   - **Call 112**, **Women 1091**, **Helpline 181**: one tap to call;
   - **Alert delivery**: each contact and channel, with **Delivered** / Sending / Failed (and why);
   - **WhatsApp / call** buttons for each contact and **Text everyone by SMS**, sent from your own phone (works even
     if the server can't send);
   - **I'm safe now**: sends an all-clear to everyone and closes the alert.
5. **Hide** keeps the alert active (the SOS button pulses). Tap SOS again to reopen it.

While SOS is active your position is sent to the tracking page every 10 seconds.

---

## 9. What your contacts see

1. A **push notification** (and email/SMS if configured) with a title like *"🚨 SOS from Priya"*, the time, your
   location, battery level, and buttons: **Live location**, **Open map**, **Call**.
2. **Live location** opens the **tracking page**:
   - a map with your route, trail and live position;
   - a status card ("Priya is on the way", "may need help", "arrived safely") with last update, battery and ETA;
   - **Call Priya** and **Call 112** buttons.

The tracking link works on **any device** when public links are on (the default in your `.env`). Otherwise contacts
use the Google Maps link included in every alert. Links stop working 2 hours after the trip ends or the SOS is
resolved.

---

## 10. Safety map and reporting a spot

**Menu → Safety map.**
- **Choose where to look.** Any of these works:
  - type an area in the search box (e.g. "Koramangala", "MG Road", "Christ University") and pick a suggestion, or press Enter;
  - pick **Current location**, or press ⌖;
  - **tap anywhere on the map** and choose **Show help around here**;
  - move the map and press **Search this area**.
- A pin marks the spot, and the panel says *"Around Koramangala"*.
- Choose the **radius**: **1 km / 2.5 km / 5 km**. If nothing is mapped nearby, the app says so and offers a 5 km search.
- The list shows a count (police · hospitals/clinics · pharmacies · fuel), each place's distance, an **open 24/7** tag
  and a **Directions** link (opens Google Maps). Filter **Police / Hospital / Pharmacy / Fuel**, and tap a row to fly to it.
- Toggle layers: **Help points**, **Community reports** (and **Risk zones** when demo zones are on).
- **Report a spot**:
  1. press the button, then tap the place on the map;
  2. choose what it's like: *Poor lighting, Harassment, Isolated, Unsafe crowd, Other,* or *Felt safe here*;
  3. add an optional note and **Submit**.

  Your report appears on the map and affects route scores near it for 90 days. Reports are anonymous to other users.

---

## 11. Other screens

Open them from the **menu** (your avatar, top right):

| Screen | What it's for |
|---|---|
| **Plan a safe trip** | Back to planning |
| **Safety map** | Help points and community reports (see §10) |
| **Emergency contacts** | Manage contacts, set up alerts, test them, see which channels are on |
| **Alert history** | Every alert and exactly who received it, by which channel. Resolve an active SOS here too |
| **How the AI works** | The scoring pipeline, model accuracy comparison, feature importance and confusion matrix (hover over bars and cells). Use this in your review |
| **Profile & settings** | Edit name and phone, **Appearance** (dark mode), alert channel status, credits |
| **Fake incoming call** | Rings in 5 seconds |
| **Sign out** | Ends your session on this browser |

---

## 12. Dark mode

- Press the **🌙 / ☀️** button in the top bar on any screen to switch instantly.
- Or go to **Profile → Appearance** and choose **System** (follow your device), **Light** or **Dark**.
- The map, charts and tracking page switch too. Your choice is remembered in this browser.

---

## 13. Using it on your phone

Phones only allow GPS on secure (https) pages, so use the **public address**:

1. Start the app with public links on (`PUBLIC_TUNNEL=true` in `.env`, already set).
2. Copy the `https://….trycloudflare.com` address from the terminal and open it on your phone.
3. Sign in, allow location, and plan a trip. Safe Trip now uses your phone's real GPS.

The layout adapts: the map stays on top, panels become bottom sheets (tap the handle to expand or collapse), and SOS
moves to the top right.

---

## 14. 5-minute demo script for the review

**Before the review:**
- start the app;
- sign in;
- add a contact with a classmate's number;
- have the classmate subscribe to the push topic (step 4) and keep their phone visible.

Optional: set `DEMO_ZONES=true` in `.env` to show flagged zones.

| # | Do | Say |
|---|---|---|
| 1 | Welcome screen → sign in | "Normal maps optimise for speed. SafeRoute optimises for safety, before, during and after the trip." |
| 2 | Plan: type **"whi"** → Whitefield, or **Indiranagar**. Choose **Tonight 9 PM**, **Walking** | "Instant search over 3,899 real Bengaluru places. Time of day changes everything." |
| 3 | **Find safe routes**: watch the checklist | "It finds real routes, scans street lamps, shops and police from OpenStreetMap, and scores every 120 m with a Random Forest." |
| 4 | Point at the coloured route, highlights and weakest stretch. Tap the other route | "Green, amber, red per stretch. It explains why: lighting %, police nearby, quiet stretches." |
| 5 | Scroll to **Best time to leave** | "Same route, every hour: leaving earlier raises safety from X to Y." |
| 6 | Press **▶** (simulate). Show the classmate's phone: *"started a Safe Trip"* | "Contacts are told instantly and can follow live." |
| 7 | Open **Share live** → open the tracking link in a new tab | "This is what a contact sees, with no login needed." |
| 8 | Watch the **riskier stretch ahead** banner and voice | "Prevention: it warns before you reach a risky stretch." |
| 9 | Press **Wrong turn**. Wait for **Are you OK?** Don't answer | "If she stops responding, it escalates on its own." The classmate's phone shows *"missed a safety check-in"* |
| 10 | Press **I'm safe** | Contacts get the all-clear |
| 11 | **Hold SOS 1 s** → countdown → **Send now**. Show the deliveries going green | "One press reaches everyone, with 112 and helplines one tap away." Then **I'm safe now** |
| 12 | Menu → **How the AI works** | "Three models compared with 10-fold cross-validation. Random Forest about 85%. Lighting and time matter most." |
| 13 | Toggle **dark mode** | Design and accessibility |

---

## 15. Troubleshooting

| Problem | Fix |
|---|---|
| Changes don't appear / contacts don't save / red "older version" banner | An old server is still running. Stop every SafeRoute terminal (Ctrl+C), run `mvn spring-boot:run` again, and press **Ctrl+F5** in the browser |
| "Port 8080 is already in use" | Another copy is running. Close it, or start with `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090` |
| "Current location" doesn't work | Allow location in the browser. On phones use the https public address (see §13). Or type a place / tap the map |
| No suggestions while typing | Type at least 2 letters. Online results need internet, but city places work offline |
| Contact didn't get a push | They must subscribe to **their own** topic in ntfy (step 4). Press **Test** and check **Alert history** for the result |
| Email / SMS shows "not set up" | Fill in `.env` (`scripts\configure.ps1`) and restart. Twilio trial numbers must be verified in the Twilio console |
| Tracking link only works on my computer | Check the terminal for `Public address`. If missing, install cloudflared (`winget install Cloudflare.cloudflared`) and keep `PUBLIC_TUNNEL=true` |
| Route score says "estimated" | Street data couldn't be loaded (outside Bengaluru and the live service is busy). Try again shortly |
| The map won't move | Press Ctrl+F5 to load the latest version. Drag with the mouse, scroll to zoom, or use + / − |
| I want to start fresh | Stop the app and delete the `data/` folder (deletes all accounts and contacts) |
