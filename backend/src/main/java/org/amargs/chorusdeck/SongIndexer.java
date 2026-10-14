package org.amargs.chorusdeck;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SongIndexer {

    public void rebuild(AppConfig config) throws IOException {
        ZipSecureFile.setMaxFileCount(10000);
        List<SongCfg> songs;
        try (InputStream in = Files.newInputStream(config.sourcePptx());
             XMLSlideShow ppt = new XMLSlideShow(in)) {
            songs = index(ppt);
        }
        write(config.indexFile(), songs);
    }

    static List<SongCfg> index(XMLSlideShow ppt) {
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
                    songs.get(idx - 1).content().add(body);
                }
            }
        }
        return songs;
    }

    static boolean slideIsEmpty(XSLFSlide slide) {
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

    private static void write(Path indexFile, List<SongCfg> songs) throws IOException {
        Path parent = indexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try (OutputStream out = Files.newOutputStream(indexFile)) {
            mapper.writeValue(out, songs);
        }
    }
}
