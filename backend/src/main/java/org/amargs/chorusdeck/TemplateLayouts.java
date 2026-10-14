package org.amargs.chorusdeck;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

public final class TemplateLayouts {

    public static final String ORDINARY_TITLE = "Título e letra - Tempo comum";
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
