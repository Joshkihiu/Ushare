// app.js — till terminal UI. All ultrasonic decode/transmit logic lives in
// your unmodified Python script (python_bridge/transceiver_server.py). This
// file just calls that local service and updates the screen — it does NOT
// do any audio processing itself.

const TRANSCEIVER_URL = 'http://localhost:5005'; // where transceiver_server.py runs

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

let running = false;
let confirmedNumber = null;
let eventSource = null;

function connectStream() {
  if (eventSource) return;
  eventSource = new EventSource(`${TRANSCEIVER_URL}/api/stream`);

  eventSource.onmessage = (e) => {
    let event;
    try { event = JSON.parse(e.data); } catch { return; }

    switch (event.type) {
      case 'status':
        setStatus(event.value, 'pending');
        log(event.value);
        break;
      case 'candidate':
        numberDisplay.textContent = event.value;
        log(`Received candidate: "${event.value}"`);
        break;
      case 'confirmed':
        onConfirmed(event.value);
        break;
      case 'stopped':
        if (running) stopListeningUI();
        break;
      default:
        break;
    }
  };

  eventSource.onerror = () => {
    // EventSource auto-reconnects; just surface it if the bridge is down.
    if (running) setStatus('Lost connection to Python bridge — is transceiver_server.py running?', 'error');
  };
}

async function startListening() {
  try {
    connectStream();
    const res = await fetch(`${TRANSCEIVER_URL}/api/start-listen`, { method: 'POST' });
    const data = await res.json();
    if (!res.ok || !data.ok) throw new Error(data.error || 'Failed to start listening');

    running = true;
    micDot.classList.add('listening');
    micStatusText.textContent = 'Listening…';
    micToggleBtn.textContent = 'Stop Listening';
    micToggleBtn.classList.add('active');
    setStatus('Awaiting incoming signal…', 'pending');
    log('Started listening (Python bridge).');
  } catch (err) {
    console.error(err);
    setStatus(
      `Could not reach Python bridge at ${TRANSCEIVER_URL} — run "python transceiver_server.py" on this machine.`,
      'error'
    );
  }
}

async function stopListening() {
  try {
    await fetch(`${TRANSCEIVER_URL}/api/stop-listen`, { method: 'POST' });
  } catch (err) {
    console.error(err);
  }
  stopListeningUI();
}

function stopListeningUI() {
  running = false;
  micDot.classList.remove('listening');
  micStatusText.textContent = 'Mic off';
  micToggleBtn.textContent = 'Start Listening';
  micToggleBtn.classList.remove('active');
  log('Stopped listening.');
}

function onConfirmed(number) {
  confirmedNumber = number;
  numberDisplay.textContent = number;
  customerNumber.textContent = number;
  sendPromptBtn.disabled = !amountInput.value || Number(amountInput.value) <= 0;
  setStatus('Number confirmed.', 'success');
  log(`CONFIRMED: ${number}`);
  running = false;
  micDot.classList.remove('listening');
  micStatusText.textContent = 'Mic off';
  micToggleBtn.textContent = 'Start Listening';
  micToggleBtn.classList.remove('active');
}

micToggleBtn.addEventListener('click', () => {
  if (running) stopListening();
  else startListening();
});

amountInput.addEventListener('input', () => {
  // Strip non-digit characters for consistent cross-browser behavior
  amountInput.value = amountInput.value.replace(/\D/g, '');
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
