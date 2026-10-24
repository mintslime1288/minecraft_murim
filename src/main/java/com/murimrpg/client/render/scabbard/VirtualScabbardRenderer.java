package com.murimrpg.client.render.scabbard;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws a scabbard in the empty off hand of a player holding the configured sword.
 *
 * <h2>Why there is no scabbard item</h2>
 * The scabbard is never an inventory slot, never a server item, never a drop, has no stack count,
 * no durability and no hit judgement. It exists only as an entry in the client's item-model
 * registry plus one {@link ItemStack} object held in a static field here, which is used purely as
 * the key the item-model pipeline needs: {@code ItemModelResolver} resolves a model from a stack,
 * and {@code ModelManager.getItemModel(Identifier)} cannot be reached without one on 26.2. That
 * object is created once at load, lives only in this class, and never touches a container, a
 * packet, the world or the player's inventory. Registering a real item was rejected because a
 * client-side item registration desynchronises the item registry against a vanilla server.
 *
 * <h2>Why the authored display transforms survive</h2>
 * Both render paths hand the work back to vanilla rather than positioning the model by hand.
 * Third person fills {@code ArmedEntityRenderState.leftHandItemState} (or the right one for a
 * left-handed player) and lets {@code ItemInHandLayer} place it, so the model's own
 * {@code thirdperson_lefthand} entry applies. First person replays vanilla's own off-hand call,
 * so {@code firstperson_lefthand} applies. Nothing in this class scales, rotates or translates
 * the model, which is what keeps the Blockbench save authoritative.
 */
