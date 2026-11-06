# Peerless Skills - prototype 0.2.25

A player-only training layer beyond Elite for Starsector 0.98a. Restart after installing the updated JAR.

[Illustrated list of all fourteen Peerless effects](docs/assets/peerless_effects_2x.png)

## Installation

Download the mod ZIP from [Releases](https://github.com/c4tntnitro-netizen/Peerless-Skills/releases). Extract the `Peerless Skills` folder into `Starsector/mods`, enable **Peerless Skills** in the launcher, and restart the game. The compiled runtime JAR is included; building from source is optional. The repository ZIP also includes a working compiled copy.

Second in Command is optional. Console Commands enables the testing commands below. LunaLib enables the optional progression settings. Quest rewards work without any of these optional libraries; the repository does not include modified copies of other mods.

## Training and testing

Use **Intel > Contacts > [your character] - Peerless training**, then select **Peerless training**, with or without SiC. The contact opens a standard text/options dialogue listing all fourteen skills across two pages. Review an effect, then explicitly spend one Peerless point. Viewing and backing out never spend points. Training requires native Elite level 2.

The introduction appears once per training session. Next/Previous and Back only change the available options. Skill options, effect descriptions and purchase options use the skill's category color: Combat red, Technology blue and Industry yellow. Successful training shows the updated point balance and plays Starsector's character level-up sound.

By default, completing the Galatia questline, Hall of Triumph's main quest, and SoS's main story (Menelaus's final Labor debrief) each grants one Peerless point, once per campaign. Already-completed Galatia campaigns receive their point when loaded. Isa and Sinni no longer offer training; use your own contact. Menelaus's legacy post-title route also remains available.

Each newly awarded point produces one clickable campaign notification with your current unspent total. It waits until other screens close, then clicking it opens the training menu. Duplicate reward checks, spending and refunds do not produce new-point notices.

Reassigning a skill so that it no longer has its Elite prerequisite clears its Peerless training and refunds one point after leaving the skill screen. The point can then be spent on another eligible skill. Skills that retain Elite keep their training. This also applies to upgrades granted by APSunlock, so removing all fourteen Peerless upgrades returns fourteen points.

In **LunaLib > Peerless Skills**, **Post-cap progression** is off by default. Enable it to earn one Peerless point per **10 post-cap levels**; **Post-cap levels per point** adjusts that ratio from 1 to 100. A post-cap level means one full XP bar at the current level cap. Progress starts when enabled, pauses while disabled, and survives saving. Changing the ratio applies to remaining progress. The selected level-up plugin supplies the cap and XP requirement, including SiC's settings.

LunaLib changes take effect immediately, including while a menu or the console is open. Use `APSProgress` in the console to see whether tracking is enabled and attached, your banked XP bars, the XP required per bar, and your point balance. This command only reads progress; it does not award points or reset tracking.

Characters above the current level cap also earn progress, including after removing a mod that raised the cap. Their level stays unchanged, and each Peerless XP bar still uses the active cap's XP requirement.

With Console Commands, run **APSunlock** during a campaign to train all fourteen upgrades and grant their Elite prerequisites without spending Peerless points. This changes the player's learned skills. AddPeerlessPoint still grants one test point once per campaign; AddPeerlessPoint helmsmanship additionally attempts to train that skill.

## Compatibility notes

**Safety Overrides and Phase Anchor:** Peerless Field Modulation cancels an SO ship's first overload and grants the maneuverability boost, allowing weapons to fire again. It does not clear flux or bypass SO's no-vent restriction. A lethal hit can trigger both Phase Anchor and Peerless Combat Endurance; Enduring does not stop the emergency retreat.

**Other mod interactions:** UAF's Optimized Ordnance Assembly stacks with Peerless missile regeneration; the green bar tracks only Peerless progress. Field Modulation's forced vent drains RAT's Phaseshift Shield charge, and its overload cancellation also works on scripted overloads such as Tahlan's Daemonic Heart penalty. Hullmods that multiply top speed also scale Helmsmanship's burst, including its nominal +100 cap.

## Effects

| Skill | Peerless effect |
| --- | --- |
| Helmsmanship | System activation grants bonus speed equal to 100% of unboosted top speed or +100 speed, whichever is less, decaying linearly over one second. Acceleration, deceleration, turn acceleration and maximum turn rate are doubled during the burst. Replaces the old permanent +20 bonus. |
| Combat Endurance | Once per battle, crossing 10% hull triggers five seconds at that hull floor. Enduring! text on hits and native Damper Field jitter visuals; no Damper damage reduction. |
| Impact Mitigation | +100% ship mass. |
| Damage Control | Engine/weapon repair speed increases by current CR percentage. At 70% CR, repair time is multiplied by 1/1.7. |
| Field Modulation | Cancel the first overload each battle and vent if able. Gain +200% maneuverability, fading to zero over five seconds. Safety Overrides ships cancel the overload but cannot vent. |
| Point Defense | PD hits add EMP equal to 10% of weapon damage. Shields block it. |
| Target Analysis | Unshielded hits cause five-second Disintegrator armor decay: min(10% of hit damage, 100) total. Cannot damage hull. |
| Ballistic Mastery | All weapons ramp to +100% autofire accuracy and 100% recoil reduction over five seconds of firing, then decay to zero over half a second without firing. |
| Systems Expertise | System activation gives 50% hull damage reduction for 0.25 seconds, tapering to zero at 0.5 seconds. |
| Missile Specialization | LARGE launchers only: after first emptying, steadily regenerate max(1, floor(actual maximum ammo x 0.05)) missiles per minute. Reaper with 28 ammo: 1/minute; Locust with 1200: 60/minute. |
| Gunnery Implants | +5 percentage points to the player's fleet ECM cap. |
| Energy Weapon Mastery | Full native damage bonus remains at 600 range or below; taper now reaches zero at 2000. Does not increase firing range. |
| Ordnance Expertise | +0.75 flux dissipation and +10 flux capacity per OP actually spent on hullmods. Free built-in/S-modded hullmods contribute zero spent OP. |
| Polarized Armor | +100% engine and weapon repair speed while venting. Adds to Damage Control's CR bonus. |

System bursts refresh without stacking. Beam EMP and armor-decay amounts are normalized by damage-tick duration; beam armor decay is capped at 100 per second before its five-second delivery. Independent decay applications stack. Existing native-regenerating missile launchers retain their original regeneration without a second mechanism. Fractional reload progress carries forward, with no banking beyond ammo capacity. Helmsmanship snapshots modified top speed without its own previous burst; at 60 top speed it adds 60, at 180 it adds 100. The maneuverability bonus stays at +100% until the one-second burst expires.

Target Analysis shares one decay scheduler and Disintegrator visual per target. Each hit retains its own impact location, damage and ten half-second ticks; new hits never refresh older applications. Armor changes are combined into one write per affected cell and one synchronization per target update. Visuals emit at most three particles per half second, with a hard limit of 24 live particles per target. The particle limit does not limit damage stacks. Native particle rendering and fading are reused without reflection or copied engine functions.

After an eligible large launcher first runs empty, its ordinary weapon-row loading bar becomes a **green Peerless reload-progress bar**. It fills toward the next regenerated missile and resets after that missile is awarded. The bar stays active while ammo is replenishing, including when the launcher has ammo available. Full launchers, inactive skills, and native regenerating weapons keep their ordinary bars. Progress freezes while combat is paused. Ammo amounts and regeneration rates are unchanged.

Active Peerless effects also appear in the bottom-left combat status list with their skill icons. Temporary bonuses show their current strength or remaining duration, then disappear when finished. Passive bonuses remain while applicable; missile regeneration appears while an eligible launcher is replenishing. The display uses the public combat-status API and follows the player's piloted ship. Automated checks cover status contents and timing; live HUD appearance still needs a playtest.

## Second in Command

SiC is optional. Its fourteen selected red player skills use native Elite level 2 and qualify for training. Deselecting a trained skill refunds its Peerless point after leaving the skill screen. Executive officers receive no upgrades. Public combat/campaign hooks provide gameplay effects without replacing vanilla/SiC skill files or custom screens. All fourteen trained skills show their Peerless descriptions and upgrade artwork on the character screen. Vanilla uses twelve gold and two industry bronze icons; with SiC enabled, Ordnance Expertise and Polarized Armor are tinted gold to match its combat roster. The training dialogue shows full descriptions and purchased status.

Descriptions append to the native skill-effect list through an isolated native-data adapter, because the public accessor returns a copy. Ordnance Expertise's entry also supplies a normal ship-stat effect, following the native skill's weapon-OP calculation with hullmod OP instead. Registration starts in onApplicationLoad before the engine builds its skill-effect dispatch caches. The refit screen therefore recalculates the bonus for its edited variant. It adds no hidden hullmods or saved variant IDs. The campaign/combat fallback shares the same modifier ID, preventing double application.

An optional SiC adapter appends the description to SiC's existing effects cache. Neither adapter replaces original gameplay effects or skill files, traverses UI internals, or uses reflection. These adapters call internal native data classes and should be checked when upgrading Starsector or SiC.

Grey explanatory skill footnotes are omitted to prevent overlap with Peerless text. Flavor quotes, effect lines and gameplay are preserved. Deselected purchases use a compact `Peerless (inactive)` heading.

Peerless icon recoloring is limited to the character skill screen. Fleet tooltips, captain selection and refit retain their ordinary icons. The fleet UI adapter and its render hooks were removed at the user's request.

The missile HUD uses a third isolated Starsector 0.98a adapter because the public API has no per-weapon loading-bar color hook. It finds native weapon rows through a bounded HUD scan and substitutes only their bar component, retaining the original anchors and other row elements. It draws with the native bar renderer, delegates to the original when inactive, and restores it when the player ship or HUD changes. It uses no reflection, fixed screen coordinates, global color changes, or ammo-tracker mutation. Check this adapter when upgrading Starsector.

## Integration and development

API: peerless.api.PeerlessSkills in the runtime JAR declared by mod_info.json. awardOnce("$your_mod_unique_reward") grants one point, records its receipt and queues one notification. getPoints(), getSupportedSkillIds(), canTrain(id), train(id), and isTrained(id) use the active campaign player. Legacy title, reward and Helmsmanship selection keys are retained; refunding training never clears reward receipts.

Rules: PeerlessSkillsCMD open opens shared training; award your_mod_unique_reward grants a point. Existing no-argument trained/canTrain/train remain Helmsmanship adapters for older integrations. hasPoint and unlocked query the shared balance.

Run build.ps1 and test.ps1. Regressions cover all fourteen effects, native Disintegrator timing and armor-only damage, system windows, combat-local receipts, missile rates/mount gates, respec/captain changes, console unlocks, training options, native story CSV parsing, and the installed SiC player-skill list. Description tests use real native skill specs and SiC's actual cache. Native GL tests verify fourteen simultaneous icon swaps, individual eligibility, interrupted reload cleanup, exact restoration at every mip level, and the existing graphics lifecycle cases. Builds stage an alternate runtime JAR and update mod_info.json when the running game locks the current JAR; tests use the declared runtime JAR.

For source builds, place this checkout in `Starsector/mods/Peerless Skills` and install Console Commands, Second in Command and LunaLib so the build can resolve their APIs. The test harness also uses LazyLib, its Kotlin runtime and the game's bundled Windows native libraries. Those developer dependencies and the base game are not included in this repository. Run `python tools/render_peerless_reference.py` with Pillow installed to regenerate the illustrated effect list using the game's bitmap fonts.

Training access uses the native Intel contact APIs in both vanilla and SiC. The character-screen training button and its UI adapter have been removed.

Optional XP progression uses an isolated 0.98a adapter around the selected level-up plugin. It delegates every calculation and observes the native CharacterStats XP-normalization query, using its immediate caller to exclude tooltip queries and repeated story-point calculations. The original plugin is restored when disabled. This uses direct native registry access and a Java stack trace, without reflection or copied engine functions. Check this adapter when upgrading Starsector. Regression coverage runs actual CharacterStats XP awards across multiple wraps; notification coverage runs native campaign-message click dispatch. These tests do not establish live UI hit testing or appearance.

Run python tools/wire_story_training.py to remove obsolete Isa/Sinni training rules from installed optional story mods while preserving their completion rewards. It is idempotent and backs up records under build/story-training-backups, preserving every unrelated CSV record byte-for-byte. The installed Chief Navigator development JAR also removes Sinni's contact button; these optional-mod changes are local and are not bundled in this repository.

Live playtests have confirmed Helmsmanship, Missile Specialization, Combat Endurance, Field Modulation and Ordnance Expertise in combat, along with Peerless descriptions/artwork in SiC, training category colors and combat status entries. Phase Anchor and Combat Endurance triggering together has also been confirmed and is retained. The reassignment crash fix and consolidated decay visuals still need live confirmation. This prototype does not change enabled mods, edit save files or advance story progression.

Original code uses the included MIT license. Starsector data and derived artwork remain subject to Fractal Softworks' rights.
