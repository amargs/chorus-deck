package org.amargs.chorusdeck.index;

import org.amargs.chorusdeck.text.BoldMarkup;

import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.util.ArrayList;
import java.util.List;

/** Songs read from the master deck. Ids here are temporary; {@link SongIds} keeps the real ones. */
public final class SlideSongs {

    private SlideSongs() {
    }

    public static Indexed index(XMLSlideShow ppt) {
        int idx = 0;
        List<SongCfg> songs = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();

        for (XSLFSlide slide : ppt.getSlides()) {
            String slideTitle = slide.getTitle();

            if (isSongTitle(slideTitle)) {
                String title = songTitle(slideTitle);
                notices.add(new Notice.Song(title));
                List<String> content = new ArrayList<>();
                content.add(lyricBody(slide));
                songs.add(new SongCfg(++idx, title, content));
            } else if (slideTitle != null && !slideTitle.isBlank()) {
                notices.add(new Notice.Skipped(slideTitle.replace('\u00A0', ' ').strip()));
            } else if (idx > 0 && isVerseSlide(slide) && !slideIsEmpty(slide)) {
                XSLFTextShape bodyShape = (XSLFTextShape) slide.getShapes().get(0);
                String body = BoldMarkup.mark(bodyShape);
                songs.get(idx - 1).content().add(body);
            }
        }
        stripUniformBold(songs);
        return new Indexed(songs, notices);
    }

    public record Indexed(List<SongCfg> songs, List<Notice> notices) {
    }

    public sealed interface Notice permits Notice.Song, Notice.Skipped {
        record Song(String title) implements Notice {
        }

        record Skipped(String title) implements Notice {
        }
    }

    /** A song title is two lines: a label (`Cântico:` or a number), then the name. */
    static boolean isSongTitle(String slideTitle) {
        return slideTitle != null && slideTitle.contains("\n");
    }

    /** A verse slide uses the lyric placeholder. A free text box is not a verse. */
    static boolean isVerseSlide(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape text) || !text.isPlaceholder()) {
                continue;
            }
            Placeholder type = text.getTextType();
            if (type != Placeholder.TITLE && type != Placeholder.CENTERED_TITLE) {
                return true;
            }
        }
        return false;
    }

    /** Lyric text is the second shape. A title-only slide has no verse. */
    static String lyricBody(XSLFSlide slide) {
        List<XSLFShape> shapes = slide.getShapes();
        if (shapes.size() < 2 || !(shapes.get(1) instanceof XSLFTextShape body)) {
            return "";
        }
        return BoldMarkup.mark(body);
    }

    public static String songTitle(String slideTitle) {
        int breakAt = slideTitle.indexOf('\n');
        String title = breakAt >= 0 ? slideTitle.substring(breakAt + 1) : slideTitle;
        return title.replace('\u00A0', ' ').strip();
    }

    public static void stripUniformBold(List<SongCfg> songs) {
        for (SongCfg song : songs) {
            if (song.content() != null && song.content().size() == 1) {
                song.content().set(0, BoldMarkup.plainIfFullyBold(song.content().get(0)));
            }
        }
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
}
