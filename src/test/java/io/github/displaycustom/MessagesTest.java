package io.github.displaycustom;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessagesTest {
    @TempDir Path directory;

    @Test void selectsPlayerLanguageAndPreservesPlaceholderValues() throws Exception {
        var messages = new Messages(directory, "zh_cn", true);
        assertEquals("自定义展示实体", messages.text(Locale.SIMPLIFIED_CHINESE, "plugin.name"));
        assertEquals("Display Custom", messages.text(Locale.UK, "plugin.name"));
        var error = Messages.failure("error.model.named", "$1\\{2}");
        assertEquals("Model not found: $1\\{2}.", messages.error(Locale.US, error));
        assertEquals("模型不存在: $1\\{2}", messages.error(Locale.CHINA, error));
    }

    @Test void loadsOverridesAndFallsBackToTheServerAndBuiltInLanguages() throws Exception {
        Files.writeString(directory.resolve("fr_fr.properties"), "plugin.name=Présentation\n");
        Files.writeString(directory.resolve("en_us.properties"), "command.list.empty=No displays.\n");

        var french = new Messages(directory, "en_us", true);
        assertEquals("Présentation", french.text(Locale.FRANCE, "plugin.name"));
        assertEquals("No displays.", french.text(Locale.FRANCE, "command.list.empty"));
        assertEquals("Display Custom", french.text(Locale.GERMANY, "plugin.name"));

        var fixed = new Messages(directory, "zh_cn", false);
        assertEquals("自定义展示实体", fixed.text(Locale.US, "plugin.name"));
    }
}
