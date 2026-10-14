package org.amargs.chorusdeck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextBox;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DeckGenerator {

    private static final int TEXT_BOX_WIDTH = 600;
    private static final int TEXT_BOX_HEIGHT = 450;

    public void generate(AppConfig config, List<Integer> ids) throws IOException {
        List<SongCfg> songs;
        try (InputStream in = Files.newInputStream(config.indexFile())) {
            songs = new ObjectMapper().readValue(in, new TypeReference<List<SongCfg>>() {});
        }

        Path parent = config.outputFile().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream in = Files.newInputStream(config.template4x3());
             XMLSlideShow templatePpt = new XMLSlideShow(in)) {
            XSLFSlideLayout layout =
                    templatePpt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlideLayout blankLayout =
                    templatePpt.getSlideMasters().get(0).getLayout(SlideLayout.BLANK);

            for (int id : ids) {
                SongCfg song = songs.get(id - 1);

                XSLFSlide firstSlide = templatePpt.createSlide(layout);
                firstSlide.getPlaceholder(0).setText(song.title());
                firstSlide.getPlaceholder(1).setText(song.content().get(0));

                for (int i = 1; i < song.content().size(); i++) {
                    XSLFSlide slide = templatePpt.createSlide(blankLayout);
                    XSLFTextBox textBox = slide.createTextBox();
                    textBox.setAnchor(new Rectangle(50, 50, TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT));
                    textBox.setText(song.content().get(i));
                }
            }

            try (OutputStream out = Files.newOutputStream(config.outputFile())) {
                templatePpt.write(out);
            }
        }
    }
}
