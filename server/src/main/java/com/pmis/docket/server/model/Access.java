package com.pmis.docket.server.model;

/** What a user may do in a folder or with a file. Ordered from least to most. */
public enum Access {
    NONE, READ, WRITE, FULL;

    public boolean atLeast(Access other) { return this.ordinal() >= other.ordinal(); }

    public static Access max(Access a, Access b) { return a.ordinal() >= b.ordinal() ? a : b; }

    public String label() {
        return switch (this) {
            case NONE -> "No access";
            case READ -> "Read only";
            case WRITE -> "Read & write";
            case FULL -> "Full control";
        };
    }
}
