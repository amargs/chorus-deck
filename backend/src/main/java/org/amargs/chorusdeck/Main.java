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
        createIndexFile();

    }

    public static void createIndexFile() throws Exception {
        ZipSecureFile.setMaxFileCount(10000);
        try (FileInputStream fis = new FileInputStream("input.pptx")) {
            XMLSlideShow inputPpt = new XMLSlideShow(fis);
            List<SongCfg> songs = index(inputPpt);
            writeToFile(songs);
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
