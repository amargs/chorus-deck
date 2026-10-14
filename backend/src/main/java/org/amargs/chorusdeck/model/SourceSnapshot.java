package org.amargs.chorusdeck.model;

/** SHA-256 of the PowerPoint and both songbooks, stored in config after a successful index. */
public record SourceSnapshot(String pptx, String songbook, String rites) {

    public enum Refresh {
        UNCHANGED,
        SONGBOOK,
        FULL
    }

    public static Refresh decide(SourceSnapshot stored, SourceSnapshot current, boolean indexExists) {
        if (!indexExists || stored == null) {
            return Refresh.FULL;
        }
        if (!stored.pptx.equals(current.pptx)) {
            return Refresh.FULL;
        }
        if (!stored.songbook.equals(current.songbook) || !stored.rites.equals(current.rites)) {
            return Refresh.SONGBOOK;
        }
        return Refresh.UNCHANGED;
    }
}
