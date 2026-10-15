package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.liturgy.LiturgyDay;

public record Cover(String season, String cover, String domingo, String frase, String versiculo, String ano) {

    public static Cover from(LiturgyDay day) {
        LiturgyDay.Reading gospel = day.evangelho();
        return new Cover(
                day.seasonKey(),
                day.coverLayout(),
                day.domingo(),
                gospel == null ? "" : gospel.citacao(),
                gospel == null ? "" : gospel.referencia(),
                day.ano());
    }

    public String quotedFrase() {
        if (frase != null && frase.isEmpty()) {
            return "";
        }
        String text = frase == null ? "" : frase.trim();
        if (text.startsWith("«") && text.endsWith("»")) {
            return text;
        }
        return "«" + text + "»";
    }
}
