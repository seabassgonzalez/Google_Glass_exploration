package com.google.android.glass.stub;

/**
 * Stub implementations of Glass APIs for compilation without GDK
 * These are minimal implementations to allow code to compile
 * Actual functionality requires the real GDK on a Glass device
 */

// Stub for GestureDetector
package com.google.android.glass.touchpad;

import android.content.Context;
import android.view.MotionEvent;

public class GestureDetector {
    public GestureDetector(Context context) {}
    
    public boolean onMotionEvent(MotionEvent event) { return false; }
    
    public GestureDetector setBaseListener(BaseListener listener) { return this; }
    
    public interface BaseListener {
        boolean onGesture(Gesture gesture);
    }
    
    public enum Gesture {
        TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT,
        TWO_TAP, TWO_LONG_PRESS, THREE_TAP, THREE_LONG_PRESS
    }
}

// Stub for Sounds
package com.google.android.glass.media;

public class Sounds {
    public static final int TAP = 13;
    public static final int DISMISSED = 15;
    public static final int SUCCESS = 12;
    public static final int ERROR = 11;
}

// Stub for CardBuilder
package com.google.android.glass.widget;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

public class CardBuilder {
    private Context context;
    private String text;
    private String footnote;
    
    public CardBuilder(Context context, Layout layout) {
        this.context = context;
    }
    
    public CardBuilder setText(String text) {
        this.text = text;
        return this;
    }
    
    public CardBuilder setFootnote(String footnote) {
        this.footnote = footnote;
        return this;
    }
    
    public View getView() {
        // Return a simple TextView as placeholder
        TextView tv = new TextView(context);
        tv.setText(text + "\n\n" + footnote);
        tv.setTextSize(30);
        tv.setPadding(40, 40, 40, 40);
        return tv;
    }
    
    public enum Layout {
        TEXT, COLUMNS, CAPTION, TITLE, AUTHOR, MENU, ALERT, EMBED_INSIDE
    }
}