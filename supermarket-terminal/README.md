# Supermarket Terminal

Three pieces, three languages, each doing the job it's actually good at:

1. **`python_bridge/transceiver_server.py`** — your *unmodified* ultrasonic
   protocol (FREQ_GRID, UltraSender, UltraReceiver — copied verbatim from
   your script) wrapped in a tiny Flask API. This is the only thing that
   touches audio hardware. Runs locally on the till machine.
2. **`server.js`** (Node/Express) — serves the browser UI and holds your
   Daraja (M-Pesa) credentials, firing the real STK Push. Never touches audio.
3. **`public/`** (browser) — just a UI. No audio decoding happens in the
   browser at all anymore; it calls the Python bridge over HTTP/SSE and the
   Node backend over HTTP.

This replaces an earlier attempt at porting your DSP logic to JavaScript —
that was a real re-implementation risk (untested against real audio). This
version runs your actual tested Python code instead, so there's no
compatibility drift from your sender.

## Why three processes instead of one

`sounddevice`/PortAudio (mic + speaker I/O) is Python-only here, and Daraja
credentials should never sit in browser JS. Splitting them out means:
- Your protocol code stays exactly as tested — no rewrite risk.
- Payment secrets never reach the browser.
- The browser is just a dumb display + button-clicker, easy to restyle
  without touching either backend.

## Setup

### 1. Python bridge (protocol/audio)

```bash
cd python_bridge
python3 transceiver_server.py
```

First run auto-creates `~/.ultrasonic_env` and installs `numpy`,
`sounddevice`, `flask`, `flask-cors` into it — same pattern as your original
script's venv bootstrap. Leave this running; it listens on
`http://localhost:5005`.

### 2. Node backend (STK push + serves the UI)

```bash
npm install
cp .env.example .env
# fill in your real Daraja credentials — see below
npm start
```

Runs on `http://localhost:4000`.

### 3. Open the terminal

Open `http://localhost:4000` in a browser **on the same machine** as the
Python bridge (it calls `localhost:5005` directly). If the till and the
Python process end up on different machines, change `TRANSCEIVER_URL` at
the top of `public/app.js` to point at the Python machine's address, and
you'll need HTTPS for mic-adjacent contexts — though note the browser no
longer needs mic access itself now, since Python owns the mic. CORS is
already open on the Flask side via `flask-cors`.

### Daraja credentials (from https://developer.safaricom.co.ke)
- `CONSUMER_KEY` / `CONSUMER_SECRET` — from your Daraja app
- `BUSINESS_SHORTCODE` — your Paybill/Till number (sandbox gives you a test one, e.g. `174379`)
- `PASSKEY` — Lipa Na M-Pesa Online passkey tied to that shortcode
- `CALLBACK_URL` — must be public HTTPS (use `ngrok http 4000` for local dev)

## Flow at the till

1. Cashier clicks **Start Listening** → browser calls
   `POST http://localhost:5005/api/start-listen` → Python starts your
   `UltraReceiver.listen()` loop on a background thread, exactly like
   `main_menu()` choice `"1"`.
2. Customer's phone transmits its number.
3. Python decodes a candidate, echoes it back via `sender.transmit()` — same
   handshake as your CLI version — and pushes a `candidate` event over SSE.
4. Once the sender's ACK (`#<number>`) arrives, Python pushes a `confirmed`
   event; the UI unlocks the amount field.
5. Cashier enters the amount, clicks **Send Payment Prompt** → this call
   goes to the *Node* backend (`/api/stkpush`), which fires the Daraja STK
   Push.
6. Node polls Safaricom's callback and the UI polls Node until the customer
   approves/declines.

## What's genuinely new vs. your script

- `UltraReceiver.listen()` gained one addition: a `stop_event` check inside
  the existing loop, so the web UI's "Stop Listening" button can interrupt
  it (your original relied on `KeyboardInterrupt`, which a background
  thread can't receive). No detection/decoding logic changed.
- A `run_receive_session()` function that's a direct translation of
  `main_menu()`'s choice `"1"` while-loop, emitting events to a queue
  instead of `print()`-ing.
- Flask routes (`/api/start-listen`, `/api/stop-listen`, `/api/status`,
  `/api/stream`) — pure plumbing, no protocol logic.

Everything else in `transceiver_server.py` between the `ORIGINAL PROTOCOL
CODE` markers is copy-pasted from your script unchanged.

## Notes / things to decide next

- **One till at a time**: `run_receive_session` uses a single global
  `UltraSender`/`UltraReceiver` pair with a lock preventing concurrent
  sessions. Fine for one cashier station; if you run multiple tills, each
  needs its own `transceiver_server.py` instance on its own machine/port.
- **Transaction storage** (Node side) is in-memory — fine for one till,
  swap for a real DB if this needs to survive restarts or scale.
- **Production**: put both the Node and Python services behind a process
  manager (systemd/pm2) so they restart if the till reboots.
