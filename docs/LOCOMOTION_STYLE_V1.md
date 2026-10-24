# Locomotion style V1

Requested directly by the user after F5 feedback: running resembles fast walking, idle is static,
and the torso rocks sideways instead of advancing the shoulder associated with the forward arm.
This is an original modification of the project's existing clips, not an Epic Fight asset/rig port.

## Evidence and changes

The run V3 source elbows are only -3..-12 degrees. Runtime style adds a speed-scaled -40-degree
bias, producing -43..-52 at full gait weight. Knees get 1.5 gain before existing 1.35 retarget,
peaking at 52.65 degrees; thighs get 1.12 and upper arms 1.15. Timing/phase is retained. Body
pitch adds 4 degrees, reaching 12.25..13.45 before transitions. This is a pose refinement:
it does not create flight phases, foot locking or new gameplay sprint speed.

Torso roll including the existing contact accent is scaled to 15%. Final arm pitch difference
drives bounded, softly saturated Y rotation. In mapped Minecraft 26.2 ModelPart ZYX order,
right shoulder x=-5 advances toward model -Z under negative yaw. Thus (rightPitch-leftPitch)
has the correct sign. Yaw uses final arms after arm inertia; existing body pitch/roll smoothing
is retained. The head world-look compensation runs last. No pivots, scale or limb geometry change.

Idle has a 2-degree forward lean and 12-degree elbow bend, plus 0.45-degree rotation-only breath
over four seconds. Legs are not changed. Idle fades using raw walkAnimationSpeed, so pure strafe
is not mistaken for standing. Existing takeoff/landing ground-pose sampling includes the styled
ground pose; airborne jump frames themselves are not restyled.

## Configuration and verification

All three groups under motion-style are opt-in (run.enabled / torso.enabled / idle.enabled).
Restart the client after editing. Runtime run limits for this trial: knee/elbow 55, body 15 degrees.
The existing clips remain below their former limits when the style is off, enabling A/B without
changing assets. Disable each group's enabled flag independently to isolate the result.

Tests measure source clip bounds and half-cycle symmetry, ZYX shoulder direction/spacing, head
world orientation, smooth yaw bounds, idle fade, breathing period, disabled/invalid input behavior.
Visual quality and mesh overlap still require F5 at idle, walk, run, transitions, reverse, strafe,
jump and sword use. A fixed-hip, rotation-only rig cannot guarantee planted feet throughout a run.
