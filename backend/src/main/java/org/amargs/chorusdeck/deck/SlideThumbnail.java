package org.amargs.chorusdeck.deck;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Windows Explorer shows {@code docProps/thumbnail.jpeg} from the package.
 * The template was saved from slide-master view, so that JPEG is the master
 * ("Click to edit Master title style") and was copied into every generated deck.
 */
final class SlideThumbnail {

    private static final String PART = "/docProps/thumbnail.jpeg";
    /** Same width PowerPoint stores for a 4:3 deck. */
    private static final int WIDTH = 256;

    private SlideThumbnail() {
    }

    static void apply(XMLSlideShow show) throws IOException {
        if (show.getSlides().isEmpty()) {
            return;
        }
        byte[] jpeg = jpeg(show);
        OPCPackage pkg = show.getPackage();
        try {
            PackagePartName name = PackagingURIHelper.createPartName(PART);
            PackagePart part = pkg.getPart(name);
            if (part == null) {
                part = pkg.createPart(name, "image/jpeg");
                pkg.addRelationship(name, TargetMode.INTERNAL, PackageRelationshipTypes.THUMBNAIL);
            }
            try (OutputStream out = part.getOutputStream()) {
                out.write(jpeg);
            }
        } catch (InvalidFormatException e) {
            throw new IOException("Could not store the slide thumbnail.", e);
        }
    }

    private static byte[] jpeg(XMLSlideShow show) throws IOException {
        Dimension page = show.getPageSize();
        int width = WIDTH;
        int height = Math.max(1, (int) Math.round(width * (page.getHeight() / page.getWidth())));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.scale(width / page.getWidth(), height / page.getHeight());
            XSLFSlide slide = show.getSlides().get(0);
            slide.draw(g);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", bytes)) {
            throw new IOException("Could not encode the slide thumbnail.");
        }
        return bytes.toByteArray();
    }
}
