package edu.sjsu.cmpe172.tutorbook.service;

/** Formats prices stored as integer cents (avoids floating-point money). */
final class Money {

    private Money() {
    }

    static String format(int cents) {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }
}
