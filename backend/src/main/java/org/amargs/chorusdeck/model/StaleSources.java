package org.amargs.chorusdeck.model;

import java.util.List;

/** What generate should say when the indexed sources are no longer current. */
public record StaleSources(boolean missing, List<String> changed) {

    public boolean current() {
        return !missing && changed.isEmpty();
    }
}
