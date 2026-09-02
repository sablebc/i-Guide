package com.kmsma.i_guide;

import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Streaming parser for the Tunarr XMLTV feed at {@code GET /api/xmltv.xml}.
 *
 * <p>Only the elements the guide actually renders are read; everything else is skipped
 * so a multi-day feed stays cheap to parse.
 */
public final class XmltvParser {

    /**
     * Parsed feed: programmes grouped by XMLTV channel id, plus a lookup from every
     * lowercased {@code <display-name>} to the id that declared it. Tunarr emits
     * several names per channel ("7 Adult Swim", "7", "Adult Swim"), and its XMLTV ids
     * ({@code C7.55.tunarr.com}) bear no relation to its API channel UUIDs, so those
     * aliases are the only way to tie the two together.
     */
    public static class Result {
        public final Map<String, List<Program>> programsByXmltvId;
        public final Map<String, String> xmltvIdByAlias;

        Result(Map<String, List<Program>> programs, Map<String, String> aliases) {
            this.programsByXmltvId = programs;
            this.xmltvIdByAlias = aliases;
        }
    }

    private XmltvParser() {
    }

    @NonNull
    public static Result parse(@NonNull InputStream in)
            throws XmlPullParserException, IOException {

        Map<String, List<Program>> programs = new HashMap<>();
        Map<String, String> aliases = new HashMap<>();

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(in, null);

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("channel".equals(name)) {
                    readChannel(parser, aliases);
                } else if ("programme".equals(name)) {
                    Program p = readProgramme(parser);
                    if (p != null) {
                        List<Program> list = programs.get(p.getChannelId());
                        if (list == null) {
                            list = new ArrayList<>();
                            programs.put(p.getChannelId(), list);
                        }
                        list.add(p);
                    }
                }
            }
            event = parser.next();
        }

        for (List<Program> list : programs.values()) {
            Collections.sort(list, (a, b) -> Long.compare(a.getStartMs(), b.getStartMs()));
        }
        return new Result(programs, aliases);
    }

    private static void readChannel(XmlPullParser parser, Map<String, String> aliases)
            throws XmlPullParserException, IOException {
        String id = parser.getAttributeValue(null, "id");
        if (id != null) {
            aliases.put(id.toLowerCase(Locale.US), id);
        }
        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() <= depth) {
                return;
            }
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && "display-name".equals(parser.getName())) {
                String value = readText(parser);
                if (id != null && value != null) {
                    // First declaration wins, so a name shared by two channels sticks
                    // with the one that claimed it first.
                    String key = value.toLowerCase(Locale.US);
                    if (!aliases.containsKey(key)) {
                        aliases.put(key, id);
                    }
                }
            }
        }
    }

    private static Program readProgramme(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        String channelId = parser.getAttributeValue(null, "channel");
        long start = parseXmltvTime(parser.getAttributeValue(null, "start"));
        long stop = parseXmltvTime(parser.getAttributeValue(null, "stop"));

        String title = null;
        String subTitle = null;
        String desc = null;
        String rating = null;
        String year = null;
        boolean isNew = false;
        boolean isHd = false;
        List<String> categories = new ArrayList<>(3);

        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int event = parser.getEventType();
            if (event == XmlPullParser.END_TAG && parser.getDepth() <= depth) {
                break;
            }
            if (event != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "title":
                    if (title == null) {
                        title = readText(parser);
                    }
                    break;
                case "sub-title":
                    if (subTitle == null) {
                        subTitle = readText(parser);
                    }
                    break;
                case "desc":
                    if (desc == null) {
                        desc = readText(parser);
                    }
                    break;
                case "category":
                    String cat = readText(parser);
                    if (cat != null) {
                        categories.add(cat);
                    }
                    break;
                case "date":
                    String date = readText(parser);
                    if (date != null && date.length() >= 4) {
                        year = date.substring(0, 4);
                    }
                    break;
                case "rating":
                    String r = readRatingValue(parser);
                    if (r != null) {
                        rating = r;
                    }
                    break;
                case "new":
                    isNew = true;
                    break;
                case "video":
                    isHd = readVideoIsHd(parser);
                    break;
                default:
                    break;
            }
        }

        if (channelId == null || title == null || start <= 0L || stop <= start) {
            return null;
        }

        String[] catArray = categories.toArray(new String[0]);
        ProgramCategory category = ProgramCategory.fromXmltv(catArray);
        String categoryLabel = categories.isEmpty() ? null : categories.get(0);

        return new Program(channelId, title, subTitle, desc, start, stop,
                category, categoryLabel, rating, year, isNew, isHd);
    }

    /** Reads {@code <rating><value>TV-PG</value></rating>}. */
    private static String readRatingValue(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String value = null;
        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() <= depth) {
                break;
            }
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && "value".equals(parser.getName())) {
                value = readText(parser);
            }
        }
        return value;
    }

    /** Reads {@code <video><quality>HDTV</quality></video>}. */
    private static boolean readVideoIsHd(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        boolean hd = false;
        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.END_TAG && parser.getDepth() <= depth) {
                break;
            }
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && "quality".equals(parser.getName())) {
                String q = readText(parser);
                if (q != null) {
                    String lower = q.toLowerCase(Locale.US);
                    hd = lower.contains("hd") || lower.contains("1080") || lower.contains("720");
                }
            }
        }
        return hd;
    }

    private static String readText(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        StringBuilder sb = new StringBuilder();
        int depth = parser.getDepth();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int event = parser.getEventType();
            if (event == XmlPullParser.END_TAG && parser.getDepth() <= depth) {
                break;
            }
            if (event == XmlPullParser.TEXT) {
                sb.append(parser.getText());
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Parses an XMLTV timestamp: {@code yyyyMMddHHmmss} optionally followed by a
     * {@code +HHMM} offset. Without an offset the value is treated as device-local.
     */
    static long parseXmltvTime(String raw) {
        if (raw == null) {
            return 0L;
        }
        String s = raw.trim();
        if (s.length() < 14) {
            return 0L;
        }
        try {
            Calendar cal = Calendar.getInstance();
            String offset = s.length() > 14 ? s.substring(14).trim() : "";
            if (offset.length() >= 5 && (offset.charAt(0) == '+' || offset.charAt(0) == '-')) {
                cal.setTimeZone(TimeZone.getTimeZone("GMT" + offset));
            } else {
                cal.setTimeZone(TimeZone.getDefault());
            }
            cal.clear();
            cal.set(Integer.parseInt(s.substring(0, 4)),
                    Integer.parseInt(s.substring(4, 6)) - 1,
                    Integer.parseInt(s.substring(6, 8)),
                    Integer.parseInt(s.substring(8, 10)),
                    Integer.parseInt(s.substring(10, 12)),
                    Integer.parseInt(s.substring(12, 14)));
            return cal.getTimeInMillis();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
