package org.gtlcore.gtlcore.integration.gtmt;

import org.gtlcore.gtlcore.client.gtmt.WirelessEnergyLocatorClient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Optional;

/** Encodes wireless-energy locator targets and delegates visual highlighting to the client. */
public final class WirelessEnergyLocator {

    public static final long HIGHLIGHT_DURATION_MILLIS = 15_000L;

    private static final String DATA_PREFIX = "gtlcore:wireless-energy-locator:";
    private static final String TARGET_PART_SEPARATOR = "\\|";
    private static final String COORDINATE_SEPARATOR = ",";
    private static final int TARGET_PART_COUNT = 2;
    private static final int COORDINATE_COUNT = 3;

    private WirelessEnergyLocator() {}

    public static String encode(ResourceKey<Level> dimension, BlockPos position) {
        return DATA_PREFIX + dimension.location() + "|" + position.getX() + COORDINATE_SEPARATOR +
                position.getY() + COORDINATE_SEPARATOR + position.getZ();
    }

    public static boolean isLocatorData(String data) {
        return data != null && data.startsWith(DATA_PREFIX);
    }

    public static void highlightOnClient(String data) {
        decode(data).ifPresent(target -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WirelessEnergyLocatorClient.highlight(target)));
    }

    private static Optional<Target> decode(String data) {
        String[] targetParts = data.substring(DATA_PREFIX.length()).split(TARGET_PART_SEPARATOR, TARGET_PART_COUNT);
        if (targetParts.length != TARGET_PART_COUNT) {
            return Optional.empty();
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(targetParts[0]);
        if (dimensionId == null) {
            return Optional.empty();
        }

        String[] coordinates = targetParts[1].split(COORDINATE_SEPARATOR, COORDINATE_COUNT);
        if (coordinates.length != COORDINATE_COUNT) {
            return Optional.empty();
        }

        try {
            BlockPos position = new BlockPos(
                    Integer.parseInt(coordinates[0].trim()),
                    Integer.parseInt(coordinates[1].trim()),
                    Integer.parseInt(coordinates[2].trim()));
            return Optional.of(new Target(ResourceKey.create(Registries.DIMENSION, dimensionId), position));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public record Target(ResourceKey<Level> dimension, BlockPos position) {}
}
