package net.barttolo.bwe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Crash-conscious storage primitive for a FUTURE terraform BEFORE-state snapshot.
 * The caller owns serialization of block states and immutable column plans:
 * this class stores opaque bytes, NOT Minecraft NBT, and does not edit a world.
 * No entity or inventory backup is implied. No rollback is enabled by itself.
 *
 * A write must finish and be independently read/validated BEFORE the first
 * world edit. A failed atomic move is fatal; never fall back to a non-atomic
 * replacement. A filesystem/power-loss durability guarantee also requires
 * appropriate directory syncing and a reliable underlying storage device.
 */
public final class TerraformBackupFile {
    private static final int MAGIC = 0x42574542; // BWEB
    private static final int VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final long HEADER_BYTES = 3L * Integer.BYTES + DIGEST_BYTES;
    public static final int MAX_PAYLOAD_BYTES = 512 * 1024 * 1024;

    private TerraformBackupFile() {}

    private static byte[] digest(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    /**
     * Creates a new backup. Never intentionally overwrites an existing backup.
     * Call only from the single server-thread job coordinator; this API does
     * not serialize competing writers in other processes.
     */
    public static void writeNew(Path target, byte[] payload) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("Empty or oversized terraform backup");
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) throw new IllegalArgumentException("Missing backup directory");
        Files.createDirectories(parent);
        if (Files.exists(absolute)) throw new IOException("Terraform backup already exists: " + absolute);
        Path temporary = Files.createTempFile(parent, ".bwe-terraform-", ".pending");
        boolean committed = false;
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                 DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(payload.length);
                output.write(digest(payload));
                output.write(payload);
                output.flush();
                channel.force(true);
            }
            // No non-atomic fallback: an incomplete backup must not be published.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            committed = true;
        } catch (AtomicMoveNotSupportedException ex) {
            throw new IOException("Atomic terraform backup creation unsupported", ex);
        } finally {
            if (!committed) Files.deleteIfExists(temporary);
        }
    }

    /** Reject truncated, modified, mismatched-version and oversized snapshots. */
    public static byte[] readVerified(Path target) throws IOException {
        Objects.requireNonNull(target, "target");
        long size = Files.size(target);
        if (size < HEADER_BYTES || size > HEADER_BYTES + MAX_PAYLOAD_BYTES)
            throw new IOException("Invalid terraform backup size");
        try (DataInputStream input = new DataInputStream(Files.newInputStream(target))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid terraform backup signature");
            if (input.readInt() != VERSION) throw new IOException("Unsupported terraform backup version");
            int length = input.readInt();
            if (length <= 0 || length > MAX_PAYLOAD_BYTES || size != HEADER_BYTES + length)
                throw new IOException("Invalid terraform backup payload length");
            byte[] expected = input.readNBytes(DIGEST_BYTES);
            if (expected.length != DIGEST_BYTES) throw new IOException("Truncated backup checksum");
            byte[] payload = input.readNBytes(length);
            if (payload.length != length || !MessageDigest.isEqual(expected, digest(payload)))
                throw new IOException("Terraform backup checksum mismatch");
            if (input.read() != -1) throw new IOException("Trailing terraform backup bytes");
            return payload;
        }
    }
}
