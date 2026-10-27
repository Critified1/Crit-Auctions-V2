# Crit Auctions (Fabric, Minecraft 1.21.11)

Client-side auction overlay: auction card with countdown, payment toasts, a New Auction screen, and a live style editor.

## Build
1. Install JDK 21.
2. Copy `gradlew`, `gradlew.bat` and `gradle/` from a template made at https://fabricmc.net/develop/template into this folder.
3. `./gradlew build` -> jar in `build/libs/` (not the `-sources` one). Install it with Fabric API.

## Commands
/crit (or /bidboard)           open the menu (Auction module)
/crit (or /bidboard) style     open the menu on the Layout module (position, accent, corners, opacity)
/crit (or /bidboard) toggle | clear | reload | move <x> <y>

## Make it match your server
Edit `config/bidboard.json`:
- `startCommand` is what "Start auction" sends. Placeholders: {min} {qty} {timer} {note}. Empty = card only, nothing sent.
- The pattern lists are regexes with named groups who / amt / item, used to detect payments, bids and auction start/end.
- `accent` / `bg` take any hex color, e.g. "#FF6B8B".

## Keybinds
Options > Controls > Key Binds > "Crit Auctions": "Open Auction Menu" (default Right Shift) and "Open Layout Menu" (unbound). Rebind them there.

## Getting the jar without installing anything
Push this folder to a GitHub repo. The included `.github/workflows/build.yml` builds it:
Actions tab > latest run > Artifacts > bidboard-jar. Unzip it and put the jar in your mods folder (with Fabric API).

## Troubleshooting payments
`/bidboard last` prints the last few payment/bid-looking lines the mod saw. If it prints nothing while someone pays you,
the server isn't sending the message in a way the mod can read; send the lines to the developer.
`startMessage` in config/bidboard.json is the public chat line sent by "Start auction" (empty = send nothing).

## Sounds
Menu > Sounds: pick a sound, volume and pitch for opening the menu, starting an auction and winning, or switch each off.
Any vanilla sound id can also be typed into `sndOpen` / `sndStart` / `sndWin` in config/bidboard.json.

## Tabs
Auction, Layout (position, accent, corners, opacity, font, card style: Classic / Circle timer / Number timer),
Sounds, General (payment toasts, totals line, ultra-smooth corners, auto-copy winner). Click your skin card (bottom left)
for lifetime earnings and losses.
