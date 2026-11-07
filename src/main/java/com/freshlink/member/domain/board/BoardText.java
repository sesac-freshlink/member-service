package com.freshlink.member.domain.board;

/** 게시판 글의 앞뒤 공백 지우기. strip()은 줄바꿈 없는 공백(U+00A0, U+2007, U+202F)을 남기므로 그것까지 지운다 */
final class BoardText {

    private BoardText() {
    }

    static String trim(String s) {
        if (s == null) {
            return null;
        }
        int start = 0;
        int end = s.length();
        while (start < end && isBlank(s.charAt(start))) {
            start++;
        }
        while (end > start && isBlank(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isBlank(char c) {
        return Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ';
    }
}
