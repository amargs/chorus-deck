package org.amargs.chorusdeck.liturgy;

import java.time.LocalDate;

/** One ordinary Sunday, shared so a new test can pass the day in instead of pasting it. */
public final class LiturgyFixtures {

    public static final LocalDate ORDINARY_29 = LocalDate.of(2026, 10, 18);

    public static final String ORDINARY_29_CALENDAR = """
            {
              "day": { "sunday_id": "ordinary-29", "sunday_cycle": "A", "season": "ordinary" },
              "readings": {
                "first_reading": "Is 45:1, 4-6",
                "second_reading": "1 Thes 1:1-5",
                "psalm_response": "Give the Lord glory and honour",
                "gospel": "Mt 22:15-21"
              }
            }
            """;

    private LiturgyFixtures() {
    }

    public static LiturgyDay ordinary29() {
        return new LiturgyDay(
                ORDINARY_29,
                "Domingo XXIX",
                "comum",
                null,
                "A",
                new LiturgyDay.Reading("Is 45, 1, 4-6", "Tomei Ciro"),
                new LiturgyDay.Reading("1Ts 1, 1-5", "Recordamos"),
                "Aclamai",
                new LiturgyDay.Reading("Mt 22, 15-21", "Dai a César"),
                null);
    }
}
