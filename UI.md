# Ultrasonic 3D App — UI Specification

A neumorphic (soft-UI), dark-mode mobile app interface for managing and "sending" phone number profiles (e.g. mobile money payment numbers), with a simulated ultrasonic transmission flow.

---

## 1. Overall Frame

- **Device mockup**: a fixed-size phone frame, `375px × 812px` (iPhone-X-style dimensions), centered on the page with a dark backdrop.
- **Corner radius**: `40px`, giving it a rounded phone-body silhouette.
- **Shadow style**: dual soft shadow (light top-left `rgba(255,255,255,0.02)`, dark bottom-right `rgba(0,0,0,0.8)`) — a classic **neumorphic "extruded" look** used throughout the whole app.
- **Overflow**: hidden, so all inner scroll areas and view transitions stay clipped inside the frame.

---

## 2. Design System

### Color Palette
| Token | Hex/Value | Usage |
|---|---|---|
| `--base` | `#1a1a1a` | Global background, card background (matches container so cards look "carved" into the surface) |
| `--shadow-light` | `#282828` | Lighter neumorphic shadow edge |
| `--shadow-dark` | `#0c0c0c` | Darker neumorphic shadow edge |
| `--cyan` | `#0dfcac` | Primary accent — active states, headers, icons, glow effects |
| `--success-green` | `#39ff9c` | Success/confirmation states (distinct from accent cyan) |
| `--text-main` | `#ffffff` | Primary text |
| `--text-dim` | `#767676` | Secondary/inactive text and icons |
| `--bubble-bg` | `#232323` | Message bubble background in the received log |

### Typography
- **Share Tech Mono** — used for numeric displays, headers, and anything meant to feel "terminal/digital" (phone number display, card titles, log bubbles, modal titles, user name).
- **Nunito** (400/600/700/800) — general UI text, labels, body copy.
- **Fira Code** (300/400) — the small terminal status line ("Waiting for numbers...").
- **Font Awesome 6.4.0** (CDN) — all iconography.

### Signature Visual Language
Neumorphism is applied consistently: every raised element (cards, inputs, chips, avatar, toggle, nav button) uses the same background as its parent with a paired light/dark drop-shadow to simulate embossing. Pressed/active elements switch to **inset** shadows to look "pushed in."

---

## 3. Screen Structure

The app has **two primary views** that occupy the same screen region and cross-fade/slide between each other, plus a persistent bottom nav and three modal overlays.

### 3.1 Home View (`#homeView`)
Default/active view. Contains, top to bottom:

1. **Profile Card** (neumorphic card, rounded 30px)
   - **Header row**: current profile type label (e.g. "Paybill") in small uppercase cyan monospace text on the left, a "+" icon button on the right to open the Add Profile modal.
   - **Phone number display**: large (38px), bold, letter-spaced monospace number with a heavy multi-layer text-shadow that gives it an embossed/glowing metallic look. This updates to reflect whichever profile is centered in the carousel below.
   - **Profile carousel strip**: a horizontally scrollable row of circular icon "chips," one per saved profile.
     - Uses CSS scroll-snap (`snap-align: center`) so one icon always centers itself.
     - Inactive icons: 50×50px, dim gray, inset shadow (recessed look).
     - The **centered/active** icon animates up to 70×70px, turns solid cyan with dark icon color, and drops the inset shadow for a raised, "selected" look.
     - Scrolling the strip recalculates which icon is nearest the center (`updateActiveIcon()`) and syncs the phone-number display + type label accordingly.

2. **Manual Input Field** (below the card)
   - A recessed (inset-shadow) text input: "Enter temporary number..."
   - A paper-plane send icon sits inside the input on the right, hidden by default (`opacity: 0`).
   - As soon as text is typed, the input wrapper gains a cyan border + soft cyan glow, and the send icon fades in and becomes clickable.
   - Clicking send: pushes a "> Sent: {number}" message to the terminal status line, clears the input, plays a beep, and resets the wrapper state.

