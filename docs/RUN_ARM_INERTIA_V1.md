# Run arm inertia V1

User requested direct Codex implementation for this step (2026-09-11).

The current torso already has ProceduralTorsoFollow and carries the upper arms when
torso-hip-pivot is enabled. Adding another torso oscillator would duplicate that motion.
This change instead delays the relative upper-arm pitch with a bounded first-order lag.
Elbow rotation is preserved; forearms inherit the shoulder motion through existing parenting.
No geometry, root translation, scale, authored clips or gameplay parameters change.

Order: authored locomotion -> contact roll -> existing torso follow -> arm pitch lag ->
head world-look compensation. The arm filter never writes the torso or head.

Settings (client restart required):
- run.arm-follow-ticks: 0.0 by default (exact bypass), valid 0..8. First trial: 0.75.
- run.arm-lag-degrees: 3.0, valid 0..6. Bounds the difference from the authored shoulder pitch.
- Existing run.contact-roll-degrees stays at the user-approved runtime value 0.6.

The effect scales with blended run weight, gait speed and positive forward-velocity fraction.
Pure strafing, backwards travel, standstill and a completed transition to walking bypass it.
Jump/landing resets the filter; incompatible local-player fallback also resets it.
Time discontinuities reseed rather than replay old inertia. No per-frame allocations are added.

Validation: pure JVM tests cover bypass, symmetry, correction bounds, repeated frame submission,
30/60/144 FPS response to a fixed target, run blend, resets, invalid data and real YAML loading.
Build success does not establish visual quality. Startup log records the effective settings.

F5: side/rear forward run, walk/run transitions, stop/start, reversal, strafe, jump and sword use.
Look for subtle arm follow without shoulder separation or changes to torso bob/head look.
Set arm-follow-ticks to 0.0 and restart for A/B or rollback. Foot locking and full-body
acceleration/turn leaning are not implemented by this small shoulder-pitch change.
