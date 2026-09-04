package com.lovemaptually.tag.entity;

public enum AttrLevel {
    HIGH, LOW;

    public AttrLevel opposite() {
        return this == HIGH ? LOW : HIGH;
    }
}
