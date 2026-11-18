package io.github.displaycustom;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinExamplesTest {
    @Test void shippedExamplesPassServerValidation() throws Exception {
        var resources = new HashSet<String>();
        try (var pack = new ZipInputStream(getClass().getResourceAsStream("/resourcepacks/display-custom-colors-0.3.0.zip"))) {
            for (var entry = pack.getNextEntry(); entry != null; entry = pack.getNextEntry()) {
                resources.add(entry.getName());
            }
        }
        for (String name : List.of("pelican_bicycle", "testdoll", "whale_girl")) {
            try (var reader = new InputStreamReader(
                    getClass().getResourceAsStream("/models/" + name + ".json"), StandardCharsets.UTF_8)) {
                var model = Model.read(reader).validate();
                assertEquals(name, model.name());
                for (var part : model.parts()) {
                    String key = part.itemModel();
                    if (key != null && key.startsWith("display_custom:colors/")) {
                        assertTrue(resources.contains("assets/display_custom/items/" + key.split(":", 2)[1] + ".json"), key);
                    }
                }
            }
        }
    }

    @Test void upgradeArchivesOnlyExactOldExamples(@TempDir Path data) throws Exception {
        Path models = Files.createDirectory(data.resolve("models"));
        Path original = models.resolve("demo.json");
        try (var fixture = getClass().getResourceAsStream("/fixtures/demo.json")) {
            Files.copy(fixture, original);
        }
        Path edited = Files.writeString(models.resolve("motion_demo.json"), "{\"name\":\"motion_demo\"}");
        Path custom = Files.writeString(models.resolve("custom.json"), "{\"name\":\"custom\"}");

        for (int run = 0; run < 2; run++) {
            try (var manifest = getClass().getResourceAsStream("/retired-examples.sha256")) {
                RetiredExamples.archive(models, manifest);
            }
        }

        assertFalse(Files.exists(original));
        assertTrue(Files.isRegularFile(data.resolve("retired-examples/demo.json")));
        assertEquals("{\"name\":\"motion_demo\"}", Files.readString(edited));
        assertTrue(Files.isRegularFile(custom));
    }

    @Test void whaleGirlUsesAimingWhileHoldingAndChargingWithSeparateRules() throws Exception {
        try (var reader = new InputStreamReader(
                getClass().getResourceAsStream("/models/whale_girl.json"), StandardCharsets.UTF_8)) {
            var whale = Model.read(reader).validate();
            var aiming = ItemAction.bestMatch(whale, "hold", "CROSSBOW", null);
            assertEquals("hold_crossbow", aiming.name());
            assertTrue(aiming.rank() > ItemAction.bestMatch(whale, "hold", "IRON_SWORD", null).rank());
            assertEquals("use_crossbow", ItemAction.select(whale, "charge", "CROSSBOW", null));
            for (String name : List.of("hold_crossbow", "charge_spear", "use_shield", "use_bow")) {
                var clip = whale.animations().get(name);
                assertTrue(clip.tracks().containsKey("right_held_item"));
                assertTrue(ItemAction.mirrored(clip).tracks().containsKey("left_held_item"));
            }
        }
    }

    @Test void hurtExpressionOverlaysWalkingAndItemUseWithItsOwnClock() throws Exception {
        try (var reader = new InputStreamReader(
                getClass().getResourceAsStream("/models/whale_girl.json"), StandardCharsets.UTF_8)) {
            var whale = Model.read(reader).validate();
            var clips = whale.animations();
            var reaction = new Model.Layer(clips.get("hurt"), .15);
            var action = new Model.Layer(clips.get("charge_spear"), 1.5);
            assertTrue(reaction.clip().isHurt());
            assertTrue(reaction.clip().runsInParallel());
            for (String id : List.of("right_held_item", "right_shoe")) {
                var part = whale.parts().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
                var original = whale.matrix(part, List.of("walk"), .28, true, 0, 0, action, null);
                assertEquals(original, whale.matrix(part, List.of("walk"), .28, true, 0, 0, action, reaction));
            }
            var eye = whale.parts().stream().filter(p -> p.id().equals("left_eye_white")).findFirst().orElseThrow();
            assertFalse(whale.motion(eye, clips.get("walk"), .28, true, action, reaction).visible());
            assertTrue(whale.motion(eye, clips.get("walk"), .28, true, action, null).visible());
        }
    }
}
