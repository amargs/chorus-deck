package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.text.FitCache;
import org.amargs.chorusdeck.text.TextFit;

import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
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
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSolidColorFillProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBodyProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal;
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackgroundProperties;
import org.openxmlformats.schemas.presentationml.x2006.main.CTBackground;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPicture;
import org.openxmlformats.schemas.presentationml.x2006.main.CTPlaceholder;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.openxmlformats.schemas.presentationml.x2006.main.STPlaceholderType;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.imageio.ImageIO;

public final class TemplateLayouts {

    public static final String COVER = "Capa";
    public static final String ORDINARY_TITLE = "Título e letra";
    public static final String FESTIVE_TITLE = "Título e letra - Natal e Páscoa";
    public static final String LYRICS = "Letra";
    public static final String INTERVAL_BLANK = "Transição - Vazio";
    public static final String INTERVAL_TEXT = "Transição - Texto";
    public static final String INTERVAL_IMAGE = "Transição - Imagem";
    public static final String INTERVAL_TEXT_IMAGE = "Transição - Texto + Imagem";
    public static final String GLORIA_1 = "Glória - 1";
    public static final String GLORIA_2 = "Glória - 2";
    public static final String GLORIA_3 = "Glória - 3";
    public static final String CREDO_LONG_1 = "Credo - Longo - 1";
    public static final String CREDO_LONG_2 = "Credo - Longo - 2";
    public static final String CREDO_LONG_3 = "Credo - Longo - 3";
    public static final String CREDO_LONG_4 = "Credo - Longo - 4";
    public static final String CREDO_LONG_5 = "Credo - Longo - 5";
    public static final String CREDO_LONG_6 = "Credo - Longo - 6";
    public static final String CREDO_SHORT_1 = "Credo - Breve - 1";
    public static final String CREDO_SHORT_2 = "Credo - Breve - 2";
    public static final String CREDO_SHORT_3 = "Credo - Breve - 3";
    public static final String LEITURA_1 = "Leitura 1";
    public static final String LEITURA_2 = "Leitura 2";
    public static final String SALMO = "Salmo";
    public static final String EVANGELHO = "Evangelho";
    public static final String LOUVOR = "Louvor a vós";
    public static final String ORACAO = "Oração Universal";
    public static final String PREPARACAO = "Preparação das oferendas";
    public static final String DIALOGO = "Diálogo Prefácio";
    public static final String MEMORIAL = "Aclamação Memorial";
    public static final String ACLAMACAO = "Aclamação";
    public static final String CONVITE = "Convite à Comunhão";
    public static final String[] CREDO_LONG = {
            CREDO_LONG_1, CREDO_LONG_2, CREDO_LONG_3, CREDO_LONG_4, CREDO_LONG_5, CREDO_LONG_6
    };
    public static final String[] CREDO_SHORT = {CREDO_SHORT_1, CREDO_SHORT_2, CREDO_SHORT_3};
    public static final String[] GLORIA = {GLORIA_1, GLORIA_2, GLORIA_3};

    private TemplateLayouts() {
    }

    public static XSLFSlide addSlide(XMLSlideShow ppt, String layoutName) {
        return ppt.createSlide(findLayout(ppt, layoutName));
    }

    public static void setScriptureBackground(XMLSlideShow ppt, byte[] jpeg) throws IOException {
        Set<PackagePart> parts = new LinkedHashSet<>();
        for (String name : new String[]{LEITURA_1, LEITURA_2, SALMO, EVANGELHO}) {
            parts.add(backgroundPart(findLayout(ppt, name)));
        }
        for (PackagePart part : parts) {
            try (OutputStream out = part.getOutputStream()) {
                out.write(jpeg);
            }
        }
    }

    static byte[] scriptureBackground(XMLSlideShow ppt) throws IOException {
        return scriptureBackground(ppt, LEITURA_1);
    }

    static byte[] scriptureBackground(XMLSlideShow ppt, String layout) throws IOException {
        return backgroundPart(findLayout(ppt, layout)).getInputStream().readAllBytes();
    }

