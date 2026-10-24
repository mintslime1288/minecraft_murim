package com.murimrpg.client;

import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.NotNull;

/** Also protects mouse look from PAL's crossfade snapshots, not just authored head tracks. */
final class PalHeadLookModifier extends AbstractModifier {
    @Override public void get3DTransform(@NotNull PlayerAnimBone bone) {
        float x=bone.rotation.x, y=bone.rotation.y, z=bone.rotation.z;
        super.get3DTransform(bone);
        if("head".equals(bone.getName())) bone.rotation.set(x,y,z);
    }
}
