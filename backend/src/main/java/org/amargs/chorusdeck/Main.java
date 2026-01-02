package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        createNewPresentation(List.of(8, 98, 16, 120), SlideDimensions.D16x9);
    }

    public static void createIndexFile() throws Exception {
        ZipSecureFile.setMaxFileCount(10000);
        try (FileInputStream fis = new FileInputStream("input.pptx")) {
            XMLSlideShow inputPpt = new XMLSlideShow(fis);
            List<SongCfg> songs = index(inputPpt);
            writeToFile(songs);
        }
    }

    public static void createNewPresentation(List<Integer> ids, SlideDimensions dimensions) throws Exception {
        String fileName;
        int width;
        int height;

        if (dimensions == SlideDimensions.D4x3) {
            fileName = "template_4x3.pptx";
            width = 600;
            height = 450;
        } else {
            fileName = "template_16x9.pptx";
            width = 1600;
            height = 900;
        }

        XMLSlideShow templatePpt;
        try (FileInputStream fis = new FileInputStream(fileName)) {
            templatePpt = new XMLSlideShow(fis);
        }

        XSLFSlideLayout layout =
                templatePpt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);

        ObjectMapper mapper = new ObjectMapper();
        List<SongCfg> songs;
        try (FileInputStream fis = new FileInputStream("index.json")) {
            songs = mapper.readValue(fis, new TypeReference<List<SongCfg>>() {});
        }

        for (int id : ids) {
            SongCfg song = songs.get(id - 1);

            XSLFSlide firstSlide = templatePpt.createSlide(layout);
            firstSlide.getPlaceholder(0).setText(song.getTitle());
            firstSlide.getPlaceholder(1).setText(song.getContent().get(0));

            for (int i = 1; i < song.getContent().size(); i++) {
                XSLFSlideLayout blankLayout =
                        templatePpt.getSlideMasters().get(0).getLayout(SlideLayout.BLANK);
                XSLFSlide slide = templatePpt.createSlide(blankLayout);

                XSLFTextBox textBox = slide.createTextBox();
                textBox.setAnchor(new Rectangle(50, 50, width, height));
                textBox.setText(song.getContent().get(i));
            }
        }

        try (FileOutputStream fos = new FileOutputStream("out_" + fileName)) {
            templatePpt.write(fos);
        }
    }

    public static boolean slideIsEmpty(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                String text = ((XSLFTextShape) shape).getText();
                if (text != null && !text.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static List<SongCfg> index(XMLSlideShow ppt) {
        int idx = 0;
        List<SongCfg> songs = new ArrayList<>();

        for (XSLFSlide slide : ppt.getSlides()) {
            String slideTitle = slide.getTitle();

            if (slideTitle != null && !slideTitle.isEmpty()) {
                String title = slideTitle.substring(slideTitle.indexOf('\n') + 1);
                System.out.println(title);
                XSLFTextShape bodyShape = (XSLFTextShape) slide.getShapes().get(1);
                String body = bodyShape.getText().trim();

                List<String> content = new ArrayList<>();
                content.add(body);
                songs.add(new SongCfg(++idx, title, content));
            } else {
                if (idx > 0 && !slideIsEmpty(slide)) {
                    XSLFTextShape bodyShape = (XSLFTextShape) slide.getShapes().get(0);
                    String body = bodyShape.getText().trim();
                    songs.get(idx - 1).getContent().add(body);
                }
            }
        }
        return songs;
    }

    public static void writeToFile(List<SongCfg> index) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        mapper.writeValue(new File("index.json"), index);
    }
}
