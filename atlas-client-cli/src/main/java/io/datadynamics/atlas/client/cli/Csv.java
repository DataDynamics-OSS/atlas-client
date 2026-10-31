package io.datadynamics.atlas.client.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * 최소한의 RFC 4180 CSV 파서입니다.
 * <p>
 * 따옴표로 감싼 필드, 따옴표 안에서 두 번 쓴 따옴표({@code ""} → {@code "}), 따옴표 안의 줄바꿈을 지원합니다.
 * 줄 끝은 LF, CR, CRLF를 모두 인식합니다. 구분자는 쉼표로 고정입니다.
 */
final class Csv {

    /** 정적 메서드만 가진 유틸리티 클래스이므로 인스턴스를 만들지 않습니다. */
    private Csv() {
    }

    /**
     * CSV 텍스트를 행 목록으로 바꿉니다. 각 행은 필드 문자열의 목록이며, 빈 줄은 건너뜁니다.
     * 맨 앞의 UTF-8 BOM은 제거합니다.
     *
     * @param text CSV 전체 내용
     * @return 행 목록. 필드 값은 자르지 않은 원래 문자열입니다
     * @throws IllegalArgumentException 따옴표 필드가 닫히지 않은 채 끝난 경우
     */
    static List<List<String>> parse(String text) {
        // 엑셀 등이 저장한 파일 맨 앞의 BOM(U+FEFF)이 첫 머리글에 붙지 않도록 떼어 냅니다.
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        // fieldStarted는 현재 줄에 내용(문자, 쉼표, 따옴표)이 하나라도 있었는지 나타냅니다.
        // 빈 줄과 빈 필드 하나뿐인 줄을 구분하는 데 씁니다.
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                // 따옴표 안: 두 번 쓴 따옴표는 따옴표 문자 하나, 단독 따옴표는 필드의 끝입니다.
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0) {
                // 필드 맨 앞의 따옴표만 따옴표 필드의 시작으로 봅니다. 중간의 따옴표는 일반 문자입니다.
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                // CRLF는 줄바꿈 하나로 처리합니다.
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

    /**
     * 현재 필드를 행에 넣고 행을 마칩니다. 줄에 아무 내용도 없었으면(빈 줄) 행을 추가하지 않습니다.
     * 다음 필드를 위해 {@code field}를 비웁니다.
     */
    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field, boolean fieldStarted) {
        if (fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        field.setLength(0);
    }
}
