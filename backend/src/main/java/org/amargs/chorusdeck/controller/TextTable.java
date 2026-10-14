package org.amargs.chorusdeck.controller;

import java.util.ArrayList;
import java.util.List;

final class TextTable {

    private TextTable() {
    }

    static List<String> format(List<List<String>> rows, String... headers) {
        int[] width = new int[headers.length];
        for (int column = 0; column < headers.length; column++) {
            width[column] = headers[column].length();
        }
        for (List<String> row : rows) {
            for (int column = 0; column < headers.length && column < row.size(); column++) {
                width[column] = Math.max(width[column], row.get(column).length());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add(formatRow(List.of(headers), width));
        lines.add(formatRule(width));
        for (List<String> row : rows) {
            lines.add(formatRow(row, width));
        }
        return lines;
    }

    private static String formatRow(List<String> cells, int[] width) {
        StringBuilder line = new StringBuilder("|");
        for (int column = 0; column < width.length; column++) {
            String value = column < cells.size() ? cells.get(column) : "";
            line.append(' ').append(pad(value, width[column])).append(" |");
        }
        return line.toString();
    }

    private static String formatRule(int[] width) {
        StringBuilder line = new StringBuilder("|");
        for (int column : width) {
            line.append("-".repeat(column + 2)).append("|");
        }
        return line.toString();
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }
}
