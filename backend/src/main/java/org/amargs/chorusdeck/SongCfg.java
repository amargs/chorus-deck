package org.amargs.chorusdeck;

import java.util.List;

public class SongCfg {

    private int id;
    private String title;
    private List<String> content;

    public SongCfg() {
    }

    public SongCfg(int id, String title, List<String> content) {
        this.id = id;
        this.title = title;
        this.content = content;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getContent() {
        return content;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setContent(List<String> content) {
        this.content = content;
    }
}
