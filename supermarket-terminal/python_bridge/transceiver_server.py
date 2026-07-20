#!/usr/bin/env python3
"""
transceiver_server.py

This wraps your ultrasonic transceiver script in a tiny local HTTP API so the
browser UI can drive it, instead of the interactive CLI menu. It does NOT
reimplement your protocol — everything between the "ORIGINAL PROTOCOL CODE"
markers below is your script, unchanged: same SAMPLE_RATE, same FREQ_GRID
construction, same UltraSender.transmit(), same UltraReceiver.listen() decode
loop (FFT bands, SNR_THRESHOLD, phase-shift matching, majority vote).

The only addition to UltraReceiver.listen() is a cooperative `stop_event`
check inside the existing while-loop, so the web UI's "Stop Listening"
button can interrupt it — your original relied on KeyboardInterrupt, which
isn't deliverable to a background thread. That's the one necessary hook;
no detection/decoding logic is touched.

Everything below "WEB API WRAPPER" is new: it's the same orchestration your
main_menu() choice "1" does (listen -> echo back -> listen again -> confirm
on ACK), just exposed over HTTP/SSE instead of print()/input().
"""

import os
import sys
import subprocess

# --- AUTOMATIC INTERNAL VIRTUAL ENVIRONMENT LIFECYCLE (unchanged from your script) ---
VENV_DIR = os.path.join(os.path.expanduser("~"), ".ultrasonic_env")
VENV_PYTHON = os.path.join(VENV_DIR, "bin", "python")

if sys.executable != VENV_PYTHON:
    print("==================================================")
    print("INITIALIZING ISOLATED PYTHON RUNTIME...")
    print("==================================================")

    if not os.path.exists(VENV_DIR):
        print("Creating virtual environment structure...")
        subprocess.run([sys.executable, "-m", "venv", VENV_DIR], check=True)

        print("Installing dependencies (numpy, sounddevice, flask, flask-cors)...")
        subprocess.run([VENV_PYTHON, "-m", "pip", "install", "--upgrade", "pip"], stdout=subprocess.DEVNULL)
        subprocess.run(
            [VENV_PYTHON, "-m", "pip", "install", "numpy", "sounddevice", "flask", "flask-cors"],
            stdout=subprocess.DEVNULL,
        )

    print("Spawning process inside the verified environment...")
    print("==================================================\n")

    os.execv(VENV_PYTHON, [VENV_PYTHON] + sys.argv)


# ============================================================
# ORIGINAL PROTOCOL CODE — copied verbatim from your script.
# Do not "improve" or refactor anything in this section; it's
# what keeps this compatible with your existing sender/receiver.
# ============================================================

import numpy as np
import sounddevice as sd
import queue
import time
from collections import Counter

SAMPLE_RATE = 44100
TONE_DURATION = 0.024
GAP_DURATION = 0.005

LOW_ROW_FREQS = [15000, 15200, 15400, 15600, 15800, 16000, 16200, 16400]
HIGH_COL_FREQS = [17500, 17700, 17900, 18100, 18300, 18500, 18700, 18900]

PHASE_SHIFT_OFFSET = 300
SNR_THRESHOLD = 3.2

ACK_PREFIX = "#"

FREQ_GRID = {}
REV_GRID = {}

idx = 0
for r_freq in LOW_ROW_FREQS:
    for c_freq in HIGH_COL_FREQS:
        if idx < 128:
            FREQ_GRID[idx] = (r_freq, c_freq)
            REV_GRID[(r_freq, c_freq)] = idx
            REV_GRID[(r_freq, c_freq + PHASE_SHIFT_OFFSET)] = idx
            idx += 1


def find_closest_grid_tones(peak_low, peak_high):
    closest_low = min(LOW_ROW_FREQS, key=lambda x: abs(x - peak_low))
    possible_highs = HIGH_COL_FREQS + [f + PHASE_SHIFT_OFFSET for f in HIGH_COL_FREQS]
    closest_high = min(possible_highs, key=lambda x: abs(x - peak_high))

    if abs(closest_low - peak_low) < 40 and abs(closest_high - peak_high) < 40:
        return (closest_low, closest_high)
    return None


