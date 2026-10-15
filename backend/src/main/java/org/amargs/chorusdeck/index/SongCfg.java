package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SongCfg(int id, String title, List<String> content, String bookId) {

    public SongCfg(int id, String title, List<String> content) {
        this(id, title, content, null);
    }
}
