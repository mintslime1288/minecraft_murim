# PAL natural motion v2 — 2026-09-14

## Evidence and scope

The previously installed JAR and pre-change build both hash to
`AFDE76004F91AE8BA15AF3F0BA35EA6ACCF88F0664BD6AA547BC8CC5CE57E3A9`.
The client's prior log includes head-channel confirmation at 09:09:05 and
shoulder follow at 09:09:13. Thus the reported visual problem was not explained
by a missing installation. The existing movement and combat WIP is preserved.

Confirmed implementation issues:

- PAL submits arms before torso; the old shoulder modifier caches torso at
  submission time and uses it on the next render call. Delay therefore depends
  on render order/rate. It also adds torso pitch to clips already labelled and
  tested as containing baked shoulder compensation.
- Walk/run resources change quadratic easing at their interior poses. Matching
  first/last values alone does not guarantee matching angular velocities.
- PAL walk/run switches start different clips at zero; the previous controller
  does not retain a shared support-leg phase.
- The previous PAL integration supplies speed but no local movement direction.
  The older `LocomotionDirection` implementation is bypassed while PAL is active.
- Every ground-to-air edge starts takeoff, including downward steps; every
  air-to-ground edge starts the full landing clip.
- Disabling authored head channels does not protect against the fade modifier's
  stored pose. The new outer modifier preserves the incoming Mojang head rotation
  after all inner modifiers, in both locomotion and combat layers.

Sources inspected locally: installed PAL API signatures, PAL 1.2.6 source,
`PlayerModelMixin`, `PlayerAnimatorLoader`, `AnimationController`,
`AbstractFadeModifier`, Bendable Cuboids part/held-item integration, and mapped
26.2 bytecode. `LivingEntity.yBodyRot` is present. Humanoid swing frequency is
`0.6662f`. The yaw-zero forward basis is +Z, with horizontal forward
`(-sin(yaw), cos(yaw))`, also used by the existing direction implementation.

## Changes

`PalSmoothClip` reads the existing nine MurimRPG locomotion resources from the
client resource manager. It validates radians, times, finite bounded rotations,
hinge signs, loop closure, and absence of position/scale tracks. It evaluates
shape-preserving cubic Hermite curves with periodic derivatives. Original JSON,
Blender and GLB files are unchanged. No third-party animation is imported.

`PalNaturalMotion` computes all five body-part poses together using game ticks
and partial ticks. Re-reading parts or held-item transforms does not advance
the clock. Shared phase survives walking/running transitions; period response
uses the analytical integral of exponential smoothing. State changes blend from
the last displayed pose. Resource replacement, invalid time, incompatible state,
large time gaps, dimension changes and large teleports reset per-avatar state.

`PalNaturalMotionModifier` applies that pose inside the locomotion PAL layer.
The existing higher-priority combat layer remains authoritative. There is one
renderer, the same Minecraft skin/geometry and Bendable Cuboids hinge pipeline.
It does not alter position, scale, camera, physics, hitbox or server data.

Default tuning in `pal-natural-motion.yml`:

- Walk full cycle: 32 ticks (previous authored loop: 28); deliberately moderate
  cadence instead of unrestricted playback scaling with instantaneous velocity.
- Run: 16 ticks; fast run: 14 ticks. Separate authored run poses remain in use.
- Torso roll: 35% of authored amount. Extra run forward lean: 2 degrees, also
  supplied to shoulders once. The old cached torso correction is bypassed.
- Idle: elbow flexion reduced to 65%; existing upper-body breathing retained,
  while legs remain at the authored initial stance.
- Forward/back/side travel: filtered body-local direction controls swing plane;
  backward fore/aft swing reverses while knee bending retains its valid sign.
- Moving takeoff: carries the actual limb asymmetry with time-based decay.
  Moving landing recovers toward the continuing gait instead of a stationary pose.
- Downward steps shorter than four ticks do not trigger takeoff/full landing.

## Comparison, diagnostics and limitations

Runtime file: `.minecraft/config/murimrpg/pal-natural-motion.yml`.
Restart the Java Fabric client after changes. `enabled: false` chooses the
previous PAL clip/shoulder path for comparison; the head snapshot protection
remains as a correctness fix. No settings hot reload is claimed.