class UltraSender:
    def transmit(self, text):
        time.sleep(0.3)
        full_signal = []
        t = np.linspace(0, TONE_DURATION, int(SAMPLE_RATE * TONE_DURATION), endpoint=False)
        window = np.hanning(len(t))

        for round_num in range(3):
            is_phase_inverted = False

            for char in text:
                ascii_val = ord(char)
                if ascii_val >= 128:
                    continue

                freq_low, freq_high = FREQ_GRID[ascii_val]

                if is_phase_inverted:
                    freq_high += PHASE_SHIFT_OFFSET

                tone = (np.sin(2 * np.pi * freq_low * t) + np.sin(2 * np.pi * freq_high * t)) * 0.5 * window
                full_signal.append(tone)
                full_signal.append(np.zeros(int(SAMPLE_RATE * GAP_DURATION)))

                is_phase_inverted = not is_phase_inverted

            space_val = ord(" ")
            f_low, f_high = FREQ_GRID[space_val]
            if is_phase_inverted:
                f_high += PHASE_SHIFT_OFFSET

            space_tone = (np.sin(2 * np.pi * f_low * t) + np.sin(2 * np.pi * f_high * t)) * 0.5 * window
            full_signal.append(space_tone)
            full_signal.append(np.zeros(int(SAMPLE_RATE * GAP_DURATION)))

            if round_num < 2:
                full_signal.append(np.zeros(int(SAMPLE_RATE * 0.020)))

        full_signal.append(np.zeros(2048))
        audio_data = np.concatenate(full_signal).astype(np.float32)

        try:
            sd.play(audio_data, SAMPLE_RATE)
            sd.wait()
        except Exception as e:
            print(f"Hardware Error on playback: {e}")


class UltraReceiver:
    def __init__(self):
        self.audio_queue = queue.Queue()

    def _callback(self, indata, frames, time_info, status):
        self.audio_queue.put(indata.copy())

    def calculate_most_common(self, raw_chars):
        raw_string = "".join(raw_chars)
        chunks = [c.strip() for c in raw_string.split(" ") if c.strip()]
        if not chunks:
            return ""

        counts = Counter(chunks)
        best_chunk, freq = counts.most_common(1)[0]

        if freq >= 2 and len(best_chunk) >= 4:
            return best_chunk

        return ""

    def listen(self, single_shot=False, timeout=None, stop_event=None):
        # NOTE: `stop_event` is the one addition vs. your original — a
        # threading.Event checked alongside the existing timeout check, so
        # a background thread can be cancelled cooperatively. Everything
        # else in this method is unchanged.
        if not single_shot:
            print("\nReceiver streaming live. Parallel Quad-Stream Evaluation Active...")
            print("Press Ctrl+C to exit back to menu.")

        block_size = int(SAMPLE_RATE * TONE_DURATION)
        hop_size = max(1, block_size // 2)

        while not self.audio_queue.empty():
            self.audio_queue.get()

        stream = sd.InputStream(samplerate=SAMPLE_RATE, channels=1, blocksize=hop_size, callback=self._callback)

        received_chars = []
        last_grid_pair = None
        silent_cycles = 0
        timeout_start = time.time()

        rolling_buffer = np.zeros(0, dtype=np.float32)

        with stream:
            while True:
                if stop_event is not None and stop_event.is_set():
                    return None
                if timeout and (time.time() - timeout_start > timeout):
                    return None

                try:
                    hop = self.audio_queue.get(timeout=0.1).flatten()

                    rolling_buffer = np.concatenate([rolling_buffer, hop])
                    if len(rolling_buffer) < block_size:
                        continue
                    if len(rolling_buffer) > block_size:
                        rolling_buffer = rolling_buffer[-block_size:]

                    block = rolling_buffer

                    fft_data = np.abs(np.fft.rfft(block))
                    freqs = np.fft.rfftfreq(len(block), 1 / SAMPLE_RATE)

                    low_band_idx = np.where((freqs >= 14500) & (freqs <= 17200))[0]
                    high_band_idx = np.where((freqs >= 17300) & (freqs <= 19800))[0]

                    if len(low_band_idx) == 0 or len(high_band_idx) == 0:
                        continue

                    max_low_idx = np.argmax(fft_data[low_band_idx])
                    max_high_idx = np.argmax(fft_data[high_band_idx])

                    peak_low_freq = freqs[low_band_idx[max_low_idx]]
                    peak_high_freq = freqs[high_band_idx[max_high_idx]]

                    low_magnitude = fft_data[low_band_idx[max_low_idx]]
                    high_magnitude = fft_data[high_band_idx[max_high_idx]]

                    low_noise_floor = np.mean(fft_data[low_band_idx]) if len(low_band_idx) > 0 else 1.0
                    high_noise_floor = np.mean(fft_data[high_band_idx]) if len(high_band_idx) > 0 else 1.0

                    if low_magnitude > (low_noise_floor * SNR_THRESHOLD) and high_magnitude > (high_noise_floor * SNR_THRESHOLD):
                        matched_pair = find_closest_grid_tones(peak_low_freq, peak_high_freq)

                        if matched_pair is not None:
                            if silent_cycles > 0:
                                last_grid_pair = None

                            if matched_pair != last_grid_pair:
                                resolved_ascii = REV_GRID[matched_pair]
                                if resolved_ascii >= 32 or resolved_ascii in [9, 10, 13]:
                                    received_chars.append(chr(resolved_ascii))
                                last_grid_pair = matched_pair
                            silent_cycles = 0
                    else:
                        silent_cycles += 1
                        if received_chars and silent_cycles > 5:
                            raw_list = list(received_chars)

                            received_chars = []
                            last_grid_pair = None
                            silent_cycles = 0

                            if raw_list:
                                voted_output = self.calculate_most_common(raw_list)
                                if voted_output:
                                    print(f"RECEIVED RAW (MOST COMMON VALUE): {voted_output}")
                                    if single_shot:
                                        return voted_output
                except queue.Empty:
                    continue
                except KeyboardInterrupt:
                    return None

# ============================================================
# END ORIGINAL PROTOCOL CODE
# ============================================================


# ============================================================
# WEB API WRAPPER — new code. Mirrors main_menu() choice "1"'s
# loop (listen -> echo -> listen for ACK -> confirm), exposed
# over HTTP/SSE for the browser instead of a CLI menu.
# ============================================================

import threading
import json
from flask import Flask, request, Response, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)  # the Node frontend (localhost:4000) calls this from a different port

