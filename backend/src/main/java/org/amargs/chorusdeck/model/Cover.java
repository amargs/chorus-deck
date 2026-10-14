package org.amargs.chorusdeck.model;

public record Cover(String season, String cover, String domingo, String frase, String versiculo, String ano) {

    public String quotedFrase() {
        String text = frase == null ? "" : frase.trim();
        if (text.startsWith("«") && text.endsWith("»")) {
            return text;
        }
        return "«" + text + "»";
    }
}
