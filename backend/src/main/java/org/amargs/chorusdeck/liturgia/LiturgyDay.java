package org.amargs.chorusdeck.liturgia;

import org.amargs.chorusdeck.model.Cover;

import java.time.LocalDate;

public record LiturgyDay(
        LocalDate date,
        String domingo,
        String seasonKey,
        String coverLayout,
        String ano,
        Reading primeira,
        Reading segunda,
        String refrao,
        Reading evangelho,
        String louvor) {

    public record Reading(String referencia, String citacao) {
    }

    public Cover cover() {
        return new Cover(seasonKey, coverLayout, domingo, evangelho.citacao(), evangelho.referencia(), ano);
    }

    public LiturgyDay withSentences(
            String primeiraCitacao, String segundaCitacao, String refraoText, String evangelhoCitacao, String louvorText) {
        return new LiturgyDay(
                date,
                domingo,
                seasonKey,
                coverLayout,
                ano,
                choose(primeira, primeiraCitacao),
                choose(segunda, segundaCitacao),
                choose(refrao, refraoText),
                choose(evangelho, evangelhoCitacao),
                choose(louvor, louvorText));
    }

    private static Reading choose(Reading reading, String citacao) {
        if (reading == null) {
            return null;
        }
        String sentence = text(citacao);
        return sentence == null ? reading : new Reading(reading.referencia(), sentence);
    }

    private static String choose(String stored, String manual) {
        String sentence = text(manual);
        return sentence == null ? stored : sentence;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