sender = UltraSender()
receiver = UltraReceiver()

session_lock = threading.Lock()
session_thread = None
session_stop_event = None
session_events = queue.Queue()
session_state = {"running": False, "confirmedNumber": None}


def run_receive_session(stop_event):
    """Same orchestration as main_menu()'s choice '1' loop."""
    session_events.put({"type": "status", "value": "Awaiting incoming signal..."})
    try:
        while not stop_event.is_set():
            msg = receiver.listen(single_shot=True, stop_event=stop_event)
            if stop_event.is_set():
                return
            if not msg:
                continue

            if msg.startswith(ACK_PREFIX):
                confirmed_number = msg[len(ACK_PREFIX):]
                session_state["confirmedNumber"] = confirmed_number
                session_events.put({"type": "confirmed", "value": confirmed_number})
                return

            session_events.put({"type": "candidate", "value": msg})
            session_events.put({"type": "status", "value": f'Echoing "{msg}" back for verification...'})
            sender.transmit(msg)
            session_events.put({"type": "status", "value": "Echoed. Waiting for sender confirmation..."})
    finally:
        with session_lock:
            session_state["running"] = False
        session_events.put({"type": "stopped"})


@app.route("/api/start-listen", methods=["POST"])
def start_listen():
    global session_thread, session_stop_event
    with session_lock:
        if session_state["running"]:
            return jsonify({"ok": False, "error": "Already listening"}), 409
        session_state["running"] = True
        session_state["confirmedNumber"] = None
        session_stop_event = threading.Event()
        session_thread = threading.Thread(
            target=run_receive_session, args=(session_stop_event,), daemon=True
        )
        session_thread.start()
    return jsonify({"ok": True})


@app.route("/api/stop-listen", methods=["POST"])
def stop_listen():
    with session_lock:
        if session_stop_event is not None:
            session_stop_event.set()
        session_state["running"] = False
    return jsonify({"ok": True})


@app.route("/api/status", methods=["GET"])
def status():
    with session_lock:
        return jsonify(dict(session_state))


@app.route("/api/stream")
def stream():
    def event_stream():
        while True:
            try:
                event = session_events.get(timeout=15)
                yield f"data: {json.dumps(event)}\n\n"
            except queue.Empty:
                yield ": keep-alive\n\n"

    return Response(event_stream(), mimetype="text/event-stream")


if __name__ == "__main__":
    port = int(os.environ.get("TRANSCEIVER_PORT", 5005))
    print(f"Ultrasonic transceiver bridge running on http://localhost:{port}")
    app.run(host="0.0.0.0", port=port, threaded=True)
