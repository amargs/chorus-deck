package org.amargs.chorusdeck.model;

import org.apache.poi.sl.usermodel.PictureData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

public record Interval(Kind kind, Path image, String text) {

    public enum Kind {
        VAZIO,
        IMAGEM,
        TEXTO,
        TEXTO_IMAGEM
    }

    public static Interval blank() {
        return new Interval(Kind.VAZIO, null, null);
    }

    public static Interval parse(String raw, Path image, String text) {
        Kind kind = kind(raw);
        boolean needsImage = kind == Kind.IMAGEM || kind == Kind.TEXTO_IMAGEM;
        boolean needsText = kind == Kind.TEXTO || kind == Kind.TEXTO_IMAGEM;
        if (needsImage && image == null) {
            throw new IllegalArgumentException("--interval " + label(kind) + " requires --interval-image.");
        }
        if (!needsImage && image != null) {
            throw new IllegalArgumentException("--interval-image is only valid for imagem and texto+imagem.");
        }
        if (needsImage) {
            requireReadable(image);
        }
        if (needsText && (text == null || text.isBlank())) {
            throw new IllegalArgumentException("--interval " + label(kind) + " requires --interval-text.");
        }
        if (!needsText && text != null) {
            throw new IllegalArgumentException("--interval-text is only valid for texto and texto+imagem.");
        }
        return new Interval(kind, image, needsText ? text : null);
    }

    public boolean needsImage() {
        return kind == Kind.IMAGEM || kind == Kind.TEXTO_IMAGEM;
    }

    public boolean needsText() {
        return kind == Kind.TEXTO || kind == Kind.TEXTO_IMAGEM;
    }

    public PictureData.PictureType pictureType() {
        if (image == null) {
            throw new IllegalArgumentException("Interval image is missing.");
        }
        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return PictureData.PictureType.PNG;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return PictureData.PictureType.JPEG;
        }
        if (name.endsWith(".gif")) {
            return PictureData.PictureType.GIF;
        }
        throw new IllegalArgumentException("Interval image must be png, jpg, or gif: " + image.getFileName());
    }

    private static void requireReadable(Path image) {
        try (InputStream in = Files.newInputStream(image)) {
            in.read();
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("Interval image not found: " + image);
        } catch (AccessDeniedException e) {
            throw new IllegalArgumentException("Interval image cannot be read: " + image);
        } catch (IOException e) {
            throw new IllegalArgumentException("Interval image cannot be read: " + image);
        }
    }

    private static Kind kind(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("vazio")) {
            return Kind.VAZIO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "imagem" -> Kind.IMAGEM;
            case "texto" -> Kind.TEXTO;
            case "texto+imagem" -> Kind.TEXTO_IMAGEM;
            default -> throw new IllegalArgumentException(
                    "Unknown interval: " + raw + ". Use vazio, imagem, texto, or texto+imagem.");
        };
    }

    private static String label(Kind kind) {
        return switch (kind) {
            case VAZIO -> "vazio";
            case IMAGEM -> "imagem";
            case TEXTO -> "texto";
            case TEXTO_IMAGEM -> "texto+imagem";
        };
    }
}
