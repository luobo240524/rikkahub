# Handover · Pet Growth System (Phase 1) — 2026-11-25

> Scope: egg-signal anti-spam, growth system, relationship-signal hatching.
> Remote branch `custom-2.5.6`, HEAD r361 = `66b9824aaa35`. DB version **v39**.
> All tunable numbers live in `WorldConstants.kt` (signals / cooldowns / growth / stage thresholds / origin texts).

## Shipped

### r358/r359 — hatch-signal anti-spam
- `hatchSignals` unit x10 (full = 100 normal / 120 golden; UI shows 10.0 / 12.0). No DB type change.
- Contributions: feed/warm +5, shake/blanket +5, TA-care +5, chat-signal +1 (reserved, not wired yet).
- Per-action cooldown read from event table: feed/care 4h, TA 6h, chat not throttled.
- Hatch gate: full signal AND at least one local calendar day crossed (`eggCrossedDays`).
- r359 fix: JS wrappers (`wrapAct`/`bumpEgg`/`hatchNow`) dropped bridge return values, so `fail=cooldown`
  never reached the page. Now `applyOpState` consumes the returned state (truth overwrite + FAIL_HINTS toast).

### r360 — thoughts page
- Composer now has an 11-mood pill selector (default calm; resets after send). Previously hardcoded `calm`.
- Word cloud: flex-start layout + deterministic pseudo-random margins (stable per thought), no longer center-crowded.

### r361 — growth system + relationship-signal hatching (DB v39)
- `world_pet_state` += `growth` / `stage` / `stage_at` / `origin` / `personality` (5 cols).
  `Migration_38_39` handwritten & registered; `AppDatabase` version 39.
- **Effective growth = care-accumulated growth + days-since-birth (purely derived)** — existing pets
  gain growth/stage immediately after migration, no backfill needed.
- Care gains: feed +2 / play +2 / groom +1; repeat decay `max(1, base/n)` counting consecutive same-action
  events (resets on switching action).
- Stage thresholds (dual gate, growth + days): 40/7 → 100/14 → 220/21, then +120/+30 per stage, unlimited.
  Lazy settle in `settleStage` + `stage_up` milestone diary entry.
- Personality: `PetPersonality` JSON (5 dims 0-100); feed/groom → affection+1, play → exploration+1.
- Hatching: `WorldRelationshipSignals` computes 5 dims locally —
  affection = VitalsDAO valence (per-assistant isolated), reading = books read in last 7 days
  (ReadTogetherRepository), watching = moments count, exploration = timeline events, listening = 0 (not persisted).
  Dominant dim decides species + origin text (affection→moon, reading→forest, listening→stardust,
  watching→tide, exploration→random of four). All-empty → random fallback. Initial personality = signal values.
- UI: `PagePet` + growthTotal/stageName/origin/nextGrowth/nextDays; profile sheet shows growth row and
  origin row; statline/ticker include stage; WorldTool state injection includes stage + growth.
- DI: `WorldRelationshipSignals` provided in DataSourceModule; `WorldRepository` ctor extended.

## Acceptance checklist (install r361 APK)

Anti-spam (test with a **freshly adopted egg**; old eggs show tiny numbers by design):
1. Signal shows x.x / 10.0 (golden 12.0).
2. Cooldown: warm/shake/blanket 4h each, TA 6h; rejected taps toast + disabled buttons.
3. Cross-day gate: full signal same-day cannot hatch; hatchable next local day.
4. Rejected ops give visible feedback (r359 fix).

Thoughts:
5. Mood pills selectable (11 moods, default calm, resets after send).
6. Cloud layout staggered from left, not center-crowded.

Growth:
7. Existing pets show growth + stage name immediately (derived from days).
8. Care diary shows "growth +x"; consecutive same action decays.
9. Profile shows "next stage needs x growth / y days"; stage-up diary entry on threshold.
10. Hatch species correlates with recent activity + origin text on profile.
11. AI state injection includes stage/growth (assistant can answer "what stage is my pet").

## TODO (priority order)

1. **Batch C: local stage art + album** — pet.html scale by stage (0.8 / 1.0 / 1.05+SVG deco / 1.1+glow);
   album reads `stage_up` events; no image generation, no granary cost.
2. **Chat-signal hook**: GenerationLoop per-round → `bumpEggSignal(..., HATCH_ACT_CHAT, SIGNAL_CHAT)`
   (channel ready, currently no caller).
3. **Poses**: forest/stardust/tide still use portrait in all 6 sprite cells — generate 5 poses each when budget allows.
4. Listening dim once music play history is persisted.
5. Optional: stage-up badge/notification; remove obsolete composer placeholder text in thoughts.html.
