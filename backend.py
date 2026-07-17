#!/usr/bin/env python3
import os
import sys
import subprocess

# --- AUTOMATIC INTERNAL VIRTUAL ENVIRONMENT LIFECYCLE ---
VENV_DIR = os.path.join(os.path.expanduser("~"), ".ultrasonic_env")
VENV_PYTHON = os.path.join(VENV_DIR, "bin", "python")

if sys.executable != VENV_PYTHON:
    print("==================================================")
    print("INITIALIZING ISOLATED PYTHON RUNTIME...")
    print("==================================================")

    if not os.path.exists(VENV_DIR):
        print("Creating virtual environment structure...")
        subprocess.run([sys.executable, "-m", "venv", VENV_DIR], check=True)

        print("Installing dependencies (numpy, sounddevice)...")
        subprocess.run([VENV_PYTHON, "-m", "pip", "install", "--upgrade", "pip"], stdout=subprocess.DEVNULL)
        subprocess.run([VENV_PYTHON, "-m", "pip", "install", "numpy", "sounddevice"], stdout=subprocess.DEVNULL)

    print("Spawning process inside the verified environment...")
    print("==================================================\n")

    os.execv(VENV_PYTHON, [VENV_PYTHON] + sys.argv)


# --- ACTUAL APP CODE (Only runs once safely inside the venv) ---
import numpy as np
import sounddevice as sd
import queue
import time
from collections import Counter

try:
    import readline
except ImportError:
    pass

# --- CONFIGURATION (ANTI-BLUR PHASE ENGINE) ---
SAMPLE_RATE = 44100
TONE_DURATION = 0.024
GAP_DURATION = 0.005

LOW_ROW_FREQS = [15000, 15200, 15400, 15600, 15800, 16000, 16200, 16400]
HIGH_COL_FREQS = [17500, 17700, 17900, 18100, 18300, 18500, 18700, 18900]

PHASE_SHIFT_OFFSET = 300
SNR_THRESHOLD = 3.2

# Marker sent by the SENDER, after it verifies the echo matches, to tell
# the RECEIVER the round trip is fully confirmed. Must use a character
# with ASCII code < 64 (see FREQ_GRID note below) -- "#" is ASCII 35.
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


# --- SENDER ENGINE ---
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


# --- RECEIVER ENGINE ---
class UltraReceiver:
    def __init__(self):
        self.audio_queue = queue.Queue()

    def _callback(self, indata, frames, time_info, status):
        self.audio_queue.put(indata.copy())

    def calculate_most_common(self, raw_chars):
        """
        FIX B: Instead of voting per-character-index across chunks of the
        'most common length' (which silently assembles a wrong-length
        Frankenstein string when block misalignment causes insertions/
        deletions), we now require an *entire* decoded chunk to repeat
        identically at least twice before we trust it. This avoids
        confidently returning a plausible-looking but incorrect string.
        """
        raw_string = "".join(raw_chars)
        chunks = [c.strip() for c in raw_string.split(" ") if c.strip()]
        if not chunks:
            return ""

        counts = Counter(chunks)
        best_chunk, freq = counts.most_common(1)[0]

        if freq >= 2 and len(best_chunk) >= 4:
            return best_chunk

        return ""

    def listen(self, single_shot=False, timeout=None):
        if not single_shot:
            print("\nReceiver streaming live. Parallel Quad-Stream Evaluation Active...")
            print("Press Ctrl+C to exit back to menu.")

        block_size = int(SAMPLE_RATE * TONE_DURATION)

        # FIX A: use a smaller callback chunk (hop) and a sliding analysis
        # window (block) so a tone can never fall entirely inside a
        # callback boundary and get blurred/missed. Overlap = 50%.
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
                if timeout and (time.time() - timeout_start > timeout):
                    return None

                try:
                    hop = self.audio_queue.get(timeout=0.1).flatten()

                    # Slide the analysis window forward by one hop, keeping
                    # the buffer at exactly block_size samples so every FFT
                    # sees a full, overlapping view of the audio.
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


# --- INTERACTIVE INTERFACE ---
def main_menu():
    sender = UltraSender()
    receiver = UltraReceiver()

    while True:
        print("=" * 50)
        print("      ULTRASONIC TRANSCEIVER SYSTEM v14.0")
        print("=" * 50)
        print("1) Receive (Listen for data)")
        print("2) Send (Broadcast a message)")
        print("3) Exit")
        print("=" * 50)

        choice = input("Select an option (1-3): ").strip()

        if choice == "1":
            print("\n[Active] Awaiting incoming signals... Press Ctrl+C to stop.")
            while True:
                try:
                    msg = receiver.listen(single_shot=True)
                    if msg:
                        # ACK_PREFIX handling: the sender sends this back
                        # ONLY after it has verified the echo matched the
                        # original number. Receiving it here means the
                        # round trip is fully confirmed on both ends.
                        # NOTE: "#" (ASCII 35) is used instead of the word
                        # "SUCCESS" because FREQ_GRID only covers ASCII
                        # codes 0-63 -- uppercase letters (65+) aren't in
                        # the grid and would crash with a KeyError.
                        if msg.startswith(ACK_PREFIX):
                            confirmed_number = msg[len(ACK_PREFIX):]
                            print(f"==========================================")
                            print(f"SUCCESS: The number is {confirmed_number}")
                            print(f"==========================================\n")
                            continue

                        print(f"[*] Echoing '{msg}' back to sender for verification...")
                        sender.transmit(msg)
                        print(f"[*] Echoed '{msg}'. Awaiting sender confirmation...\n")
                except KeyboardInterrupt:
                    print("\nStopped.")
                    break

        elif choice == "2":
            print("\nEntering persistent sender loop. Type 'exit' to go back.\n")
            while True:
                msg = input("Send data > ").strip()
                if msg.lower() == "exit":
                    break
                if msg:
                    attempt = 1
                    while True:
                        try:
                            print(f"[*] Transmitting data stream (Attempt {attempt})...")
                            sender.transmit(msg)

                            print("[*] Listening for returned echo bounce...")
                            verified_echo = receiver.listen(single_shot=True, timeout=4.5)

                            if verified_echo == msg:
                                print(f"\n==========================================")
                                print(f"SUCCESS: The number is {verified_echo}")
                                print(f"==========================================\n")

                                # Tell the receiver the round trip is
                                # confirmed so it can print its own
                                # SUCCESS instead of just sitting there
                                # having echoed blind.
                                print(f"[*] Sending confirmation ACK to receiver...")
                                sender.transmit(f"{ACK_PREFIX}{msg}")
                                break
                            else:
                                print(f"===============> MISMATCH/TIMEOUT: Got '{verified_echo}'")
                                print("[!] Cleardown window active. Preparing retransmission...")
                                attempt += 1
                                time.sleep(0.4)

                        except Exception as e:
                            print(f"Error handling transceiver state: {e}")
                            break
                else:
                    print("Cannot send empty buffer.")

        elif choice == "3":
            print("\nGoodbye!")
            break
        else:
            print("Invalid choice.\n")
            time.sleep(1)

if __name__ == "__main__":
    main_menu()