3. **Received Section** (fills remaining vertical space, own internal scroll)
   - Header row: "RECEIVED" label (dimmed, letter-spaced, uppercase) + a trash-can icon to clear the log (turns red on hover).
   - **Terminal status line** (`#terminalText`, Fira Code font): shows live status messages such as:
     - `> Waiting for numbers...` (idle/default)
     - `> Sending…` → `> Confirming…` → `> Signal received!` (long-press flow)
     - `> Sent: {number}` (quick tap / manual send)
     - `> Cancelled.` (long press released early)
   - **Log list** (`#receivedLog`): a vertically scrolling, gap-8px stack of **message bubbles**, newest at the bottom (auto-scrolls to bottom on new entry).

#### Message Bubble (Log Entry) Anatomy
Each received entry renders as a chat-style bubble:
- Rounded corners (16px, flattened bottom-left corner for a "tail" effect), left border accent stripe in cyan (3px).
- Background `--bubble-bg`, soft neumorphic shadow.
- Contents, left to right:
  1. Envelope icon (dim cyan, small)
  2. The received number (monospace, flex-grow so it fills space)
  3. Timestamp (small, dim, `Nunito`, e.g. "3:42 PM")
  4. **Copy button** — a small square icon button (copy icon) with subtle border.
- **Entrance animation**: `bubbleIn` — slides in from the left with a slight scale-up and fade (0.3s ease-out).
- **Copy interaction**: clicking the copy button copies the number to clipboard (via `navigator.clipboard`, with a `document.execCommand` fallback for unsupported browsers). On success, the icon swaps to a checkmark and the button turns green (`--success-green`) for 1.5 seconds before reverting.

### 3.2 Profiles View (`#profilesView`)
Hidden by default; slides in from the right when the user taps the Profiles tab.

1. **My Profile card**
   - Header: "MY PROFILE" title + gear icon (opens Settings modal).
   - Centered account block: circular avatar (astronaut icon, cyan, inset neumorphic shell), bold monospace username ("User_992"), and a small uppercase "SECURE CONNECTION" caption in dim gray.

2. **"Active Profiles" section header** (plain white monospace text).

3. **Profile list** — one **Profile Card** per saved number:
   - Left: colored icon representing the payment type (wallet, shop, money-bill-transfer, or mobile icon depending on type).
   - Middle: the number (bold monospace) and its type label underneath (dim gray, small).
   - Right: **edit** (pencil) and **delete** (trash) icon buttons.
     - Edit opens the Add/Edit modal pre-filled with that profile's data.
     - Delete asks for a native `confirm()` before removing the profile; if the deleted profile was active, the app auto-selects the first remaining profile.

**Profile "types"** (mapped to icons):
| Type | Icon |
|---|---|
| Send Money | wallet |
| Till | shop |
| Paybill | money-bill-transfer |
| Pochi | mobile-screen-button |

---

## 4. Bottom Navigation

A floating, transparent nav bar fixed near the bottom of the frame (30px from bottom), containing three elements evenly spaced:

1. **Home tab** (house icon) — left.
2. **Center Send Button** — the star of the interface:
   - A large pill-shaped button (85×60px, 20px radius), raised neumorphic style with a cyan paper-plane icon.
   - **Quick tap**: triggers a "sending wave" animation (two expanding ring pulses emanating from the button, scaling horizontally to mimic an ultrasonic pulse), shows "> Sent: {number}" in the terminal, and briefly turns the button green with a glow.
   - **Long press (500ms+ hold)**: enters a "processing" state —
     - The two side nav icons slide outward (~35px each) to make room, and continuous looping wave-ring animations play around the center button.
     - Terminal text progresses: `Sending…` (immediate) → `Confirming…` (at 1.5s) → `Signal received!` (at 3s), at which point the number is pushed into the Received log, a beep sound plays, and the button flashes green with a glow for 2 seconds.
     - **Releasing early** (before the 3s mark) cancels the sequence, resets nav icon positions, and shows `> Cancelled.` briefly before returning to idle text.
   - Both mouse and touch events are handled (`mousedown/mouseup/mouseleave`, `touchstart/touchend`).