public final class VirtualScabbardRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("MurimRPG-Scabbard");

    private static ScabbardSettings settings = ScabbardSettings.disabled();
    /** Render-only. Never enters a slot, a container, a packet or the world. */
    private static ItemStack scabbardRenderStack = ItemStack.EMPTY;
    private static Identifier scabbardModel;
    private static Identifier swordModel;
    private static ScabbardVisibility.Reason lastReason = ScabbardVisibility.Reason.DISABLED_IN_CONFIG;
    private static boolean missingModelLogged;
    private static boolean shownLogged;

    private VirtualScabbardRenderer() {
    }

    public static void loadResources(Minecraft client) {
        reload(FabricLoader.getInstance().getConfigDir().resolve("murimrpg"));
    }

    /** Separated so the reload command and tests share one path. */
    public static void reload(Path configDirectory) {
        settings = ScabbardSettings.load(configDirectory);
        scabbardModel = null;
        swordModel = null;
        scabbardRenderStack = ItemStack.EMPTY;
        missingModelLogged = false;
        shownLogged = false;
        lastReason = settings.enabled()
                ? ScabbardVisibility.Reason.MAIN_HAND_IS_NOT_THE_SWORD
                : ScabbardVisibility.Reason.DISABLED_IN_CONFIG;
        if (!settings.enabled()) {
            LOGGER.info("Virtual scabbard is disabled in {}", ScabbardSettings.FILE_NAME);
            return;
        }
        scabbardModel = parse(settings.scabbardItemModel());
        swordModel = parse(settings.swordItemModel());
        if (scabbardModel == null || swordModel == null) {
            settings = ScabbardSettings.disabled();
            lastReason = ScabbardVisibility.Reason.DISABLED_IN_CONFIG;
            LOGGER.warn("Virtual scabbard stays off: sword-scabbard.yml has an unusable identifier");
            return;
        }
        LOGGER.info("Virtual scabbard ready: {}", settings.describe());
    }

    private static ItemStack renderStack() {
        if (scabbardModel == null) return ItemStack.EMPTY;
        if (scabbardRenderStack.isEmpty()) {
            // CLIENT_STARTED fires before item components are bound on 26.2. Create the
            // render-only stack on the first render pass, after registry initialization.
            ItemStack stack = new ItemStack(Items.STICK);
            stack.set(DataComponents.ITEM_MODEL, scabbardModel);
            scabbardRenderStack = stack;
        }
        return scabbardRenderStack;
    }

    private static Identifier parse(String value) {
        if (!ScabbardSettings.isWellFormed(value)) return null;
        int colon = value.indexOf(':');
        try {
            return Identifier.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
        } catch (RuntimeException error) {
            return null;
        }
    }

    public static ScabbardSettings settings() {
        return settings;
    }

    /** True when an attack must leave the off arm alone; read by the PAL combat layer. */
    public static boolean rightArmOnlyAttack() {
        return settings.enabled() && settings.rightArmOnlyAttack();
    }

    public static String status() {
        if (!settings.enabled()) return "비활성: " + ScabbardSettings.FILE_NAME + " 꺼짐";
        return "활성 (" + lastReason + ")";
    }

    /**
     * True when the configured scabbard model is actually present in the loaded resources.
     * Without the resource pack the model manager hands back its missing-model placeholder, and
     * drawing that would hang a black and magenta cube off the hand.
     */
    private static boolean modelAvailable(Minecraft client) {
        if (scabbardModel == null) return false;
        var manager = client.getModelManager();
        if (manager == null) return false;
        boolean present = !(manager.getItemModel(scabbardModel) instanceof MissingItemModel);
        if (!present && !missingModelLogged) {
            missingModelLogged = true;
            LOGGER.warn("Scabbard model {} is not in the loaded resources; no scabbard is drawn. "
                    + "Install the resource pack or move the model into the mod jar.", scabbardModel);
        }
        return present;
    }

    private static boolean isConfiguredSword(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (settings.matchSwordTagInstead()) return stack.is(ItemTags.SWORDS);
        return swordModel != null && swordModel.equals(stack.get(DataComponents.ITEM_MODEL));
    }

    /** The decision for one player, shared by both render paths so they can never disagree. */
    private static ScabbardVisibility.Decision decide(Player player, boolean offHandEmpty) {
        var client = Minecraft.getInstance();
        var decision = ScabbardVisibility.decide(new ScabbardVisibility.Input(
                settings.enabled(),
                modelAvailable(client),
                isConfiguredSword(player.getMainHandItem()),
                offHandEmpty,
                player.isSpectator(),
                player.getMainArm() == HumanoidArm.RIGHT));
        lastReason = decision.reason();
        if (decision.visible() && !shownLogged) {
            shownLogged = true;
            LOGGER.info("Virtual scabbard drawn for the first time on the {} arm", decision.arm());
        }
        return decision;
    }

    /**
     * Third person, for the local player in F5 and for every other player alike.
     *
     * <p>Called at the tail of the armed-entity render-state extraction, it fills the off arm's
     * {@link ItemStackRenderState} and nothing else. The off arm's real {@code ItemStack} stays
     * the genuinely empty stack, so the arm pose, the use-item animation and every gameplay
     * reader still see an empty hand; only the drawn geometry is added. Vanilla's hand layer
     * gates on the render state rather than the stack, which is what makes this work without
     * faking an item.
     */
    public static void fillThirdPersonOffHand(LivingEntity entity, ArmedEntityRenderState state,
                                              ItemModelResolver resolver) {
        if (!settings.enabled() || !settings.thirdPerson()) return;
        if (!(entity instanceof Player player)) return;
        if (resolver == null || renderStack().isEmpty()) return;
        // Only fill an arm vanilla left empty. If anything is already there, leave it.
        boolean offHandEmpty = player.getOffhandItem().isEmpty();
        var decision = decide(player, offHandEmpty);
        if (!decision.visible()) return;
        ItemStackRenderState target = decision.arm() == ScabbardVisibility.Arm.LEFT
                ? state.leftHandItemState : state.rightHandItemState;
        if (!target.isEmpty()) return;
        ItemDisplayContext context = decision.arm() == ScabbardVisibility.Arm.LEFT
                ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        resolver.updateForLiving(target, scabbardRenderStack, context, entity);
    }

    /**
     * First person. Returns the stack to hand to vanilla's own off-hand arm call, or
     * {@link ItemStack#EMPTY} when nothing should be drawn.
     *
     * <p>Returning the stack rather than drawing here is deliberate: the caller replays vanilla's
     * exact off-hand invocation, so the equip height, the swing offset, the arm transform and the
     * {@code firstperson_lefthand} display entry are all vanilla's, not a reimplementation.
     */
    public static ItemStack firstPersonOffHandStack(Player player) {
        if (!settings.enabled() || !settings.firstPerson()) return ItemStack.EMPTY;
        if (renderStack().isEmpty()) return ItemStack.EMPTY;
        if (!player.getOffhandItem().isEmpty()) return ItemStack.EMPTY;
        return decide(player, true).visible() ? scabbardRenderStack : ItemStack.EMPTY;
    }
}
