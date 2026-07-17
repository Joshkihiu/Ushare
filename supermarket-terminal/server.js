// server.js
// Supermarket web terminal backend — cashier-side STK Push trigger.
//
// This is the ONLY place your Daraja (M-Pesa) credentials should ever live.
// Never put CONSUMER_KEY / CONSUMER_SECRET / PASSKEY in frontend JS.

require('dotenv').config();
const express = require('express');
const axios = require('axios');
const path = require('path');

const app = express();
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const {
  DARAJA_ENV = 'sandbox', // 'sandbox' or 'production'
  CONSUMER_KEY,
  CONSUMER_SECRET,
  BUSINESS_SHORTCODE,
  PASSKEY,
  CALLBACK_URL, // must be a public HTTPS URL (use ngrok for local dev)
  PORT = 4000
} = process.env;

const BASE_URL =
  DARAJA_ENV === 'production'
    ? 'https://api.safaricom.co.ke'
    : 'https://sandbox.safaricom.co.ke';

function assertConfigured() {
  const missing = ['CONSUMER_KEY', 'CONSUMER_SECRET', 'BUSINESS_SHORTCODE', 'PASSKEY', 'CALLBACK_URL']
    .filter((key) => !process.env[key]);
  if (missing.length) {
    throw new Error(`Missing required env vars: ${missing.join(', ')}. See .env.example.`);
  }
}

function timestampNow() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return (
    d.getFullYear().toString() +
    pad(d.getMonth() + 1) +
    pad(d.getDate()) +
    pad(d.getHours()) +
    pad(d.getMinutes()) +
    pad(d.getSeconds())
  );
}

/** Normalizes local Kenyan numbers (07xx / 01xx / +254...) to Daraja's required 2547xxxxxxxx format. */
function normalizeMsisdn(raw) {
  const digits = raw.replace(/\D/g, '');
  if (digits.startsWith('254') && digits.length === 12) return digits;
  if (digits.startsWith('0') && digits.length === 10) return '254' + digits.slice(1);
  if (digits.startsWith('7') && digits.length === 9) return '254' + digits;
  if (digits.startsWith('1') && digits.length === 9) return '254' + digits;
  throw new Error(`Could not normalize phone number: ${raw}`);
}

async function getAccessToken() {
  const credentials = Buffer.from(`${CONSUMER_KEY}:${CONSUMER_SECRET}`).toString('base64');
  const { data } = await axios.get(`${BASE_URL}/oauth/v1/generate?grant_type=client_credentials`, {
    headers: { Authorization: `Basic ${credentials}` }
  });
  return data.access_token;
}

// In-memory log of recent transactions for the cashier UI to poll.
// Swap for a real DB/queue if this needs to survive restarts or scale.
const transactions = new Map();

app.post('/api/stkpush', async (req, res) => {
  try {
    assertConfigured();
    const { phone, amount, accountReference = 'Supermarket', description = 'Checkout' } = req.body;

    if (!phone || !amount) {
      return res.status(400).json({ error: 'phone and amount are required' });
    }
    const msisdn = normalizeMsisdn(phone);
    const numericAmount = Math.round(Number(amount));
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      return res.status(400).json({ error: 'amount must be a positive number' });
    }

    const token = await getAccessToken();
    const timestamp = timestampNow();
    const password = Buffer.from(`${BUSINESS_SHORTCODE}${PASSKEY}${timestamp}`).toString('base64');

    const { data } = await axios.post(
      `${BASE_URL}/mpesa/stkpush/v1/processrequest`,
      {
        BusinessShortCode: BUSINESS_SHORTCODE,
        Password: password,
        Timestamp: timestamp,
        TransactionType: 'CustomerPayBillOnline',
        Amount: numericAmount,
        PartyA: msisdn,
        PartyB: BUSINESS_SHORTCODE,
        PhoneNumber: msisdn,
        CallBackURL: CALLBACK_URL,
        AccountReference: accountReference,
        TransactionDesc: description
      },
      { headers: { Authorization: `Bearer ${token}` } }
    );

    transactions.set(data.CheckoutRequestID, {
      status: 'pending',
      phone: msisdn,
      amount: numericAmount,
      createdAt: Date.now()
    });

    res.json({
      ok: true,
      checkoutRequestId: data.CheckoutRequestID,
      merchantRequestId: data.MerchantRequestID,
      customerMessage: data.CustomerMessage
    });
  } catch (err) {
    const details = err.response?.data || err.message;
    console.error('STK push failed:', details);
    res.status(500).json({ error: 'STK push failed', details });
  }
});

// Daraja calls this asynchronously once the customer accepts/declines/times out on their phone.
app.post('/api/mpesa/callback', (req, res) => {
  const stkCallback = req.body?.Body?.stkCallback;
  if (!stkCallback) {
    console.warn('Unexpected callback payload:', JSON.stringify(req.body));
    return res.json({ ResultCode: 0, ResultDesc: 'Accepted' });
  }

  const { CheckoutRequestID, ResultCode, ResultDesc, CallbackMetadata } = stkCallback;
  const success = ResultCode === 0;
  const meta = {};
  if (success && CallbackMetadata?.Item) {
    for (const item of CallbackMetadata.Item) {
      meta[item.Name] = item.Value;
    }
  }

  transactions.set(CheckoutRequestID, {
    ...(transactions.get(CheckoutRequestID) || {}),
    status: success ? 'success' : 'failed',
    resultDesc: ResultDesc,
    mpesaReceiptNumber: meta.MpesaReceiptNumber,
    amountConfirmed: meta.Amount,
    completedAt: Date.now()
  });

  console.log(`STK callback [${CheckoutRequestID}]: ${success ? 'SUCCESS' : 'FAILED'} — ${ResultDesc}`);
  res.json({ ResultCode: 0, ResultDesc: 'Accepted' });
});

// Cashier UI polls this to see if the customer has paid yet.
app.get('/api/stkpush/:checkoutRequestId', (req, res) => {
  const tx = transactions.get(req.params.checkoutRequestId);
  if (!tx) return res.status(404).json({ error: 'not found' });
  res.json(tx);
});

app.listen(PORT, () => {
  console.log(`Supermarket terminal backend running on http://localhost:${PORT}`);
  console.log(`Daraja env: ${DARAJA_ENV} (${BASE_URL})`);
});