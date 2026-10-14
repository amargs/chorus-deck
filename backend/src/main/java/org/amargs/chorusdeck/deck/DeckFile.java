package org.amargs.chorusdeck.deck;

import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Writes the generated deck, and refuses to overwrite a file PowerPoint still has open. */
public final class DeckFile {

    private DeckFile() {
    }

    public static void ensureWritable(Path outputFile) throws IOException {
        if (Files.notExists(outputFile)) {
            return;
        }
        try (FileChannel probe = FileChannel.open(outputFile, StandardOpenOption.WRITE)) {
            // Opening for write fails when PowerPoint already has the deck open.
        } catch (FileSystemException e) {
            if (isLocked(e)) {
                throw open(outputFile, e);
            }
            throw e;
        }
    }

    public static void write(XMLSlideShow show, Path outputFile) throws IOException {
        try (OutputStream out = Files.newOutputStream(outputFile)) {
            show.write(out);
        } catch (FileSystemException e) {
            if (isLocked(e)) {
                throw open(outputFile, e);
            }
            throw e;
        }
    }

    static boolean isLocked(FileSystemException e) {
        if (e instanceof NoSuchFileException) {
            return false;
        }
        if (e instanceof AccessDeniedException) {
            return true;
        }
        String reason = e.getReason();
        if (reason == null) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("used by another process")
                || lower.contains("access is denied")
                || lower.contains("text file busy")
                || lower.contains("device or resource busy");
    }

    private static IllegalArgumentException open(Path outputFile, Exception cause) {
        return new IllegalArgumentException(
                "Can't write " + outputFile.toAbsolutePath() + " because it is open. Close it and run generate again.",
                cause);
    }
}
