package net.barttolo.bwe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Standalone Java 17 tests; uses disposable files, never Minecraft worlds. */
public final class TerraformBackupFileTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejectsIO(Checked operation, String message) throws Exception {
        try { operation.run(); throw new AssertionError("Expected IOException: " + message); }
        catch (IOException expected) { checks++; }
    }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("bwe-backup-test-");
        try {
            Path target = dir.resolve("nested/backup.bwe");
            byte[] data = new byte[2048];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
            TerraformBackupFile.writeNew(target, data);
            check(Files.isRegularFile(target), "backup created in new directory");
            check(Arrays.equals(data, TerraformBackupFile.readVerified(target)), "round trip");
            rejectsIO(() -> TerraformBackupFile.writeNew(target, new byte[] {9}),
                "never replace existing snapshot");
            check(Arrays.equals(data, TerraformBackupFile.readVerified(target)), "existing data preserved");
            byte[] stored = Files.readAllBytes(target);
            byte[] corrupt = stored.clone();
            corrupt[corrupt.length - 1] ^= 1;
            Path damaged = dir.resolve("damaged.bwe");
            Files.write(damaged, corrupt);
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "corrupted payload");
            Files.write(damaged, Arrays.copyOf(stored, stored.length - 1));
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "truncated payload");
            corrupt = stored.clone(); corrupt[0] ^= 1;
            Files.write(damaged, corrupt);
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "invalid magic");
            corrupt = stored.clone(); corrupt[7] ^= 1;
            Files.write(damaged, corrupt);
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "invalid version");
            corrupt = stored.clone(); corrupt[11] ^= 1;
            Files.write(damaged, corrupt);
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "length mismatch");
            Files.write(damaged, Arrays.copyOf(stored, stored.length + 1));
            rejectsIO(() -> TerraformBackupFile.readVerified(damaged), "trailing bytes");
            try { TerraformBackupFile.writeNew(dir.resolve("empty.bwe"), new byte[0]);
                throw new AssertionError("Expected invalid empty backup"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(!Files.exists(dir.resolve("empty.bwe")), "empty backup not published");
            try (var entries = Files.list(target.getParent())) {
                check(entries.noneMatch(p -> p.getFileName().toString().endsWith(".pending")),
                    "no temporary backups left");
            }
            System.out.println("OK: " + checks + " terraform backup file checks (Java 17)");
        } finally {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ex) { throw new RuntimeException(ex); }
                });
            }
        }
    }
}
