# Peerless Skills v0.1

Fourteen player-only upgrades beyond Elite for Starsector 0.98a-RC8, with optional Second in Command support. Train through your own entry in Intel > Contacts.

- Field Modulation cancels the first overload each battle, vents if possible, and grants +200% maneuverability that fades over five seconds.
- Fixed refunds for skills granted through APSunlock: removing all fourteen upgrades returns fourteen Peerless points.
- Fixed optional post-cap XP tracking when enabling it in LunaLib and when a character remains above the cap after removing a level-cap mod. Added the read-only APSProgress console command for checking progress.
- Training now plays the game's character level-up sound.
- Retains quest rewards, category-colored training text, clickable point notifications, combat status entries, and green reload bars for depleted large missile launchers. Post-cap progression remains off by default; its default ratio is one point per ten post-cap XP bars when enabled.

**Safety Overrides and Phase Anchor:** Peerless Field Modulation cancels an SO ship's first overload and grants the maneuverability boost, allowing weapons to fire again. It does not clear flux or bypass SO's no-vent restriction. A lethal hit can trigger both Phase Anchor and Peerless Combat Endurance; Enduring does not stop the emergency retreat.

**Other mod interactions:** UAF's Optimized Ordnance Assembly stacks with Peerless missile regeneration; the green bar tracks only Peerless progress. Field Modulation's forced vent drains RAT's Phaseshift Shield charge, and its overload cancellation also works on scripted overloads such as Tahlan's Daemonic Heart penalty. Hullmods that multiply top speed also scale Helmsmanship's burst, including its nominal +100 cap.

Install the ZIP in your mods folder and restart Starsector. Replace the older Peerless Skills folder rather than keeping a second copy. Console Commands, Second in Command and LunaLib are optional for players. HoT and SoS quest rewards require compatible story-mod integrations.

Validation: all fifteen regression suites pass. Phase Anchor and Combat Endurance triggering together has also been confirmed in live combat. Cross-mod coverage is partial; the automated checks do not establish compatibility with every installed mod.
