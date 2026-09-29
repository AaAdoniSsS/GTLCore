package org.gtlcore.gtlcore.client.ae2.graph;

import org.gtlcore.gtlcore.GTLCore;

import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only browser preferences; never changes crafting or server configuration. */
public final class GraphViewSettings {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static GraphViewSettings instance;
    public boolean compact = true;
    public boolean showAmounts = true;
    public boolean screenshotAmounts = true;
    public boolean missingOnlyByDefault;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("gtlcore-crafting-view.json");
    }

    public static GraphViewSettings get() {
        if (instance == null) {
            try {
                if (Files.isRegularFile(file())) {
                    try (var reader = Files.newBufferedReader(file())) {
                        instance = JSON.fromJson(reader, GraphViewSettings.class);
                    }
                }
            } catch (IOException | RuntimeException e) {
                GTLCore.LOGGER.warn("Could not read crafting view preferences", e);
            }
            if (instance == null) instance = new GraphViewSettings();
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), JSON.toJson(this));
        } catch (IOException e) {
            GTLCore.LOGGER.warn("Could not save crafting view preferences", e);
        }
    }
}
