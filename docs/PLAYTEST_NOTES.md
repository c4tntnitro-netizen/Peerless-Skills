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

## Character-screen training - prototype 0.2.7

- Added **Peerless skills...** immediately above vanilla **Reassign skills...**, opening the existing text/options training menu. SiC uses a compact button below its player level information.
- Button creation and dialogue opening use public APIs; the isolated native tree lookup uses direct methods without reflection. Repeated frames, resizing, stale callbacks, tab changes and paused interaction are handled.
- Removed Isa's training rules and Sinni's training rules/contact button. Kept Sinni's other contact functions.
- Preserved HoT's quest-completion award and Menelaus's final-debrief award, including existing receipt keys and balances. Unrelated dialogue records remain byte-identical.
- Updated only SinniContactIntel.class in the installed Chief Navigator JAR. Other runtime payloads, including MenelausTrial.class, are unchanged.
- Eleven Peerless suites, the revised Sinni contact regression and native CSV checks pass. Live placement and dialogue opening await a restart/playtest.

## Contact-menu training - prototype 0.2.8

- The user reports that the SiC button is absent and requests contact-menu training instead.
- Removed both character-screen button adapters. Use Intel > Contacts > [your character] - Peerless training > Peerless training, with or without SiC.
- Isa/Sinni training remains retired. Both quest-completion rewards and their existing receipt keys are unchanged.
- Updated both installed story mods' required Peerless version to 0.2.8-prototype.
- Eleven suites pass, including real contact-button-to-text-menu interaction, all fourteen skills, zero-point previews, single-point training and repeat-load registration. Verified the runtime JAR contains no character-button adapter classes. Live contact appearance awaits restart/playtest.

## Vanilla shortcut retained - prototype 0.2.9

- The user clarified that the Peerless button should remain for non-SiC games.
- Restored the shortcut above vanilla Reassign skills, including when reassignment is disabled. It opens the existing text menu.
- With SiC enabled, the shortcut script is not installed and performs no character-panel lookup or injection. The player's own Contacts entry is retained in both configurations.
- Isa/Sinni training stays removed; both quest-completion rewards and receipt keys are unchanged. Both story-mod dependencies match 0.2.9-prototype.
- Twelve suites pass, including native anchor lookup, placement/resizing, paused input, stale callback cleanup, no point spend on opening, contact training and strict SiC exclusion. Live vanilla appearance awaits restart/playtest.

## Vanilla button opening - prototype 0.2.10

- The user confirms that the vanilla button appears but does not open training.
- The previous handler waited for no dialog while leaving the character screen open, and discarded requests when opening was rejected. It now closes the core screen with the public API, waits until the transition finishes, and retains the request if opening is temporarily rejected.
- Explicitly enables input processing for the button's UI element. Opening another core tab cancels deferred training; stale callbacks remain ignored.
- Twelve suites pass, including the character screen counted as an active dialog, delayed dismissal, a rejected opening followed by success, callback/checked-state input, paused execution, no duplicate opening or point spend, and strict SiC exclusion.
- Built and installed 0.2.10-prototype. Live opening awaits a full restart/playtest. No reflection or engine-function extraction added.

## Contact training for all configurations - prototype 0.2.11

- The user reports that the 0.2.10 character button also failed in-game and requests contact training for all configurations.
- Removed the character-button script, its native UI lookup, registration and simulated button tests. The player contact is installed unconditionally, with or without SiC.
- Use Intel > Contacts > [your character] - Peerless training > Peerless training. Existing training, points and quest-completion awards are preserved.
- Automated contact tests exercise registration, the Intel callback, training options and point spending with simulated UI interfaces. They do not establish live click handling or rendering; live contact access still needs confirmation after a full restart.
- All eleven remaining suites pass. Verified that the installed JAR contains the contact and training dialog and no character-button adapter classes.

## Rewards, notices, reassignment and optional XP - prototype 0.2.12

- Each successful point award queues one campaign notice with the unspent balance. Notices wait for the campaign view; clicking uses the native interaction action to open the shared training dialog. Existing reward receipts suppress repeats. Refunds and spending are silent.
- Removing a trained skill's Elite prerequisite refunds its spent point after leaving the skill screen, or when opening training. New free APSunlock upgrades do not create refundable points. Reward receipts remain permanent.
- Default rewards are one point each for Galatia, HoT and SoS. Galatia uses the native `$gaATG_missionCompleted` flag and a separate once-only receipt, including existing completed campaigns. HoT/SoS routes and receipts are preserved.
- Optional LunaLib progression defaults to disabled, with a configurable 1-100 post-cap XP bars per point (default 10). Disabled XP is excluded; partial enabled progress persists through toggles and saves.
- A small native level-up adapter observes XP immediately before its native wrap, delegating all cap, XP, skill-point and story-point calculations. It uses direct native registry access and caller stack information, without production reflection or extracted engine functions.
- Native message dispatch reaches the real training menu in the regression harness. Native CharacterStats tests cover large and repeated XP awards within one frame, exact thresholds, unchanged story-point gains, modified caps, toggles and reload. Physical clicking, appearance and LunaLib menu behavior still require a live playtest after restart.
- All thirteen regression suites pass. Package checks confirm Luna defaults are off/10, the old character-button classes remain absent, and production bytecode contains no reflection references.

