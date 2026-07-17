// ultrasonic.js
//
// Browser port of the custom dual-tone ultrasonic protocol from the Python
// UltraSender/UltraReceiver. This is NOT a generic library like ggwave —
// it's a 1:1 port of your own FREQ_GRID / phase-shift / majority-vote
// scheme, so it can talk to your existing Python sender/receiver.
//
// Encodes ASCII codes 0-63 only (digits, space, '#') — same constraint as
// the Python version, which is fine since we're only ever sending phone
// numbers + the ACK marker.
//
// Honest caveat: I ported this by careful line-by-line translation of your
// script, but I have no way to run real audio through a mic/speaker in this
// environment. Expect to tune SNR_THRESHOLD / frequency tolerances once you
// test against real hardware — mic/speaker frequency response above 15kHz
// varies a lot device to device.

const ULTRASONIC = (() => {
  const TONE_DURATION = 0.024;
  const GAP_DURATION = 0.005;
  const ROUND_GAP = 0.020;

  const LOW_ROW_FREQS = [15000, 15200, 15400, 15600, 15800, 16000, 16200, 16400];
  const HIGH_COL_FREQS = [17500, 17700, 17900, 18100, 18300, 18500, 18700, 18900];
  const PHASE_SHIFT_OFFSET = 300;
  const SNR_THRESHOLD = 3.2;
  const ACK_PREFIX = '#';

  // --- Build FREQ_GRID / REV_GRID exactly as the Python version does ---
  const FREQ_GRID = {};
  const REV_GRID = new Map();
  {
    let idx = 0;
    for (const rFreq of LOW_ROW_FREQS) {
      for (const cFreq of HIGH_COL_FREQS) {
        if (idx < 128) {
          FREQ_GRID[idx] = [rFreq, cFreq];
          REV_GRID.set(`${rFreq}|${cFreq}`, idx);
          REV_GRID.set(`${rFreq}|${cFreq + PHASE_SHIFT_OFFSET}`, idx);
          idx++;
        }
      }
    }
  }

  function findClosestGridTones(peakLow, peakHigh) {
    let closestLow = LOW_ROW_FREQS[0];
    let bestLowDist = Infinity;
    for (const f of LOW_ROW_FREQS) {
      const d = Math.abs(f - peakLow);
      if (d < bestLowDist) { bestLowDist = d; closestLow = f; }
    }
    const possibleHighs = [...HIGH_COL_FREQS, ...HIGH_COL_FREQS.map((f) => f + PHASE_SHIFT_OFFSET)];
    let closestHigh = possibleHighs[0];
    let bestHighDist = Infinity;
    for (const f of possibleHighs) {
      const d = Math.abs(f - peakHigh);
      if (d < bestHighDist) { bestHighDist = d; closestHigh = f; }
    }
    if (bestLowDist < 40 && bestHighDist < 40) return [closestLow, closestHigh];
    return null;
  }

  function hann(n) {
    const w = new Float64Array(n);
    for (let i = 0; i < n; i++) w[i] = 0.5 - 0.5 * Math.cos((2 * Math.PI * i) / (n - 1));
    return w;
  }

  // --- Minimal iterative radix-2 FFT (real input -> magnitude spectrum) ---
  // Requires power-of-two length. We size analysis windows to the nearest
  // power of two to TONE_DURATION worth of samples (see UltraReceiver).
  function fftMagnitudes(real) {
    const n = real.length;
    const re = Float64Array.from(real);
    const im = new Float64Array(n);

    for (let i = 1, j = 0; i < n; i++) {
      let bit = n >> 1;
      for (; j & bit; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        [re[i], re[j]] = [re[j], re[i]];
        [im[i], im[j]] = [im[j], im[i]];
      }
    }

    for (let len = 2; len <= n; len <<= 1) {
      const ang = (-2 * Math.PI) / len;
      const wr = Math.cos(ang);
      const wi = Math.sin(ang);
      for (let i = 0; i < n; i += len) {
        let curWr = 1;
        let curWi = 0;
        for (let k = 0; k < len / 2; k++) {
          const uRe = re[i + k];
          const uIm = im[i + k];
          const vRe = re[i + k + len / 2] * curWr - im[i + k + len / 2] * curWi;
          const vIm = re[i + k + len / 2] * curWi + im[i + k + len / 2] * curWr;
          re[i + k] = uRe + vRe;
          im[i + k] = uIm + vIm;
          re[i + k + len / 2] = uRe - vRe;
          im[i + k + len / 2] = uIm - vIm;
          const nWr = curWr * wr - curWi * wi;
          const nWi = curWr * wi + curWi * wr;
          curWr = nWr;
          curWi = nWi;
        }
      }
    }

    const mags = new Float64Array(n / 2 + 1);
    for (let i = 0; i <= n / 2; i++) mags[i] = Math.hypot(re[i], im[i]);
    return mags;
  }

  function makeTone(fLow, fHigh, len, sr, window) {
    const out = new Float32Array(len);
    for (let i = 0; i < len; i++) {
      const t = i / sr;
      out[i] = (Math.sin(2 * Math.PI * fLow * t) + Math.sin(2 * Math.PI * fHigh * t)) * 0.5 * window[i];
    }
    return out;
  }

  function calculateMostCommon(rawChars) {
    // FIX B from the Python version: require a whole decoded chunk to
    // repeat identically at least twice before trusting it.
    const rawString = rawChars.join('');
    const chunks = rawString.split(' ').map((c) => c.trim()).filter(Boolean);
    if (chunks.length === 0) return '';
    const counts = new Map();
    for (const c of chunks) counts.set(c, (counts.get(c) || 0) + 1);
    let best = null;
    let bestFreq = 0;
    for (const [chunk, freq] of counts) {
      if (freq > bestFreq) { best = chunk; bestFreq = freq; }
    }
    if (best && bestFreq >= 2 && best.length >= 4) return best;
    return '';
  }

  class UltraSender {
    constructor(audioCtx) {
      this.audioCtx = audioCtx;
    }

    /** Mirrors UltraSender.transmit(text): 3 repeat rounds, phase-shift anti-blur. */
    async transmit(text) {
      const sr = this.audioCtx.sampleRate;
      const toneLen = Math.floor(sr * TONE_DURATION);
      const gapLen = Math.floor(sr * GAP_DURATION);
      const roundGapLen = Math.floor(sr * ROUND_GAP);
      const window = hann(toneLen);

      const chunks = [];
      for (let round = 0; round < 3; round++) {
        let phaseInverted = false;
        for (const ch of text) {
          const asciiVal = ch.charCodeAt(0);
          if (asciiVal >= 128 || !(asciiVal in FREQ_GRID)) continue;
          let [fLow, fHigh] = FREQ_GRID[asciiVal];
          if (phaseInverted) fHigh += PHASE_SHIFT_OFFSET;
          chunks.push(makeTone(fLow, fHigh, toneLen, sr, window));
          chunks.push(new Float32Array(gapLen));
          phaseInverted = !phaseInverted;
        }
        const spaceVal = ' '.charCodeAt(0);
        let [fLow, fHigh] = FREQ_GRID[spaceVal];
        if (phaseInverted) fHigh += PHASE_SHIFT_OFFSET;
        chunks.push(makeTone(fLow, fHigh, toneLen, sr, window));
        chunks.push(new Float32Array(gapLen));
        if (round < 2) chunks.push(new Float32Array(roundGapLen));
      }
      chunks.push(new Float32Array(2048));

      const total = chunks.reduce((a, c) => a + c.length, 0);
      const full = new Float32Array(total);
      let off = 0;
      for (const c of chunks) { full.set(c, off); off += c.length; }

      const buffer = this.audioCtx.createBuffer(1, full.length, sr);
      buffer.copyToChannel(full, 0);

      return new Promise((resolve) => {
        const src = this.audioCtx.createBufferSource();
        src.buffer = buffer;
        src.connect(this.audioCtx.destination);
        src.onended = () => resolve();
        src.start();
      });
    }
  }

  class UltraReceiver {
    constructor(audioCtx, stream) {
      this.audioCtx = audioCtx;
      this.stream = stream;
      this._stopped = false;
    }

    stop() {
      this._stopped = true;
    }

    /**
     * Mirrors UltraReceiver.listen(single_shot=True, timeout=...): sliding
     * FFT window, dual-tone + SNR detection, majority-vote on silence gap.
     * Resolves with the decoded string, or null on stop/timeout.
     */
    listen({ timeoutMs = null } = {}) {
      return new Promise((resolve) => {
        const sr = this.audioCtx.sampleRate;
        const idealBlockSize = Math.floor(sr * TONE_DURATION);
        const blockSize = Math.pow(2, Math.round(Math.log2(idealBlockSize))); // nearest power of two
        const hopSize = Math.max(1, blockSize >> 1);

        const source = this.audioCtx.createMediaStreamSource(this.stream);
        const processor = this.audioCtx.createScriptProcessor(hopSize, 1, 1);
        const mute = this.audioCtx.createGain();
        mute.gain.value = 0; // never actually play the mic back out
        source.connect(processor);
        processor.connect(mute);
        mute.connect(this.audioCtx.destination);

        let rollingBuffer = new Float32Array(0);
        let receivedChars = [];
        let lastGridPair = null;
        let silentCycles = 0;
        const startTime = this.audioCtx.currentTime;
        let finished = false;

        const cleanup = () => {
          if (finished) return;
          finished = true;
          try { processor.disconnect(); } catch (e) { /* noop */ }
          try { source.disconnect(); } catch (e) { /* noop */ }
          try { mute.disconnect(); } catch (e) { /* noop */ }
        };
        const finish = (result) => { cleanup(); resolve(result); };

        processor.onaudioprocess = (e) => {
          if (finished) return;
          if (this._stopped) { finish(null); return; }
          if (timeoutMs !== null && (this.audioCtx.currentTime - startTime) * 1000 > timeoutMs) {
            finish(null);
            return;
          }

          const hop = e.inputBuffer.getChannelData(0);
          const merged = new Float32Array(rollingBuffer.length + hop.length);
          merged.set(rollingBuffer, 0);
          merged.set(hop, rollingBuffer.length);
          rollingBuffer = merged.length > blockSize ? merged.slice(merged.length - blockSize) : merged;
          if (rollingBuffer.length < blockSize) return;

          const mags = fftMagnitudes(rollingBuffer);
          const freqRes = sr / blockSize;

          const lowLo = Math.max(0, Math.round(14500 / freqRes));
          const lowHi = Math.min(mags.length - 1, Math.round(17200 / freqRes));
          const highLo = Math.max(0, Math.round(17300 / freqRes));
          const highHi = Math.min(mags.length - 1, Math.round(19800 / freqRes));

          let maxLowIdx = -1, maxLowVal = -Infinity, lowSum = 0, lowCount = 0;
          for (let i = lowLo; i <= lowHi; i++) {
            lowSum += mags[i]; lowCount++;
            if (mags[i] > maxLowVal) { maxLowVal = mags[i]; maxLowIdx = i; }
          }
          let maxHighIdx = -1, maxHighVal = -Infinity, highSum = 0, highCount = 0;
          for (let i = highLo; i <= highHi; i++) {
            highSum += mags[i]; highCount++;
            if (mags[i] > maxHighVal) { maxHighVal = mags[i]; maxHighIdx = i; }
          }
          if (maxLowIdx === -1 || maxHighIdx === -1 || lowCount === 0 || highCount === 0) return;

          const peakLowFreq = maxLowIdx * freqRes;
          const peakHighFreq = maxHighIdx * freqRes;
          const lowNoiseFloor = lowSum / lowCount;
          const highNoiseFloor = highSum / highCount;

          if (maxLowVal > lowNoiseFloor * SNR_THRESHOLD && maxHighVal > highNoiseFloor * SNR_THRESHOLD) {
            const matched = findClosestGridTones(peakLowFreq, peakHighFreq);
            if (matched) {
              if (silentCycles > 0) lastGridPair = null;
              if (!lastGridPair || lastGridPair[0] !== matched[0] || lastGridPair[1] !== matched[1]) {
                const key = `${matched[0]}|${matched[1]}`;
                const resolvedAscii = REV_GRID.get(key);
                if (resolvedAscii !== undefined && (resolvedAscii >= 32 || [9, 10, 13].includes(resolvedAscii))) {
                  receivedChars.push(String.fromCharCode(resolvedAscii));
                }
                lastGridPair = matched;
              }
              silentCycles = 0;
            }
          } else {
            silentCycles++;
            if (receivedChars.length > 0 && silentCycles > 5) {
              const rawList = receivedChars;
              receivedChars = [];
              lastGridPair = null;
              silentCycles = 0;
              const voted = calculateMostCommon(rawList);
              if (voted) finish(voted);
            }
          }
        };
      });
    }
  }

  return { UltraSender, UltraReceiver, ACK_PREFIX, FREQ_GRID };
})();
