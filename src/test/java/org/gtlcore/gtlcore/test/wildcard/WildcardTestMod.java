package org.gtlcore.gtlcore.test.wildcard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod("gtlcore_wildcard_test")
public final class WildcardTestMod {

    public WildcardTestMod() {
        MinecraftForge.EVENT_BUS.addListener(this::runTests);
    }

    private void runTests(ServerStartedEvent event) {
        List<Result> results = new ArrayList<>();
        try {
            boolean expectAbsent = Boolean.getBoolean("gtlcore.test.wildcardAbsent");
            boolean present = ModList.get().isLoaded("wildcard_pattern");
            if (present == expectAbsent) {
                throw new AssertionError("Unexpected Wildcard Pattern presence: " + present);
            }
            if (expectAbsent) {
                results.add(new Result("optional_dependency_absent", true, "GTLCore reached ServerStartedEvent"));
            } else {
                WildcardRegression.run(event.getServer(), results);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            results.add(new Result("test_harness", false, failure.toString()));
        }
        try {
            Files.writeString(Path.of("wildcard-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot write wildcard test results", failure);
        } finally {
            event.getServer().halt(false);
        }
    }

    public record Result(String name, boolean passed, String detail) {}
}
