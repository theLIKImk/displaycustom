package io.github.displaycustom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RetiredExamples {
    private RetiredExamples() {}

    static void archive(Path modelDir, InputStream manifest) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path archive = modelDir.resolveSibling("retired-examples");
        for (String entry : new String(manifest.readAllBytes(), StandardCharsets.US_ASCII).lines().toList()) {
            String[] fields = entry.split(" ", 2);
            Path model = modelDir.resolve(fields[1]);
            if (!Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS) || Files.size(model) > 2 * 1024 * 1024) {
                continue;
            }
            String hash = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(model)));
            if (hash.equals(fields[0])) {
                Files.createDirectories(archive);
                Path destination = archive.resolve(fields[1]);
                if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(model, destination);
                }
            }
        }
    }
}
