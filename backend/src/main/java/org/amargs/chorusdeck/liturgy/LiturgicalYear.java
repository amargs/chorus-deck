package org.amargs.chorusdeck.liturgy;

import java.time.LocalDate;

public final class LiturgicalYear {

    private LiturgicalYear() {
    }

    /** The year turns on the First Sunday of Advent. 2026 is A, 2027 is B, 2025 is C. */
    public static String cycle(LocalDate date) {
        int year = date.getYear();
        if (!date.isBefore(firstSundayOfAdvent(year))) {
            year++;
        }
        return switch (year % 3) {
            case 1 -> "A";
            case 2 -> "B";
            default -> "C";
        };
    }

    static LocalDate firstSundayOfAdvent(int year) {
        LocalDate nov27 = LocalDate.of(year, 11, 27);
        int untilSunday = (7 - nov27.getDayOfWeek().getValue()) % 7;
        return nov27.plusDays(untilSunday);
    }
}