    private static PackagePart backgroundPart(XSLFSlideLayout layout) {
        if (layout.getXmlObject().getCSld() == null || !layout.getXmlObject().getCSld().isSetBg()) {
            throw new IllegalArgumentException("Scripture background not found on " + layout.getName());
        }
        CTBackground bg = layout.getXmlObject().getCSld().getBg();
        if (bg.getBgPr() == null || !bg.getBgPr().isSetBlipFill() || bg.getBgPr().getBlipFill().getBlip() == null) {
            throw new IllegalArgumentException("Scripture background not found on " + layout.getName());
        }
        String embed = bg.getBgPr().getBlipFill().getBlip().getEmbed();
        PackageRelationship rel = layout.getPackagePart().getRelationship(embed);
        if (rel == null) {
            throw new IllegalArgumentException("Scripture background not found on " + layout.getName());
        }
        try {
            return layout.getPackagePart().getRelatedPart(rel);
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IllegalArgumentException("Scripture background not found on " + layout.getName(), e);
        }
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

    public static void paintCover(XSLFSlideLayout layout, Season.CoverPaint paint) {
        CTBackground bg = layout.getXmlObject().getCSld().getBg();
        if (bg == null || bg.getBgPr() == null) {
            throw new IllegalArgumentException("Cover background not found");
        }
        CTBackgroundProperties bgPr = bg.getBgPr();
        if (bgPr.isSetSolidFill()) {
            bgPr.unsetSolidFill();
        }
        CTSolidColorFillProperties solid = bgPr.addNewSolidFill();
        if (paint.backgroundRgb() != null) {
            solid.addNewSrgbClr().setVal(HexFormat.of().parseHex(paint.backgroundRgb()));
        } else {
            CTSchemeColor scheme = solid.addNewSchemeClr();
            scheme.setVal(schemeColor(paint.backgroundScheme()));
            modulate(scheme, paint.lumMod(), paint.lumOff(), paint.alpha());
        }
        paintDomingo(textShape(layout, "Domingo"), paint);
    }

    public static void paintCoverText(XSLFSlide slide, Season.CoverPaint paint) {
        CTShape domingo = textShape(slide, "Domingo");
        if (domingo == null) {
            domingo = textShape(slide.getSlideLayout(), "Domingo");
        }
        paintDomingo(domingo, paint);
    }

    public static void setCoverLogo(XSLFSlideLayout layout, XSLFPictureData picture) throws IOException {
        XSLFPictureShape logo = null;
        for (XSLFShape shape : layout.getShapes()) {
            if (shape instanceof XSLFPictureShape pic && "Picture 4".equals(shape.getShapeName())) {
                logo = pic;
                break;
            }
        }
        if (logo == null || !(logo.getXmlObject() instanceof CTPicture ctPicture)) {
            throw new IllegalArgumentException("Cover logo not found");
        }
        Rectangle2D frame = logo.getAnchor();
        POIXMLDocumentPart.RelationPart rel = layout.addRelation(null, XSLFRelation.IMAGES, picture);
        ctPicture.getBlipFill().getBlip().setEmbed(rel.getRelationship().getId());
        logo.setAnchor(fittedAnchor(frame, picture));
    }

    private static void paintDomingo(CTShape shape, Season.CoverPaint paint) {
        if (shape == null
                || shape.getTxBody() == null
                || shape.getTxBody().getLstStyle() == null
                || !shape.getTxBody().getLstStyle().isSetLvl1PPr()
                || !shape.getTxBody().getLstStyle().getLvl1PPr().isSetDefRPr()) {
            return;
        }
        CTTextCharacterProperties defaults = shape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr();
        if (defaults.isSetSolidFill()) {
            defaults.unsetSolidFill();
        }
        CTSchemeColor scheme = defaults.addNewSolidFill().addNewSchemeClr();
        scheme.setVal(schemeColor(paint.textScheme()));
        modulate(scheme, paint.textLumMod(), paint.textLumOff(), 0);
    }

    private static void modulate(CTSchemeColor scheme, int lumMod, int lumOff, int alpha) {
        if (lumMod > 0) {
            scheme.addNewLumMod().setVal(lumMod);
        }
        if (lumOff > 0) {
            scheme.addNewLumOff().setVal(lumOff);
        }
        if (alpha > 0 && alpha < 100_000) {
            scheme.addNewAlpha().setVal(alpha);
        }
    }

    private static STSchemeColorVal.Enum schemeColor(String name) {
        STSchemeColorVal.Enum color = STSchemeColorVal.Enum.forString(name);
        if (color == null) {
            throw new IllegalArgumentException("Unknown theme color: " + name);
        }
        return color;
    }

    private static CTShape textShape(XSLFSheet sheet, String name) {
        for (XSLFShape shape : sheet.getShapes()) {
            if (name.equals(shape.getShapeName()) && shape.getXmlObject() instanceof CTShape ctShape) {
                return ctShape;
            }
        }
        return null;
    }

    /** Scripture text is a darkened accent. Point that accent at the season and keep the darkening. */
    public static void setScriptureAccent(XMLSlideShow ppt, String scheme) {
        STSchemeColorVal.Enum color = schemeColor(scheme);
        for (String name : new String[]{LEITURA_1, LEITURA_2, SALMO, EVANGELHO, LOUVOR}) {
            recolorAccents(findLayout(ppt, name).getXmlObject(), color);
        }
    }

    public static void setScriptureAccent(XSLFSlide slide, String scheme) {
        recolorAccents(slide.getXmlObject(), schemeColor(scheme));
    }

    private static void recolorAccents(XmlObject xml, STSchemeColorVal.Enum color) {
        try (XmlCursor cursor = xml.newCursor()) {
            visitAccents(cursor, color);
        }
    }

    private static void visitAccents(XmlCursor cursor, STSchemeColorVal.Enum color) {
        XmlCursor.TokenType token = cursor.currentTokenType();
        if (token != null && token.isStart()
                && cursor.getName() != null
                && "schemeClr".equals(cursor.getName().getLocalPart())
                && cursor.getObject() instanceof CTSchemeColor scheme
                && isAccent(scheme.getVal())) {
            scheme.setVal(color);
        }
        if (cursor.toFirstChild()) {
            do {
                visitAccents(cursor, color);
            } while (cursor.toNextSibling());
            cursor.toParent();
        }
    }

    private static boolean isAccent(STSchemeColorVal.Enum value) {
        return value == STSchemeColorVal.ACCENT_1
                || value == STSchemeColorVal.ACCENT_2
                || value == STSchemeColorVal.ACCENT_3
                || value == STSchemeColorVal.ACCENT_4
                || value == STSchemeColorVal.ACCENT_5
                || value == STSchemeColorVal.ACCENT_6;
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
            if (!(shape.getXmlObject() instanceof CTShape ctShape)) {
                continue;
            }
            if ("Header".equals(shape.getShapeName())) {
                paint(ctShape, color);
            } else if ("Título".equals(shape.getShapeName())) {
                paintTitle(ctShape, color);
            }
        }
    }

