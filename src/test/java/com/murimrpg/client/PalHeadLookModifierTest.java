package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.*;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.junit.jupiter.api.Test;

class PalHeadLookModifierTest {
    @Test void restoresLiveLookEvenWhenUnderlyingSnapshotReplacesHead() {
        var modifier=new PalHeadLookModifier();
        modifier.setAnim(new AbstractModifier() {
            @Override public void get3DTransform(PlayerAnimBone bone) { bone.rotation.set(2,2,2); }
        });
        var head=new PlayerAnimBone("head");head.rotation.set(.4f,-.7f,0);
        modifier.get3DTransform(head);
        assertEquals(.4f,head.rotation.x);assertEquals(-.7f,head.rotation.y);assertEquals(0,head.rotation.z);
        var arm=new PlayerAnimBone("right_arm");modifier.get3DTransform(arm);
        assertEquals(2,arm.rotation.x);
    }
}
