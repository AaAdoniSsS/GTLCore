package org.gtlcore.gtlcore.integration.ae2.chamber;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractDisplayPart;
import appeng.util.Platform;

public final class MEChamberManagerTerminalPart extends AbstractDisplayPart {

    private static final ResourceLocation MODEL_OFF = GTLCore.id("part/me_chamber_manager_terminal_off");
    private static final ResourceLocation MODEL_ON = GTLCore.id("part/me_chamber_manager_terminal_on");
    private static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    private static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    private static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    public MEChamberManagerTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    public static void registerModels() {
        PartModels.registerModels(MODEL_OFF, MODEL_ON);
    }

    @Override
    public boolean onPartActivate(Player player, InteractionHand hand, Vec3 pos) {
        if (!super.onPartActivate(player, hand, pos) && !isClientSide() && player instanceof ServerPlayer serverPlayer &&
                getMainNode().isActive() && Platform.hasPermissions(getHost().getLocation(), player)) {
            MEChamberManagerTerminalMenu.open(serverPlayer, this);
        }
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }
}
