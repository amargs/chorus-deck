package org.amargs.chorusdeck.web;

import java.io.IOException;
import java.nio.file.Path;

/** The song index and liturgy shipped inside the program. Callers copy them out; they do not edit these files. */
public interface LibrarySeed {

    void copyIndex(Path destination) throws IOException;

    void copyLiturgy(Path destination) throws IOException;
}
