package io.github.didacll.madre.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.jar.JarFile;

/** Host-internal filesystem mechanics shared by the two owner-managed JAR lifecycles. */
final class ManagedJarFiles {
    private ManagedJarFiles() { }

    static Path requireSourceJar(Path value) throws IOException {
        Path source = Objects.requireNonNull(value, "source").toAbsolutePath().normalize();
        if (!source.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("installation source must be a .jar file: " + source);
        }
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new IllegalArgumentException("installation source is not a readable regular file: "
                    + source);
        }
        try (JarFile archive = new JarFile(source.toFile(), true)) {
            archive.size();
        }
        return source;
    }

    static Path stage(Path source, Path ownerDirectory) throws IOException {
        Path directory = Objects.requireNonNull(ownerDirectory, "ownerDirectory")
                .toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory)) {
            throw new IOException("owner artifact root is not a directory: " + directory);
        }
        Path staged = Files.createTempFile(directory, ".madre-install-", ".jar.tmp");
        boolean copied = false;
        try {
            Files.copy(Objects.requireNonNull(source, "source"), staged,
                    StandardCopyOption.REPLACE_EXISTING);
            copied = true;
            return staged;
        } finally {
            if (!copied) Files.deleteIfExists(staged);
        }
    }

    static void commit(Path staged, Path destination) throws IOException {
        Path source = Objects.requireNonNull(staged, "staged").toAbsolutePath().normalize();
        Path target = Objects.requireNonNull(destination, "destination").toAbsolutePath().normalize();
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static Path managedPath(Path ownerDirectory, String domain, String canonicalIdentity) {
        Path directory = Objects.requireNonNull(ownerDirectory, "ownerDirectory")
                .toAbsolutePath().normalize();
        return directory.resolve(managedFileName(domain, canonicalIdentity));
    }

    static Path requireManagedPath(Path ownerDirectory, String domain, String canonicalIdentity,
            Path artifact) {
        Path actual = Objects.requireNonNull(artifact, "artifact").toAbsolutePath().normalize();
        Path expected = managedPath(ownerDirectory, domain, canonicalIdentity);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(domain + " " + canonicalIdentity
                    + " is discovered from a manually placed owner JAR (" + actual.getFileName()
                    + "); MADRE will not replace or delete it. Remove that file manually first if intended");
        }
        return actual;
    }

    static String managedFileName(String domain, String canonicalIdentity) {
        String label = Objects.requireNonNull(domain, "domain");
        String identity = Objects.requireNonNull(canonicalIdentity, "canonicalIdentity");
        return label + "-" + digest(identity.getBytes(StandardCharsets.UTF_8)).substring(0, 24)
                + ".jar";
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (var input = Files.newInputStream(Objects.requireNonNull(path, "path"))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String digest(byte[] bytes) {
        MessageDigest digest = sha256();
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
