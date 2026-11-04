# Peerless prototype playtest notes

## November 3, 2026 - user test

- **Peerless Helmsmanship (Phelm): works in live combat.** The increased speed cap was difficult to reach before the one-second decay because acceleration was insufficient.
- **Peerless Missile Specialization (Pmissile): works in live combat.**
- **Peerless Combat Endurance: confirmed working in live combat by the user.**
- **Peerless Field Modulation (POverload): overload-to-forced-vent effect confirmed working in live combat by the user.**
- **Peerless Ordnance Expertise: confirmed working in live combat by the user.** Its bonus flux capacity and dissipation are missing from the refit preview; that display still needs correction.
- Requested missile UI improvement: after a launcher runs empty, replace its usual missile-loading display with a **green reload-progress bar** showing Peerless regeneration. Implemented in prototype 0.2.2; details below.

## Helmsmanship revision following that test

- Bonus speed is **min(unboosted modified top speed, 100 su/second)**, snapshotted on activation, decaying linearly to zero over one second.
- Examples: 60 top speed gains +60; 180 top speed gains +100.
- During the burst, acceleration, deceleration, turn acceleration and maximum turn rate are **+100%**.
- Remove the previous burst before calculating a refresh so repeated activations cannot compound it.
- The revised values still need a new live-combat test; the earlier user confirmation applies to the preceding prototype.

The accompanying display correction adds descriptions for all fourteen trained skills and their authored Peerless icons. Automated checks cover native and SiC descriptions, simultaneous texture swaps and restoration, and inactive/untrained gates. A full game restart is required to load the staged JAR.

## Green missile reload bar - prototype 0.2.2

- The green bar occupies the existing weapon loading-bar slot after the large launcher first empties.
- Fill comes directly from the custom regeneration timer; it resets each time a missile is regenerated and does not change ammo rates.
- It keeps displaying while replenishing, even if some regenerated ammo is available. Full launchers and inactive upgrades return to the original native loading display.
- Native row positions, ammo counters, HUD fades and layout anchors are retained. Switching ships or rebuilding HUD rows restores the original component.
- No native ammo-regeneration settings are changed, preventing duplicate regeneration.
- Automated checks cover the actual native bar renderer/framebuffer, row discovery, progress, native fallback, movement/fading, restoration and unchanged Reaper/Locust regeneration rates.
- Live in-game appearance awaits a restart/playtest. Earlier user confirmation of missile regeneration remains recorded above.

## Fleet icons and footnotes - prototype 0.2.3

- The user reports that fleet portrait skill tooltips and captain-picker rows still show ordinary icons.
- The live log identifies the cause: Starsector rejects the adapter's reflection access with a SecurityException. The original automated fixture did not enforce that restriction.
- Native grey explanatory skill footnotes are removed to avoid overlap. Flavor quotes and gameplay effects remain. Inactive training uses the heading `Peerless (inactive)` instead of a grey caveat.

## Fleet icon correction - prototype 0.2.4 attempt (subsequently removed)

- Replaced the rejected reflection adapter with direct native UI accessors and before/after child-render hooks.
- Reuses the character screen's working cached-texture swap, restoring ordinary artwork before an officer row draws.
- Automated native-widget/framebuffer checks cover player/officer isolation in one frame, portrait tooltip ownership, nested draw scopes and hook removal. Production adapter bytecode is checked for reflection references.
- The new implementation still uses internal native UI classes. It contains no reflection or copied engine functions.
- Live appearance in these two views awaits a full restart/playtest.

## Ordnance refit correction - prototype 0.2.4

- The user's combat confirmation remains recorded above. The missing refit bonus was caused by applying the effect only to combat and campaign fleet-member stats, rather than the stats rebuilt by refit.
- Added a normal ShipSkillEffect using the native Ordnance Expertise calculation path, with hullmod OP in place of weapon OP. It adds 0.75 dissipation and 10 capacity per spent hullmod OP.
- Registered through the existing skill-spec adapter at application load, before native skill-effect caches are built. No reflection, custom hullmod or persistent ship variant ID is added.
- A short-lived hidden-hullmod implementation was removed and its JAR replaced before completion. It is not part of the shipped build.
- The real native CharacterStats dispatcher regression covers refit-style stats, hullmod edits, free hullmods, captain/Elite/training gates and no stacking with the combat/campaign fallback.
- All ten suites pass. Refit values and the revised fleet/captain artwork still await live confirmation after a full restart.

## Recoloring scope - prototype 0.2.5

- The user requested abandoning fleet, refit and other secondary-view recoloring.
- Removed the fleet/captain UI adapter, its registration and scoped draw hooks. Character skill-screen recoloring remains.
- The Ordnance Expertise refit stat correction remains; this change only narrows icon recoloring.

## SiC industry icons - prototype 0.2.6

- Requested gold Peerless Ordnance Expertise and Polarized Armor icons in SiC's character skill roster.
- With SiC enabled, the existing renderer shifts the two bronze icons' warm tones to gold while retaining their detail and alpha. Vanilla keeps bronze.
- No new engine hooks, reflection or saved data are added. Secondary-view recoloring remains removed.
- The user's screenshot shows SiC's live character skill roster with Peerless descriptions and artwork. The new gold tint still needs a restart/live check.
- All ten regression suites pass, including native GL runs with SiC enabled/disabled. Inspected the rendered bronze/gold comparison in build/sic-industry-icons.png.