`diagnostics: true` reports state/readiness/speed/phase for the local player at
most every 40 ticks. A separate once-per-avatar submission line identifies the
new actual render path. Resource validation and submission are separate evidence.

This is still a rotation-only approximation: no terrain IK or foot-position
locking is added. Slower walking can increase foot sliding at unchanged gameplay
speed; precise backward contact and side-step anatomy need in-game evaluation.
Airborne carry preserves takeoff asymmetry but is not a newly authored complete
directional jump library. Original attack timing is preserved, not redesigned.
Skins, armour, crouch/swim/riding/death and held-item visual regression must be
checked in F5. Fallback appearance is not guaranteed identical for all equipment.

Numerical tests do not prove subjective quality. Required F5 comparison: idle
look, ten-second walk/stop, run transition, backward/side movement, moving jump,
moving landing, small downward step, boxing/wooden sword and fallback states.

## Reference and validation status

The user's two walk/run YouTube links were attempted but could not be fetched;
no viewing or frame analysis is claimed. The existing user screenshots remain
symptom references. Idea-board checker returned `UNCHANGED` with SHA-256
`993a50346e34c6ac851c6e04ba0fe7c5d784ec699e20e177f6061dc39c4491eb`.

The first test/build with the new path and regression tests passed: 333 tests,
zero failures. Deployment hash and fresh runtime/F5 evidence belong in the
dated shared review report, not inferred from build success.

## 2026-10-18 기본 플레이어 전방 기울기 조정

현재 개발 게임은 Wuxia GLB를 설정으로 끄고, 현재 스킨의 Minecraft 기본 플레이어
모델과 PAL 모션을 사용한다. 사용자가 F5 달리기에서 몸이 앞으로 과하게 숙여진다고
확인했다. `pal_run.json`과 `pal_fast_run.json`의 9개 키프레임에서 몸통 pitch만
절반으로 줄였다. 달리기는 약 12.6–15.8도에서 6.3–7.9도, 빠른 달리기는
약 17.5–20.9도에서 8.7–10.5도가 됐다. `extra-run-lean-degrees: 2`와
걷기/달리기 주기, 팔·다리 키프레임, 서버 이동은 유지한다.

원본 클립은 `MurimRPG-Claude-Collab/03-codex-review`에 `.bak`으로 보존했다.
소스 스냅샷 검사는 통과했다. 전체 빌드의 기존 외부 자산 누락 13건은 그대로다.
PAL은 F3+T만으로 안전하게 재등록되지 않으므로 클라이언트를 정상 종료하고
재실행해 검증한다. 기울기의 시각적 판정은 재실행 후 같은 달리기를 F5에서
비교해야 한다.

첫 수정 후에도 사용자가 앞뒤 숙임이 과하다고 재확인했다. 코드 경로를 다시 확인하니
`PalNaturalMotion.sampleGait`가 달리기 클립의 torso pitch 위에 설정의 추가 전방 기울기
2도를 더하고 있었다. 따라서 달리기 클립의 몸통 pitch를 약 2.1–2.6도, 빠른 달리기를
약 2.9–3.5도로 더 줄이고, 고개 pitch도 같은 비율로 낮췄다. 소스 기본 설정과 개발
설정의 `extra-run-lean-degrees`를 0도로 바꿔 중복 가산을 없앴다. 접지 순간의 1.2도
미만 짧은 pitch 변화는 그대로 남는다. 주기, 팔·다리, 이동 판정은 바꾸지 않았다.
첫 수정 상태의 파일 역시 검토 폴더에 별도 `.bak`으로 남겼다. 스냅샷 빌드는 통과했으며
새 클라이언트를 실행해 사용자 F5 체감 확인을 기다린다.

이후 사용자는 재실행한 달리기가 **너무 뻣뻣하게 서 있다**고 판정했다. 두 번째
기울기 수정은 이 이유로 시각 품질 승인 상태가 아니다. 다음 단계에서는 원본 파일
백업과 현재 F5 피드백을 함께 비교해 중간 범위를 검토해야 한다. 현재 키프레임을
완성된 최종 모션이라고 표시하지 않는다.
