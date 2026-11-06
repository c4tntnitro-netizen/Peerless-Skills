# Peerless Skills 0.2.18 (Prototype)

Fourteen player-only upgrades beyond Elite for Starsector 0.98a, with optional Second in Command support.

- Train through your own entry in Intel > Contacts. Skill options and descriptions use their category colors.
- Earn one Peerless point from each of the Galatia, Hall of Triumph and SoS main questlines. HoT and SoS require their compatible story-mod integrations.
- Each newly earned point gives a clickable notification that opens training. Removing an Elite prerequisite refunds the spent Peerless point.
- Optional LunaLib post-cap progression is disabled by default. When enabled, the default is one point per ten post-cap XP bars, adjustable in settings.
- Active effects appear in the combat status list. Depleted large missile launchers show green regeneration progress.
- Armor decay keeps independent five-second damage stacks while sharing one renderer per target, with a 24-particle limit.
- Fixed the Helmsmanship reassignment crash caused by a cleared training flag. Successful training plays a short three-hit sound.

Install the ZIP in your mods folder and restart Starsector. Do not install a second copy alongside an older Peerless Skills folder. Console Commands, Second in Command and LunaLib are optional for players.

Validation: all fifteen regression suites pass, including the real native-memory crash reproducer and high-volume armor-decay checks. The latest UI changes and live combat performance still need playtesting.
