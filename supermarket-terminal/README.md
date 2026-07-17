# Supermarket Terminal

Cashier web terminal:
1. Listens via the browser mic for your custom dual-tone ultrasonic protocol (ported directly from your Python `UltraSender`/`UltraReceiver` — same `FREQ_GRID`, phase-shift anti-blur, and 3-round majority-vote scheme, **not** a generic library like ggwave).
2. Echoes the candidate number back through the speaker for the sender to verify (same handshake as your Python "1) Receive" menu option).
3. Once the sender's ACK (`#<number>`) is decoded, the number is confirmed and the amount field unlocks.
4. Cashier types the amount, hits **Send Payment Prompt**.
5. Backend fires a real Daraja STK Push to the customer's phone.
6. Terminal polls until the customer approves/declines, then shows the result.

## ⚠️ Important: this DSP code is untested against real audio

I ported `ultrasonic.js` line-by-line from your Python script (same frequency grid, same SNR threshold, same FFT band logic, own from-scratch radix-2 FFT since there's no numpy in the browser). But I have no microphone/speaker in this environment to actually run it against your sender. Realistic things to check on first real test:

- **`SNR_THRESHOLD` (3.2)** may need retuning — laptop/phone mic noise floors above 15kHz vary a lot by device, more than they do on whatever hardware you tested the Python version on.
- **FFT window size**: Python uses a non-power-of-two block (`44100 * 0.024 ≈ 1058` samples). Browsers need power-of-two FFT sizes for a fast transform, so `ultrasonic.js` rounds to the *nearest* power of two (1024) rather than padding up to 2048, to stay close to your original ~24ms timing. If character blurring shows up, this is the first thing to revisit.
- **AudioContext sample rate**: it requests `44100` explicitly, but not all browsers honor a requested rate — Safari in particular sometimes ignores it. The code reads `audioCtx.sampleRate` dynamically everywhere rather than hardcoding 44100, so it should stay internally consistent even if the browser silently picks a different rate, but the *actual* transmitted frequencies will drift if that happens.
- **Echo/feedback**: the receiver mutes its own analysis tap before connecting to the speaker output, so it shouldn't hear its own echo transmission as new input — but worth confirming on real hardware, especially with speaker/mic close together at a till.

Expect one or two rounds of "record real audio → tell me what broke" once you can test it against your phone.

## Setup

```bash
npm install
cp .env.example .env
# edit .env with your real Daraja sandbox/production credentials
```

### Daraja credentials you need (from https://developer.safaricom.co.ke)
- `CONSUMER_KEY` / `CONSUMER_SECRET` — from your Daraja app
- `BUSINESS_SHORTCODE` — your Paybill/Till number (sandbox gives you a test one, e.g. `174379`)
- `PASSKEY` — Lipa Na M-Pesa Online passkey tied to that shortcode
- `CALLBACK_URL` — **must be public HTTPS**. Daraja cannot reach `localhost`.

### Exposing your callback URL locally
Daraja needs to call your server back when the customer approves/declines. While developing locally, use ngrok:

```bash
ngrok http 4000
```

Copy the `https://xxxx.ngrok-free.app` URL it gives you, then set in `.env`:
```
CALLBACK_URL=https://xxxx.ngrok-free.app/api/mpesa/callback
```
(Restart the server after changing `.env`.)

## Run

```bash
npm start
```

Open `http://localhost:4000` on the cashier machine (needs mic **and speaker** — use HTTPS or `localhost`, since browsers block mic access on plain HTTP for any other origin).

## Flow at the till

1. Cashier clicks **Start Listening**.
2. Customer's phone transmits its number via your existing Python (or equivalent) sender.
3. Terminal decodes a candidate, echoes it back through the till's speaker for the sender to verify — matches your Python handshake exactly.
4. Sender confirms the echo matched and transmits the ACK (`#<number>`); terminal decodes that and shows **Number confirmed**.
5. Cashier enters the amount, clicks **Send Payment Prompt**.
6. Customer gets the M-Pesa PIN prompt on their phone.
7. Terminal polls and shows **Paid!** with the M-Pesa receipt number once confirmed.

## Notes / things to decide next

- **Transaction storage** is in-memory (`Map` in `server.js`) — fine for a single till on one machine, but restarts wipe pending transactions and it won't work if you run multiple terminal instances. Swap for SQLite/Postgres if this needs to scale past one till.
- **HTTPS in production**: mic access requires a secure context. `localhost` is exempt for dev, but a real deployment needs TLS (put it behind nginx/Caddy or your cloud provider's HTTPS).
- **Multiple simultaneous tills** transmitting/listening near each other could cross-talk since they're all in the same ultrasonic band — worth testing if you're deploying more than one terminal in the same store.

