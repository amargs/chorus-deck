package org.amargs.chorusdeck.deck;

import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckFileTest {

    @Test
    void writtenDeckThumbnailIsTheFirstSlide() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-thumb");
        Path out = dir.resolve("deck.pptx");
        Dimension page;
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            page = ppt.getPageSize();
            cover(ppt, page);
            DeckFile.write(ppt, out);
        }
        BufferedImage thumb = thumbnail(out);
        assertEquals(256, thumb.getWidth());
        assertEquals(expectedHeight(page), thumb.getHeight());
        assertRed(thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() / 2));
        assertEquals(1, thumbnailRels(out));
    }

    @Test
    void templateThumbnailIsReplaced() throws Exception {
        Path dir = Files.createTempDirectory("chorus-deck-thumb");
        Path out = dir.resolve("deck.pptx");
        byte[] original = readThumbnail(template());
        Dimension page;
        try (var in = Files.newInputStream(template()); XMLSlideShow ppt = new XMLSlideShow(in)) {
            page = ppt.getPageSize();
            while (!ppt.getSlides().isEmpty()) {
                ppt.removeSlide(0);
            }
            cover(ppt, page);
            DeckFile.write(ppt, out);
        }
        BufferedImage thumb = thumbnail(out);
        assertEquals(256, thumb.getWidth());
        assertEquals(expectedHeight(page), thumb.getHeight());
        assertRed(thumb.getRGB(thumb.getWidth() / 2, thumb.getHeight() / 2));
        assertEquals(1, thumbnailRels(out));
        assertFalse(Arrays.equals(readThumbnail(out), original));
    }

    private static void cover(XMLSlideShow ppt, Dimension page) {
        XSLFSlide slide = ppt.createSlide();
        XSLFAutoShape box = slide.createAutoShape();
        box.setShapeType(ShapeType.RECT);
        box.setAnchor(new Rectangle(0, 0, page.width, page.height));
        box.setFillColor(Color.RED);
        box.setLineColor(Color.RED);
    }

    private static int expectedHeight(Dimension page) {
        return Math.max(1, (int) Math.round(256.0 * page.getHeight() / page.getWidth()));
    }

    private static void assertRed(int rgb) {
        Color color = new Color(rgb);
        assertTrue(color.getRed() > 200 && color.getGreen() < 40 && color.getBlue() < 40,
                "thumbnail center was " + color.getRed() + "," + color.getGreen() + "," + color.getBlue());
    }

    private static BufferedImage thumbnail(Path pptx) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(readThumbnail(pptx)));
    }

    private static byte[] readThumbnail(Path pptx) throws Exception {
        try (FileSystem zip = FileSystems.newFileSystem(pptx)) {
            return Files.readAllBytes(zip.getPath("/docProps/thumbnail.jpeg"));
        }
    }

    private static int thumbnailRels(Path pptx) throws Exception {
        String rels;
        try (FileSystem zip = FileSystems.newFileSystem(pptx)) {
            rels = Files.readString(zip.getPath("/_rels/.rels"));
        }
        int count = 0;
        Matcher matcher = Pattern.compile("Type=\"" + Pattern.quote(PackageRelationshipTypes.THUMBNAIL) + "\"").matcher(rels);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Path template() {
        Path data = Path.of("data", "template_4x3.pptx");
        if (Files.isRegularFile(data)) {
            return data;
        }
        Path nested = Path.of("backend", "data", "template_4x3.pptx");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("template_4x3.pptx not found");
    }
}
