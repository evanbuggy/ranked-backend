package com.startGgIntegration.valueObjects;
//authored by Liam Kelly, 22346317

public record StartGgURL(String value) {
    private static final java.util.regex.Pattern VALID = java.util.regex.Pattern.compile("start\\.gg/tournament/[^/?#]+/event/[^/?#]+"); //note: the . needs to be escaped

    public StartGgURL {
        if (value == null || !VALID.matcher(value).find())
            throw new IllegalArgumentException("Invalid Start.gg event URL. Your link should look like: start.gg/tournament/(tournament name)/event/(event name)");
    }
}