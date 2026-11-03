# Peerless Skills

A Starsector 0.98a mod for player training beyond Elite. Install the **Peerless Skills** folder under `mods` and enable it in the launcher. It runs independently of both story mods.

**Helmsmanship** is the first supported skill. Training costs one point and raises its Elite top-speed bonus from **+10 to +30 su/second total**. The player must already have Elite Helmsmanship. The skill stays trained through reassignment and becomes active again when Elite is relearned.

| Story mod | One-time reward | Where to train |
| --- | --- | --- |
| Hall of Triumph | Finish Isa's **A Borrowed Name** at the Gan Eden Space Elevator | Isa's contact menu |
| Chief Navigator | Finish Sinni's story by completing Menelaus's final Labor debrief | Contacts → Sinni → Peerless training |

With both mods installed, the two rewards stack in either order. A second point stays banked after training Helmsmanship. Thirteen other Elite skills have artwork prepared for future upgrades. The game uses its ordinary XP and level-up rules.

## Integration

Declare `{"id":"peerless_skills","name":"Peerless Skills","version":"0.1.0"}` in the consuming mod's `mod_info.json` dependencies. For Java integration, compile against `jars/PeerlessSkills.jar` and call `PeerlessSkills.awardOnce("$your_mod_story_reward")` from `peerless.api` after the story completes. The key must be unique and reserved for this reward. The method sets the key and credits one point exactly once. For a rules-only integration, use `PeerlessSkillsCMD award your_mod_story_reward` (bare key, without `$`).

`getPoints()`, `canTrain(skillId)`, `train(skillId)`, and `isTrained(skillId)` use the active campaign's player. The rules adapter also exposes `unlocked`, `hasPoint`, `trained`, `canTrain` and `train` for the current Helmsmanship training dialogue. The optional Console Commands mod provides `AddPeerlessPoint [helmsmanship]` for testing.

## Development

From this directory, run `./build.ps1` and `./test.ps1` against a local Starsector installation. The tests exercise the point balance, native skill effects, console command and cached icon rendering. The Helmsmanship skill definition is shared with the base game; a mod replacing that same definition must preserve the Peerless effect entry.

Original code uses the included MIT license. Starsector's skill definition and the icons derived from its artwork remain subject to Fractal Softworks' rights. The icon catalog and authoring prompts are in `docs/`. The production mod uses public Starsector APIs and LWJGL, with no reflection.
