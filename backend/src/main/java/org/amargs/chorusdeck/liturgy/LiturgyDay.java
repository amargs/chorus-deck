package org.amargs.chorusdeck.liturgy;

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

    public LiturgyDay withReferences(String primeiraRef, String segundaRef) {
        Reading nextPrimeira = reference(primeira, primeiraRef);
        Reading nextSegunda = reference(segunda, segundaRef);
        if (nextPrimeira == primeira && nextSegunda == segunda) {
            return this;
        }
        return new LiturgyDay(
                date, domingo, seasonKey, coverLayout, ano, nextPrimeira, nextSegunda, refrao, evangelho, louvor);
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

    private static Reading reference(Reading reading, String referencia) {
        if (reading == null) {
            return null;
        }
        String next = text(referencia);
        if (next == null || next.equals(reading.referencia())) {
            return reading;
        }
        return new Reading(next, reading.citacao());
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