3. **Profiles tab** (user icon) — right.

Active tab icon (Home or Profiles) is highlighted in cyan with a subtle glow (`text-shadow`).

---

## 5. Modals

All modals share the same treatment: full-frame dark overlay (`rgba(0,0,0,0.8)`) with backdrop blur, and a centered rounded content panel that scales/slides in with a bouncy cubic-bezier easing when shown, and shrinks/fades when dismissed. Clicking the overlay background (outside the panel) closes the modal.

### 5.1 Add / Edit Profile Modal
- Title switches between "Add New Profile" and "Edit Profile" depending on context.
- A single text input for the number (recessed neumorphic style).
- A "Type" label followed by **four selectable chips**: Send Money, Till, Paybill, Pochi.
  - Chips are pill-shaped, dim/gray by default with a soft raised shadow; the selected chip turns solid cyan with bold dark text and no shadow.
  - Only one chip can be active at a time.
- Footer actions: a plain-text "Cancel" button (dim) and a solid cyan "Save" button (dark bold text, raised shadow).
- Saving validates that the number field isn't empty, then either updates the existing profile (by `editingId`) or appends a new one, re-renders the carousel/list, and auto-scrolls the carousel to the new/edited profile.

### 5.2 Settings Modal
- Title: "Settings."
- A single settings row: **"Sending Sound"** label with a toggle switch on the right.
  - Toggle is a horizontal pill (44×24px) with a sliding white circle knob; active state turns the track cyan and slides the knob to the right.
  - Controls whether the beep sound effect plays on send/receive events.
- A "Privacy Policy" link (cyan, underlined) below the setting, which opens the Privacy modal.
- Footer: "Close" button.

### 5.3 Privacy Policy Modal
- Title: "Privacy Policy."
- Scrollable text block (max-height 300px, custom thin cyan scrollbar) containing the policy statement and a "Last updated" line.
- Footer: "Close" button.

---

## 6. Interaction & Motion Summary

| Interaction | Effect |
|---|---|
| Scroll profile carousel | Nearest-to-center icon enlarges + turns cyan; number display & type label update live |
| Tap send button (quick) | Two-ring outward wave pulse, terminal shows "Sent:", button glows green briefly |
| Hold send button (≥0.5s) | Nav icons part sideways, continuous wave rings, staged terminal messages, culminates in a new log entry + green glow + beep |
| Release hold early | Immediately cancels, shows "Cancelled." |
| Type in manual input | Border/glow appears, inline send icon fades in |
| Tap copy on a log bubble | Clipboard copy, icon → checkmark, button turns green for 1.5s |
| Tap trash on Received header | Clears entire received log instantly |
| Tap Home / Profiles tabs | Views slide horizontally past each other (opposite directions) with a bouncy easing curve |
| Tap "+" on Home card | Opens Add Profile modal (fresh, first chip pre-selected) |
| Tap edit/delete on a Profiles-view card | Opens pre-filled Edit modal / confirms and deletes |
| Tap gear icon | Opens Settings modal |
| Toggle "Sending Sound" | Enables/disables the beep on send actions |
| Tap Privacy Policy link | Opens nested Privacy modal on top |
| Click any modal's dark backdrop | Closes that modal |

---

## 7. Notes on Tone & Purpose

The interface is styled to evoke a covert, "hacker-terminal-meets-fintech" aesthetic: dark neumorphic surfaces, a glowing cyan accent, monospace numerals, and sound-wave animations that visually suggest an **ultrasonic (audio-based) data transfer** metaphor — i.e., "sending" a number is dramatized as an audio pulse traveling outward from the device, with a multi-stage confirmation sequence before the "signal" is logged as received.