    /** Title text uses a light tint of the header accent. Keep the tint and point it at the new accent. */
    private static void paintTitle(CTShape shape, STSchemeColorVal.Enum color) {
        if (shape.getTxBody() == null
                || shape.getTxBody().getLstStyle() == null
                || !shape.getTxBody().getLstStyle().isSetLvl1PPr()) {
            return;
        }
        CTTextCharacterProperties defaults = shape.getTxBody().getLstStyle().getLvl1PPr().getDefRPr();
        if (defaults == null || !defaults.isSetSolidFill() || !defaults.getSolidFill().isSetSchemeClr()) {
            return;
        }
        defaults.getSolidFill().getSchemeClr().setVal(color);
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
        setShapeText(slide, shapeName, text, fit, false);
    }

    /** Fit even when the layout turns shrinking off. The layout size stays the maximum. */
    public static void setShrinkingText(XSLFSlide slide, String shapeName, String text) {
        setShapeText(slide, shapeName, text, true, true);
    }

    private static void setShapeText(XSLFSlide slide, String shapeName, String text, boolean fit, boolean forceShrink) {
        XSLFTextShape shape = findTextShape(slide, shapeName);
        String marked = text == null ? "" : text;
        shape.setText(BoldMarkup.plain(marked));
        BoldMarkup.apply(shape, marked);
        if (forceShrink) {
            enableShrink(shape);
        }
        if (fit) {
            TextFit.apply(shape, marked, null, 0, 0);
        }
    }

    private static void enableShrink(XSLFTextShape shape) {
        if (!(shape.getXmlObject() instanceof CTShape ctShape) || ctShape.getTxBody() == null) {
            return;
        }
        CTTextBodyProperties body = ctShape.getTxBody().getBodyPr();
        if (body == null) {
            body = ctShape.getTxBody().addNewBodyPr();
        }
        if (body.isSetNoAutofit()) {
            body.unsetNoAutofit();
        }
        if (body.isSetSpAutoFit()) {
            body.unsetSpAutoFit();
        }
        if (!body.isSetNormAutofit()) {
            body.addNewNormAutofit();
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
