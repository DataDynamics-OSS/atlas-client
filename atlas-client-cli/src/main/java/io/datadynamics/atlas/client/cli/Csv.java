package io.datadynamics.atlas.client.cli;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 parser: quoted fields, doubled quotes, line breaks inside quotes. */
final class Csv {

    private Csv() {
    }

    /** Rows of fields; blank lines are skipped. */
    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, field, fieldStarted);
                row = new ArrayList<>();
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV ends inside a quoted field");
        }
        endRow(rows, row, field, fieldStarted);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field, boolean fieldStarted) {
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        field.setLength(0);
    }
}
