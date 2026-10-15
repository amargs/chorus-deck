package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.text.FitCache;
import org.amargs.chorusdeck.text.TextFit;

import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSheet;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTheme;

import java.util.ArrayList;
import java.util.List;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPlaceholder;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.openxmlformats.schemas.presentationml.x2006.main.STPlaceholderType;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class TemplateLayouts {

    public static final String ORDINARY_TITLE = "Título e letra";
    public static final String FESTIVE_TITLE = "Título e letra - Natal e Páscoa";
    public static final String LYRICS = "Letra";
    public static final String INTERVAL_BLANK = "Transição - Vazio";
    public static final String INTERVAL_TEXT = "Transição - Texto";
    public static final String INTERVAL_IMAGE = "Transição - Imagem";
    public static final String INTERVAL_TEXT_IMAGE = "Transição - Texto + Imagem";

    private TemplateLayouts() {
    }

    public static XSLFSlide addSlide(XMLSlideShow ppt, String layoutName) {
        return ppt.createSlide(findLayout(ppt, layoutName));
    }

    public static void setIntervalPicture(XSLFSlide slide, XSLFPictureData picture) throws IOException {
        XSLFShape placeholder = picturePlaceholder(slide);
        boolean onSlide = placeholder != null;
        if (placeholder == null) {
            placeholder = picturePlaceholder(slide.getSlideLayout());
        }
        if (placeholder == null || placeholder.getAnchor() == null) {
            throw new IllegalArgumentException("Picture placeholder not found");
        }
        Rectangle2D anchor = placeholder.getAnchor();
        CTPlaceholder source = picturePlaceholderXml(placeholder);
        boolean hasIdx = source != null && source.isSetIdx();
        long idx = hasIdx ? source.getIdx() : 0;
        boolean hasType = source != null && source.isSetType();
        STPlaceholderType.Enum type = hasType ? source.getType() : null;
        if (onSlide) {
            slide.removeShape(placeholder);
        }
        XSLFPictureShape inserted = slide.createPicture(picture);
        inserted.setAnchor(fittedAnchor(anchor, picture));
        if (source != null && inserted.getXmlObject() instanceof CTPicture ctPicture) {
            CTPlaceholder target = ctPicture.getNvPicPr().getNvPr().isSetPh()
                    ? ctPicture.getNvPicPr().getNvPr().getPh()
                    : ctPicture.getNvPicPr().getNvPr().addNewPh();
            if (hasType) {
                target.setType(type);
            }
            if (hasIdx) {
                target.setIdx(idx);
            }
        }
    }

    private static Rectangle2D fittedAnchor(Rectangle2D frame, XSLFPictureData data) throws IOException {
        java.awt.image.BufferedImage image = ImageIO.read(new ByteArrayInputStream(data.getData()));
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
            throw new IOException("Cannot read the interval image size.");
        }
        double imageRatio = (double) image.getWidth() / image.getHeight();
        double frameRatio = frame.getWidth() / frame.getHeight();
        double width = frame.getWidth();
        double height = frame.getHeight();
        if (imageRatio > frameRatio) {
            height = width / imageRatio;
        } else {
            width = height * imageRatio;
        }
        double x = frame.getX() + (frame.getWidth() - width) / 2.0;
        double y = frame.getY() + (frame.getHeight() - height) / 2.0;
        return new java.awt.geom.Rectangle2D.Double(x, y, width, height);
    }

    private static XSLFShape picturePlaceholder(XSLFSheet sheet) {
        for (XSLFShape shape : sheet.getShapes()) {
            if (shape.getPlaceholder() == Placeholder.PICTURE || shape.getPlaceholder() == Placeholder.CLIP_ART) {
                return shape;
            }
        }
        return null;
    }

    private static CTPlaceholder picturePlaceholderXml(XSLFShape shape) {
        if (shape.getXmlObject() instanceof CTShape ctShape
                && ctShape.getNvSpPr() != null
                && ctShape.getNvSpPr().getNvPr() != null
                && ctShape.getNvSpPr().getNvPr().isSetPh()) {
            return ctShape.getNvSpPr().getNvPr().getPh();
        }
        return null;
    }

    public static void setNotes(XSLFSlide slide, String text) {
        XMLSlideShow ppt = slide.getSlideShow();
        linkNestedThemes(ppt);
        XSLFNotes notes = ppt.getNotesSlide(slide);
        String body = text == null ? "" : text;
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getTextType() == Placeholder.BODY) {
                textShape.setText(body);
                for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                    paragraph.setTextAlign(TextParagraph.TextAlign.CENTER);
                }
                return;
            }
        }
        throw new IllegalArgumentException("Notes placeholder not found");
    }

    /**
     * POI creates a new theme for the notes master and picks the next index from the
     * presentation's own theme parts. This template already stores theme2 on the handout
     * master, so that index collides. Linking those nested themes makes POI take a free index.
     */
    private static void linkNestedThemes(XMLSlideShow ppt) {
        List<XSLFTheme> linked = new ArrayList<>();
        for (POIXMLDocumentPart part : ppt.getRelations()) {
            if (part instanceof XSLFTheme theme) {
                linked.add(theme);
            }
        }
        for (POIXMLDocumentPart part : List.copyOf(ppt.getRelations())) {
            for (POIXMLDocumentPart child : part.getRelations()) {
                if (child instanceof XSLFTheme theme && !linked.contains(theme)) {
                    ppt.addRelation(null, XSLFRelation.THEME, theme);
                    linked.add(theme);
                }
            }
        }
    }

    public static XSLFSlideLayout findLayout(XMLSlideShow ppt, String name) {
        for (XSLFSlideMaster master : ppt.getSlideMasters()) {
            for (XSLFSlideLayout layout : master.getSlideLayouts()) {
                if (name.equals(layout.getName())) {
                    return layout;
                }
            }
        }
        throw new IllegalArgumentException("Layout not found: " + name);
    }

    public static void setHeaderScheme(XSLFSlideLayout layout, String schemeColor) {
        if (schemeColor == null || schemeColor.isBlank()) {
            return;
        }
        STSchemeColorVal.Enum color = STSchemeColorVal.Enum.forString(schemeColor);
        if (color == null) {
            throw new IllegalArgumentException("Unknown theme color: " + schemeColor);
        }
        for (XSLFShape shape : layout.getShapes()) {
            if ("Header".equals(shape.getShapeName()) && shape.getXmlObject() instanceof CTShape ctShape) {
                paint(ctShape, color);
            }
        }
    }

    private static void paint(CTShape shape, STSchemeColorVal.Enum color) {
        if (shape.getSpPr() != null && shape.getSpPr().isSetSolidFill() && shape.getSpPr().getSolidFill().isSetSchemeClr()) {
            shape.getSpPr().getSolidFill().getSchemeClr().setVal(color);
        }
        if (!shape.isSetStyle()) {
            return;
        }
        CTShapeStyle style = shape.getStyle();
        paint(style.getFillRef() == null ? null : style.getFillRef().getSchemeClr(), color);
        paint(style.getLnRef() == null ? null : style.getLnRef().getSchemeClr(), color);
        paint(style.getEffectRef() == null ? null : style.getEffectRef().getSchemeClr(), color);
    }

    private static void paint(CTSchemeColor scheme, STSchemeColorVal.Enum color) {
        if (scheme != null) {
            scheme.setVal(color);
        }
    }

    public static void setShapeText(XSLFSlide slide, String shapeName, String text) {
        setShapeText(slide, shapeName, text, true);
    }

    public static void setShapeText(XSLFSlide slide, String shapeName, String text, boolean fit) {
        XSLFTextShape shape = findTextShape(slide, shapeName);
        String marked = text == null ? "" : text;
        shape.setText(BoldMarkup.plain(marked));
        BoldMarkup.apply(shape, marked);
        if (fit) {
            TextFit.apply(shape, marked, null, 0, 0);
        }
    }

    public static void setShapeText(XSLFSlide slide, String shapeName, String text, FitCache cache, int songId, int slideNumber) {
        XSLFTextShape shape = findTextShape(slide, shapeName);
        String marked = text == null ? "" : text;
        shape.setText(BoldMarkup.plain(marked));
        BoldMarkup.apply(shape, marked);
        TextFit.apply(shape, marked, cache, songId, slideNumber);
    }

    private static XSLFTextShape findTextShape(XSLFSlide slide, String shapeName) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && shapeName.equals(textShape.getShapeName())) {
                return textShape;
            }
        }
        throw new IllegalArgumentException("Shape not found: " + shapeName);
    }
}
