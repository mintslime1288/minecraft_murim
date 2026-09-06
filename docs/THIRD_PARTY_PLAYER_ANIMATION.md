# Third-party player animation provider

MurimRPG supports a client-only provider switch in `config/murimrpg/pal-player-animation.yml`.
MurimRPG keeps a stable vanilla-player baseline by default. Its PAL+Bendable Cuboids skin-joint
motion set is available for deliberate testing; the installed `cpa`
(Custom Player Animations) JAR is retained but does not take control unless the user explicitly sets
`third-party-provider.prefer-when-installed: true` and restarts the client. This prevents a silent
provider takeover that would bypass MurimRPG's walk, run, jump, punch, and wooden-sword clips. The
normal Minecraft skin, held items, hitbox, server movement, combat authority, currency, EXP, and
training-dummy GLB are unaffected.

PAL clip validation is intentionally delayed until the first client resource reload has completed.
Validating during `CLIENT_STARTED` can race the resource manager and incorrectly report all clips as
missing, which would silently restore vanilla rendering.

## Local test stack

| Component | Version | Side | Purpose |
| --- | --- | --- | --- |
| Custom Player Animations | 5.9.8-fabric+26.2 | Client | Smooth movement, jump, action, and tool poses |
| Player Animation Library | 1.2.6+mc.26.2 | Client | Required animation backend |
| Cloth Config API | 26.2.155+fabric | Client | CPA configuration dependency |
| Fabric API | 0.158.0+26.2 | Client | Existing Fabric platform dependency |

Do not install CPA or Cloth Config in the dedicated server `mods` directory: CPA is client-only and
does not alter MurimRPG's server authority. A player without it joins normally and uses vanilla poses.

## Licensing

CPA is listed as CC-BY-NC-SA-4.0 by its official Modrinth page. It is acceptable for the local test
installation documented here, but it must not be bundled in a commercial MurimRPG launcher or
redistributed as part of a commercial modpack without a separate permission review. Player Animation
Library and Bendable Cuboids use MIT notices; preserve their notices when distributing them.

## Safety fallback

Set `third-party-provider.prefer-when-installed: true` and restart the client only when you want CPA
to replace MurimRPG's own motion provider. The player GLB experiment remains disabled by
`player-model.yml` and is not needed by either provider.