## Training navigation and sound - prototype 0.2.13

- The user's screenshot confirms the training menu is visible but repeats its introduction whenever Next/Previous or Back is pressed. Moved the introduction to dialog initialization; navigation now refreshes options only. Training results include the updated balance.
- Added an original 2.3-second, three-hit training sound, played once by a successful paid training action. Previewing, navigating, failed/repeated purchases and APSunlock do not play it.
- All thirteen suites pass, including repeated navigation, one introduction per opened dialog, current balance after spending and one success cue. Verified sound registration and PCM audio format. Live sound mix and the updated menu await restart/playtest.

## Combat status display - prototype 0.2.14

- Added all fourteen Peerless skills to the native bottom-left combat status list using the public maintainStatusForPlayerShip API and existing skill artwork. No reflection or extracted engine functions are added.
- Temporary effects display only while active: speed and accuracy track their current bonuses, hull protection tracks its reduction, Enduring counts down, and the emergency-vent entry is limited to the one-use forced vent. Later manual vents do not revive it.
- Passive bonuses remain while applicable. Repair speed follows CR, Ordnance Expertise shows the actual flux bonuses, and missile regeneration lists the number of replenishing large launchers. Weapon bonuses require relevant weapons.
- Entries remain visible while paused without advancing timers and stop refreshing after expiry, death, captain changes or loss of the skill's prerequisites.
- All fourteen regression suites pass. The new suite checks the real effect/controller state sent to the public status API; it does not verify native HUD layout or appearance. Restart and check the status list in combat, including simultaneous effects and short system bursts.

## Shorter combat status text - prototype 0.2.15

- The user's screenshot confirms Peerless entries appear in the live combat status list.
- Shortened Gunnery Implants to "+5% ECP cap" and Target Analysis to "Armor decay on hit", as requested. Damage Control now shows "+X% repair speed" using current CR.
- Energy Weapon Mastery now shows "Bonus damage range doubled".
- Display wording only; gameplay is unchanged. Rebuilt the runtime JAR and checked the packaged strings.

## Consolidated armor decay - prototype 0.2.16

- Target Analysis now uses one scheduler and native Disintegrator renderer per affected ship. Hits keep independent source, impact location, damage and five-second deadlines, with ten damage ticks beginning half a second after each hit. New hits do not refresh existing stacks. Long frames catch up due ticks.
- Due armor damage is accumulated per cell before applying it, with at most one write per affected cell and one armor synchronization per target update. The existing 21-cell weighting, per-hit projectile/beam damage cap, live damage modifiers and armor-only behavior are preserved.
- Shared visuals emit no more than three particles per half second and never exceed 24 live particles per target. The visual tail is reused by new hits; completed damage stacks are discarded immediately. Death/removal stops pending damage, and expiration or cleanup removes the target from the combat-local registry.
- All fifteen regression suites pass. Dedicated checks cover independent staggered deadlines, moving/rotating targets, live modifiers, pause, long frames, no hull spillover and cleanup. A 1,000-hit volley preserves all damage with one renderer, 21 cell writes and one armor sync per tick. A 6,000-hit sustained load preserves total damage while staying below the particle cap and releasing completed stacks.
- These are deterministic resource-count and damage tests, not live FPS/VRAM measurements. Restart for a live visual/performance check.

## Reassignment crash - prototype 0.2.17

- The user reports a possible crash while testing skill reassignment. The game log confirms a fatal NullPointerException in PeerlessSkills.isTrained, reached from PeerlessDescription during skill-tooltip creation. The icon hook first encounters the same error and disables its rendering.
- Refunding Helmsmanship previously stored a null training value. Native Memory.getString throws when the key exists with a null value. Training checks now read the raw value safely, including existing saved null values, and refunds remove the key using the public MemoryAPI.unset method.
- Reproduced the exact exception against the game's real Memory class before the fix. Regression coverage now checks the formerly crashing tooltip path, confirmed refunds, repeat checks, retraining, and retaining prior Peerless ownership when restoring Elite during reassignment. The shared memory fixture now mirrors native null-string behavior.
- The user reports an unexpected but desired two-tap Peerless upgrade behavior during reassignment. Preserve that observed behavior; this patch changes only null handling and key removal, not skill-screen input or progression rules. Its full live behavior has not been independently reproduced.
- Built 0.2.17-prototype and passed all fifteen suites, including the native-memory reproducer. The before-fix run fails with the same Memory.getString/isTrained exception as the live log; the patched run passes. Live reassignment still needs confirmation after restart.

## Training category colors - prototype 0.2.18

- Skill options, effect descriptions and purchase options now use the governing aptitude color supplied by the public skill API: Combat red, Technology blue, Industry yellow. Navigation and general training messages retain their ordinary colors.
- Uses the public option/text color overloads; no reflection or internal UI hooks. Live dialog appearance awaits restart/playtest.
