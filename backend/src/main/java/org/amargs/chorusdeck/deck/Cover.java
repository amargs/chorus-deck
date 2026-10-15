package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.liturgy.LiturgyDay;

public record Cover(String season, String cover, String domingo, String frase, String versiculo, String ano) {

    public static Cover from(LiturgyDay day) {
        return new Cover(
                day.seasonKey(),
                day.coverLayout(),
                day.domingo(),
                day.evangelho().citacao(),
                day.evangelho().referencia(),
                day.ano());
    }

    public String quotedFrase() {
        String text = frase == null ? "" : frase.trim();
        if (text.startsWith("«") && text.endsWith("»")) {
            return text;
        }
        return "«" + text + "»";
    }
}
