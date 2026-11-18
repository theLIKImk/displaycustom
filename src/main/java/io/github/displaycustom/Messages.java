package io.github.displaycustom;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Messages {
    private static final String ENGLISH = "en_us";
    private static final String CHINESE = "zh_cn";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([0-9]{1,2})}");
    private static final Map<String, Properties> BUILTIN = Map.of(
        ENGLISH, builtin(ENGLISH), CHINESE, builtin(CHINESE));
    private final Map<String, Properties> catalogs = new HashMap<>();
    private final String serverLanguage;
    private final boolean playerLanguage;

    Messages(Path directory, String language, boolean playerLanguage) throws IOException {
        catalogs.putAll(BUILTIN);
        if (Files.isDirectory(directory)) {
            try (var files = Files.newDirectoryStream(directory, "*.properties")) {
                for (Path file : files) {
                    String name = normalize(file.getFileName().toString().replaceFirst("\\.properties$", ""));
                    if (!name.matches("[a-z]{2,3}(?:_[a-z0-9]{2,8})*") || !Files.isRegularFile(file)) continue;
                    Properties values = new Properties();
                    if (BUILTIN.containsKey(name)) values.putAll(BUILTIN.get(name));
                    try (var stream = Files.newInputStream(file)) {
                        values.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException error) {
                        throw new IOException("Invalid language file " + file.getFileName(), error);
                    }
                    catalogs.put(name, values);
                }
            }
        }
        String configured = normalize(language);
        serverLanguage = catalogs.containsKey(configured) ? configured : CHINESE;
        this.playerLanguage = playerLanguage;
    }

    static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    String language(Locale locale) {
        if (!playerLanguage || locale == null) return serverLanguage;
        String exact = normalize(locale.toLanguageTag());
        if (catalogs.containsKey(exact)) return exact;
        return switch (locale.getLanguage()) {
            case "en" -> ENGLISH;
            case "zh" -> CHINESE;
            default -> serverLanguage;
        };
    }

    String text(Locale locale, String key, Object... values) {
        String template = catalogs.get(language(locale)).getProperty(key);
        if (template == null) template = catalogs.get(serverLanguage).getProperty(key);
        if (template == null) template = catalogs.get(ENGLISH).getProperty(key);
        if (template == null) template = BUILTIN.get(ENGLISH).getProperty(key, key);
        return format(template, values);
    }

    String error(Locale locale, Throwable error) {
        if (error instanceof MessageException localized) return text(locale, localized.key, localized.values);
        if (error.getCause() instanceof MessageException localized) return text(locale, localized.key, localized.values);
        return Objects.toString(error.getMessage(), error.getClass().getSimpleName());
    }

    static MessageException failure(String key, Object... values) {
        return new MessageException(key, values);
    }

    private static String format(String template, Object[] values) {
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            int index = Integer.parseInt(match.group(1));
            return Matcher.quoteReplacement(index < values.length ? String.valueOf(values[index]) : match.group());
        });
    }

    private static Properties builtin(String name) {
        Properties values = new Properties();
        try (InputStream stream = Objects.requireNonNull(Messages.class.getResourceAsStream("/lang/" + name + ".properties"))) {
            values.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
        return values;
    }

    static final class MessageException extends IllegalArgumentException {
        private final String key;
        private final Object[] values;

        private MessageException(String key, Object[] values) {
            super(format(BUILTIN.get(ENGLISH).getProperty(key, key), values));
            this.key = key;
            this.values = values;
        }
    }
}
