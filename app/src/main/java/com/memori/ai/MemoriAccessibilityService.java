package com.memori.ai;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.content.SharedPreferences;

/**
 * Opt-in bridge. A single text insertion is armed only after an in-app
 * confirmation, then consumed on the next focused editable field.
 */
public class MemoriAccessibilityService extends AccessibilityService {
    private static volatile MemoriAccessibilityService instance;
    private boolean inserting = false;

    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (inserting || event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        SharedPreferences prefs = getSharedPreferences("memori_settings", MODE_PRIVATE);
        if (!prefs.getBoolean("pending_text_armed", false)) return;
        String text = prefs.getString("pending_text", "");
        if (text.isEmpty()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null || !focused.isEditable()) return;
            inserting = true;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean done = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (done) prefs.edit().putBoolean("pending_text_armed", false)
                    .remove("pending_text").apply();
        } finally {
            root.recycle();
            inserting = false;
        }
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
    public static boolean isConnected() { return instance != null; }
}
