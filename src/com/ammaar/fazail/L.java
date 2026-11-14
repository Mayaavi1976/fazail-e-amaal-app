package com.ammaar.fazail;

import java.util.HashMap;
import java.util.Map;

/** Interface language: English or Tamil. The book's own text is not translated here. */
final class L {
    static boolean ta;
    private static final Map<String, String> M = new HashMap<>();

    static String t(String en) {
        if (!ta) return en;
        String s = M.get(en);
        return s == null ? en : s;
    }

    static final String ABOUT_EN = "Offline reader for the English edition of Fazail-e-Amaal.\n\n"
            + "The book opens as text you can resize from the menu; Arabic passages are shown "
            + "exactly as printed. The text was read from the scan by machine and corrected "
            + "automatically, so a few mistakes may remain: use \"Show scanned page\" in the "
            + "menu to check any line against the original.\n\n"
            + "In the scanned view, swipe to turn pages and pinch or double-tap to zoom.";
    static final String ABOUT_TA = "ஃபழாயிலே அஃமால் ஆங்கிலப் பதிப்பிற்கான ஆஃப்லைன் வாசிப்புச் செயலி.\n\n"
            + "புத்தகம் உரையாகத் திறக்கும்; எழுத்தின் அளவை மெனுவில் மாற்றலாம். அரபி வாசகங்கள் "
            + "அச்சில் உள்ளபடியே காட்டப்படுகின்றன. ஆங்கில உரை ஸ்கேனிலிருந்து இயந்திரத்தால் "
            + "படிக்கப்பட்டு தானாகத் திருத்தப்பட்டது; எனவே சில பிழைகள் இருக்கலாம். எந்த வரியையும் "
            + "மூலத்துடன் ஒப்பிட மெனுவில் \"ஸ்கேன் செய்த பக்கத்தைக் காட்டு\" என்பதைப் பயன்படுத்தவும்.\n\n"
            + "ஸ்கேன் காட்சியில், பக்கம் திருப்ப இடம்/வலம் தள்ளவும்; பெரிதாக்க இரு விரலால் விரிக்கவும் "
            + "அல்லது இருமுறை தட்டவும்.";

    static {
        String[] p = {
            "About", "பற்றி",
            "Bigger text", "எழுத்தைப் பெரிதாக்கு",
            "Smaller text", "எழுத்தைச் சிறிதாக்கு",
            "Bookmark removed", "புத்தகக்குறி நீக்கப்பட்டது",
            "Bookmarks (", "புத்தகக்குறிகள் (",
            "Bookmarks", "புத்தகக்குறிகள்",
            "Cancel", "ரத்து",
            "Contents", "பொருளடக்கம்",
            "Continue reading  -  page ", "தொடர்ந்து படிக்க  -  பக்கம் ",
            "Could not open the book", "புத்தகத்தைத் திறக்க முடியவில்லை",
            "Could not open the book: ", "புத்தகத்தைத் திறக்க முடியவில்லை: ",
            "Go to page", "பக்கத்திற்குச் செல்",
            "Go", "செல்",
            "Next >", "அடுத்து >",
            "< Prev", "< முந்தைய",
            "Night mode", "இரவுப் பயன்முறை",
            "Night mode: ", "இரவுப் பயன்முறை: ",
            "On", "இயக்கத்தில்",
            "Off", "நிறுத்தத்தில்",
            "No bookmarks yet.\nOpen a page and tap the star to add one.",
            "இன்னும் புத்தகக்குறிகள் இல்லை.\nஒரு பக்கத்தைத் திறந்து நட்சத்திரத்தைத் தட்டிச் சேர்க்கவும்.",
            "No results found.", "முடிவுகள் எதுவும் கிடைக்கவில்லை.",
            "OK", "சரி",
            "Page ", "பக்கம் ",
            " bookmarked", " புத்தகக்குறியிடப்பட்டது",
            " of ", " / ",
            "Preparing the book...", "புத்தகம் தயாராகிறது...",
            "Read as text", "உரையாகப் படி",
            "Search", "தேடு",
            "Searching...", "தேடுகிறது...",
            "Show scanned page", "ஸ்கேன் செய்த பக்கத்தைக் காட்டு",
            "This book has no contents list.", "இந்தப் புத்தகத்தில் பொருளடக்கம் இல்லை.",
            "This book's pages are images, so the text cannot be searched.",
            "இந்தப் புத்தகத்தின் பக்கங்கள் படங்களாக இருப்பதால் உரையைத் தேட முடியாது.",
            "This is the first page", "இது முதல் பக்கம்",
            "This is the last page", "இது கடைசிப் பக்கம்",
            "This is the first section", "இது முதல் பகுதி",
            "This is the last section", "இது கடைசிப் பகுதி",
            "This page is only available as a scan", "இந்தப் பக்கம் ஸ்கேன் வடிவில் மட்டுமே உள்ளது",
            "Type at least 2 characters.", "குறைந்தது 2 எழுத்துகளை உள்ளிடவும்.",
            "Word or phrase", "சொல் அல்லது சொற்றொடர்",
        };
        for (int i = 0; i < p.length; i += 2) M.put(p[i], p[i + 1]);
    }
}
