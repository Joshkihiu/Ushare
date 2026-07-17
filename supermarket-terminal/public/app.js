// app.js — till terminal, driven by your custom ultrasonic protocol
// (ultrasonic.js). This mirrors the Python menu's "1) Receive" flow:
// listen -> echo the candidate back for the sender to verify -> keep
// listening -> once the sender's ACK ("#<number>") arrives, treat the
// number as confirmed and unlock the amount/STK-push step.

const micDot = document.getElementById('micDot');
const micStatusText = document.getElementById('micStatusText');
const numberDisplay = document.getElementById('numberDisplay');
const customerNumber = document.getElementById('customerNumber');
const amountInput = document.getElementById('amountInput');
const sendPromptBtn = document.getElementById('sendPromptBtn');
const statusLine = document.getElementById('statusLine');
const micToggleBtn = document.getElementById('micToggleBtn');
const logEl = document.getElementById('log');
const clearLogBtn = document.getElementById('clearLogBtn');

if (clearLogBtn) {
  clearLogBtn.addEventListener('click', () => { logEl.innerHTML = ''; });
}

function log(msg) {
  const time = new Date().toLocaleTimeString();
  const line = document.createElement('div');
  line.textContent = `[${time}] ${msg}`;
  logEl.prepend(line);
}

function setStatus(text, kind = '') {
  statusLine.textContent = text;
  statusLine.className = `status-line ${kind}`;
}

let audioCtx = null;
let mediaStream = null;
let sender = null;
let receiver = null;
let running = false;
let confirmedNumber = null;

async function ensureAudio() {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 44100 });
  }
  if (audioCtx.state === 'suspended') await audioCtx.resume();
  if (!mediaStream) {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false }
    });
  }
  if (!sender) sender = new ULTRASONIC.UltraSender(audioCtx);
}

async function startListening() {
  try {
    await ensureAudio();
    running = true;
    micDot.classList.add('listening');
    micStatusText.textContent = 'Listening…';
    micToggleBtn.textContent = 'Stop Listening';
    micToggleBtn.classList.add('active');
    setStatus('Awaiting incoming signal…', 'pending');
    log('Awaiting incoming signal…');
    runReceiveLoop();
  } catch (err) {
    console.error(err);
    setStatus(`Mic error: ${err.message}`, 'error');
  }
}

function stopListening() {
  running = false;
  if (receiver) receiver.stop();
  micDot.classList.remove('listening');
  micStatusText.textContent = 'Mic off';
  micToggleBtn.textContent = 'Start Listening';
  micToggleBtn.classList.remove('active');
  log('Stopped listening.');
}

async function runReceiveLoop() {
  while (running) {
    receiver = new ULTRASONIC.UltraReceiver(audioCtx, mediaStream);
    const msg = await receiver.listen({});
    if (!running) return;
    if (!msg) continue;

    if (msg.startsWith(ULTRASONIC.ACK_PREFIX)) {
      const number = msg.slice(ULTRASONIC.ACK_PREFIX.length).trim();
      onConfirmed(number);
      return;
    }

    numberDisplay.textContent = msg;
    log(`Received candidate: "${msg}" — echoing back for verification…`);
    setStatus('Verifying with sender…', 'pending');
    await sender.transmit(msg);
    log(`Echoed "${msg}". Waiting for sender confirmation…`);
    // loop continues; the next listen() call is waiting for the ACK
  }
}

function onConfirmed(number) {
  confirmedNumber = number;
  numberDisplay.textContent = number;
  customerNumber.textContent = number;
  sendPromptBtn.disabled = !amountInput.value || Number(amountInput.value) <= 0;
  setStatus('Number confirmed.', 'success');
  log(`CONFIRMED: ${number}`);
  stopListening();
}

micToggleBtn.addEventListener('click', () => {
  if (running) stopListening();
  else startListening();
});

amountInput.addEventListener('input', () => {
  sendPromptBtn.disabled = !confirmedNumber || !amountInput.value || Number(amountInput.value) <= 0;
});

sendPromptBtn.addEventListener('click', async () => {
  if (!confirmedNumber || !amountInput.value) return;
  sendPromptBtn.disabled = true;
  setStatus('Sending payment prompt…', 'pending');
  log(`Requesting STK push: ${confirmedNumber} -> KES ${amountInput.value}`);

  try {
    const res = await fetch('/api/stkpush', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: confirmedNumber, amount: amountInput.value })
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'STK push failed');

    setStatus('Prompt sent — waiting for customer to approve on their phone…', 'pending');
    log(`STK push sent (CheckoutRequestID: ${data.checkoutRequestId}).`);
    pollForResult(data.checkoutRequestId);
  } catch (err) {
    console.error(err);
    setStatus(`Error: ${err.message}`, 'error');
    sendPromptBtn.disabled = false;
  }
});

function pollForResult(checkoutRequestId) {
  const started = Date.now();
  const interval = setInterval(async () => {
    if (Date.now() - started > 90000) {
      clearInterval(interval);
      setStatus('Timed out waiting for customer response.', 'error');
      resetForNextCustomer();
      return;
    }
    try {
      const res = await fetch(`/api/stkpush/${checkoutRequestId}`);
      if (!res.ok) return;
      const tx = await res.json();
      if (tx.status === 'success') {
        clearInterval(interval);
        setStatus(`Paid! Receipt: ${tx.mpesaReceiptNumber || 'confirmed'}`, 'success');
        log(`Payment SUCCESS — receipt ${tx.mpesaReceiptNumber}`);
        resetForNextCustomer();
      } else if (tx.status === 'failed') {
        clearInterval(interval);
        setStatus(`Payment failed: ${tx.resultDesc}`, 'error');
        log(`Payment FAILED — ${tx.resultDesc}`);
        resetForNextCustomer();
      }
    } catch (err) {
      // transient network hiccup — keep polling
    }
  }, 2500);
}

function resetForNextCustomer() {
  setTimeout(() => {
    confirmedNumber = null;
    numberDisplay.textContent = '— — — — — — — — —';
    customerNumber.textContent = 'Not yet received';
    amountInput.value = '';
    sendPromptBtn.disabled = true;
  }, 4000);
}
