# Peerless Skills v0.1.1

Removes the internal XP hook responsible for the reported `ScriptStore.Object(Class)` campaign-loading crash. Optional post-cap progression now checks XP every campaign frame through Starsector's public API, with no reflection or replacement of the selected level-up plugin.

- Keeps banked XP progress, awarded points and quest reward receipts from existing saves.
- Keeps post-cap progression off by default. Enable it in LunaLib; the default remains one Peerless point per ten post-cap XP bars, with an adjustable ratio.
- Counts visible XP gains and observed bar rollovers. Complete XP cycles between checks cannot be recovered, so large awards can be undercounted. Characters left above a lowered cap may also lose rollover progress when their level-up plugin supplies no usable range.
- Updates `APSProgress` to report public-API polling and the sampling limitation.
- Retains the Galatia, HoT and SoS quest rewards and existing skill effects.

**Compatibility:** The declared target remains **Starsector 0.98a-RC8**. This is **not a full 0.98.5a-RC7 compatibility update**. The unchanged missile loading-bar adapter still depends on older native HUD classes and prevents a full source rebuild against RC7. Removing the XP crash does not establish compatibility for the rest of the mod.

**Validation:** Focused progression regressions pass against the installed RC7 API/runtime, covering campaign-frame updates, rollovers, toggles, reloads, old banked progress, quest rewards and unchanged player stats. The tracker compiles without the internal engine JAR, and both obsolete XP hook classes are absent from the packaged runtime. Full-mod tests and a live campaign playtest have not been completed on RC7.

**Installation:** Replace the old Peerless Skills mod folder with the one in `Peerless-Skills-v0.1.1.zip`, then restart Starsector. Console Commands, Second in Command and LunaLib remain optional for players.
