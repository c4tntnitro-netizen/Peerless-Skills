# Peerless Skills - prototype 0.2.6

A player-only training layer beyond Elite for Starsector 0.98a. Restart after installing the updated JAR.

[Illustrated list of all fourteen Peerless effects](docs/assets/peerless_effects_2x.png)

## Installation

Download the repository ZIP using **Code > Download ZIP**. Extract the folder containing `mod_info.json` into `Starsector/mods/Peerless Skills`, enable **Peerless Skills** in the launcher, and restart the game. The compiled runtime JAR is included; building from source is optional.

Second in Command is optional. Console Commands is optional and enables the testing commands below. Story-contact training requires the optional integration step described under **Integration and development**; the repository does not include modified copies of other mods.

## Training and testing

Open **Intel > Contacts > [your character] - Peerless training**, then select **Peerless training**. The standard text/options dialogue lists all fourteen skills across two pages. Review an effect, then explicitly spend one Peerless point. Viewing and backing out never spend points. Training requires native Elite level 2.

Isa and Sinni open the same dialogue in the installed Hall of Triumph and Chief Navigator copies. Their existing story rewards remain independent, once-per-campaign grants. Menelaus's legacy post-title training route also opens the shared dialogue. No XP or level-up override is used. Without a reward, the player contact still permits inspecting skills.

With Console Commands, run **APSunlock** during a campaign to train all fourteen upgrades and grant their Elite prerequisites without spending Peerless points. This changes the player's learned skills. AddPeerlessPoint still grants one test point once per campaign; AddPeerlessPoint helmsmanship additionally attempts to train that skill.

## Effects

| Skill | Peerless effect |
| --- | --- |
| Helmsmanship | System activation grants bonus speed equal to 100% of unboosted top speed or +100 speed, whichever is less, decaying linearly over one second. Acceleration, deceleration, turn acceleration and maximum turn rate are doubled during the burst. Replaces the old permanent +20 bonus. |
| Combat Endurance | Once per battle, crossing 10% hull triggers five seconds at that hull floor. Enduring! text on hits and native Damper Field jitter visuals; no Damper damage reduction. |
| Impact Mitigation | +100% ship mass. |
| Damage Control | Engine/weapon repair speed increases by current CR percentage. At 70% CR, repair time is multiplied by 1/1.7. |
| Field Modulation | Once per battle, first overload converts to a forced native vent on the next listener update. |
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

After an eligible large launcher first runs empty, its ordinary weapon-row loading bar becomes a **green Peerless reload-progress bar**. It fills toward the next regenerated missile and resets after that missile is awarded. The bar stays active while ammo is replenishing, including when the launcher has ammo available. Full launchers, inactive skills, and native regenerating weapons keep their ordinary bars. Progress freezes while combat is paused. Ammo amounts and regeneration rates are unchanged.

## Second in Command

SiC is optional. Its fourteen selected red player skills use native Elite level 2 and qualify for training. Purchases persist through respecs, deactivate while deselected, and reactivate on reselection. Executive officers receive no upgrades. Public combat/campaign hooks provide gameplay effects without replacing vanilla/SiC skill files or custom screens. All fourteen trained skills show their Peerless descriptions and upgrade artwork on the character screen. Vanilla uses twelve gold and two industry bronze icons; with SiC enabled, Ordnance Expertise and Polarized Armor are tinted gold to match its combat roster. Inactive purchases are identified in the tooltip and keep ordinary icons. The training dialogue also shows full descriptions and purchased/active status.

Descriptions append to the native skill-effect list through an isolated native-data adapter, because the public accessor returns a copy. Ordnance Expertise's entry also supplies a normal ship-stat effect, following the native skill's weapon-OP calculation with hullmod OP instead. Registration starts in onApplicationLoad before the engine builds its skill-effect dispatch caches. The refit screen therefore recalculates the bonus for its edited variant. It adds no hidden hullmods or saved variant IDs. The campaign/combat fallback shares the same modifier ID, preventing double application.

An optional SiC adapter appends the description to SiC's existing effects cache. Neither adapter replaces original gameplay effects or skill files, traverses UI internals, or uses reflection. These adapters call internal native data classes and should be checked when upgrading Starsector or SiC.

Grey explanatory skill footnotes are omitted to prevent overlap with Peerless text. Flavor quotes, effect lines and gameplay are preserved. Deselected purchases use a compact `Peerless (inactive)` heading.

Peerless icon recoloring is limited to the character skill screen. Fleet tooltips, captain selection and refit retain their ordinary icons. The fleet UI adapter and its render hooks were removed at the user's request.

The missile HUD uses a third isolated Starsector 0.98a adapter because the public API has no per-weapon loading-bar color hook. It finds native weapon rows through a bounded HUD scan and substitutes only their bar component, retaining the original anchors and other row elements. It draws with the native bar renderer, delegates to the original when inactive, and restores it when the player ship or HUD changes. It uses no reflection, fixed screen coordinates, global color changes, or ammo-tracker mutation. Check this adapter when upgrading Starsector.

## Integration and development

API: peerless.api.PeerlessSkills in the runtime JAR declared by mod_info.json. awardOnce("$your_mod_unique_reward") grants one point and records its receipt. getPoints(), getSupportedSkillIds(), canTrain(id), train(id), and isTrained(id) use the active campaign player. Legacy title, reward and Helmsmanship selection keys are retained; other skills have independent permanent keys.

Rules: PeerlessSkillsCMD open opens shared training; award your_mod_unique_reward grants a point. Existing no-argument trained/canTrain/train remain Helmsmanship adapters for older integrations. hasPoint and unlocked query the shared balance.

Run build.ps1 and test.ps1. Regressions cover all fourteen effects, native Disintegrator timing and armor-only damage, system windows, combat-local receipts, missile rates/mount gates, respec/captain changes, console unlocks, training options, native story CSV parsing, and the installed SiC player-skill list. Description tests use real native skill specs and SiC's actual cache. Native GL tests verify fourteen simultaneous icon swaps, individual eligibility, interrupted reload cleanup, exact restoration at every mip level, and the existing graphics lifecycle cases. Builds stage an alternate runtime JAR and update mod_info.json when the running game locks the current JAR; tests use the declared runtime JAR.

For source builds, place this checkout in `Starsector/mods/Peerless Skills` and install Console Commands and Second in Command so the build can resolve their APIs. The test harness also uses LazyLib's Kotlin runtime and the game's bundled Windows native libraries. Those developer dependencies and the base game are not included in this repository. Run `python tools/render_peerless_reference.py` with Pillow installed to regenerate the illustrated effect list using the game's bitmap fonts.

Run python tools/wire_story_training.py to route installed optional story contacts to the generic dialogue. It is idempotent and backs up records under build/story-training-backups, preserving every unrelated CSV record byte-for-byte. Distribution of optional-mod patches is not packaged yet; the current installed copies are wired locally.

The user has confirmed Helmsmanship, Missile Specialization, Combat Endurance, Field Modulation and Ordnance Expertise in live combat and supplied a SiC character-screen screenshot showing Peerless descriptions and artwork. The revised refit calculation, new SiC gold tint and full SiC gameplay still need live confirmation. This prototype does not change enabled mods, edit save files, advance story progression, or publish a release.

Original code uses the included MIT license. Starsector data and derived artwork remain subject to Fractal Softworks' rights.
