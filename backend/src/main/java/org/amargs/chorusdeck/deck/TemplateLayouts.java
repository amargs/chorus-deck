package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.text.TextFit;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.drawingml.x2006.main.CTSchemeColor;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeStyle;
import org.openxmlformats.schemas.drawingml.x2006.main.STSchemeColorVal;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;

public final class TemplateLayouts {

    public static final String ORDINARY_TITLE = "Título e letra";
    public static final String FESTIVE_TITLE = "Título e letra - Natal e Páscoa";
    public static final String LYRICS = "Letra";

    private TemplateLayouts() {
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
        setShapeText(slide, shapeName, text, "semibold");
    }

    public static void setShapeText(XSLFSlide slide, String shapeName, String text, String boldWeight) {
        XSLFTextShape shape = findTextShape(slide, shapeName);
        String marked = text == null ? "" : text;
        shape.setText(BoldMarkup.plain(marked));
        BoldMarkup.apply(shape, marked, boldWeight);
        TextFit.apply(shape);
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
