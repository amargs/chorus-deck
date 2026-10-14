package org.amargs.chorusdeck.service;

import org.amargs.chorusdeck.index.SongbookReader;
import org.amargs.chorusdeck.model.SongbookItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Song titles and numbers read from the songbooks. */
public final class SongbookService {

    private final SongbookReader reader = new SongbookReader();

    public List<SongbookItem> read(Path docx) throws IOException {
        return reader.read(docx);
    }
}
