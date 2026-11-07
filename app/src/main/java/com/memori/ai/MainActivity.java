package com.memori.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 41;
    private static final int REQUEST_AUDIO = 42;
    private static final String PREFS = "memori_settings";
    private static final String KEY_ENDPOINT = "chat_endpoint";
    private static final String KEY_CHARACTER = "selected_character";
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean onboarding = false;
    private boolean waitingForReply = false;
    private TextView status;
    private Button micButton;
    private final ExecutorService network = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(5, 13, 32));
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 32));
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int language = tts.setLanguage(Locale.getDefault());
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA &&
                        language != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        if (prefs.getString(KEY_CHARACTER, null) == null) showCharacterPicker();
        else showVoiceHome();
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable panel(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF07142D, 0xFF091D3D, 0xFF050B19});
        root.setBackground(bg);
        return root;
    }

    private void showCharacterPicker() {
        LinearLayout root = page();
        root.setGravity(Gravity.CENTER);
        TextView logo = label("M E M O R I", 15, 0xFF73D8FF, true);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView ai = label("AI", 12, 0xFF4DAEFF, true);
        root.addView(ai);
        TextView title = label(getString(R.string.choose_character), 24, Color.WHITE, true);
        title.setPadding(0, dp(24), 0, dp(8));
        root.addView(title);
        TextView sub = label(getString(R.string.choose_character_subtitle), 14, 0xFF9EB9D9, false);
        root.addView(sub);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        cards.setPadding(0, dp(30), 0, dp(22));
        addCharacterCard(cards, "male");
        addCharacterCard(cards, "female");
        root.addView(cards, new LinearLayout.LayoutParams(-1, dp(300)));
        TextView note = label(getString(R.string.voice_first_note), 13, 0xFF8DA7C8, false);
        note.setPadding(dp(12), dp(10), dp(12), 0);
        root.addView(note);
        setContentView(root);
    }

    private void addCharacterCard(LinearLayout parent, String gender) {
        boolean male = gender.equals("male");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(14), dp(8), dp(16));
        card.setBackground(panel(0xFF10284A, 24, 0xFF286CA5));
        card.setElevation(dp(8));
        card.setOnClickListener(v -> chooseCharacter(gender));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
        cp.setMargins(dp(6), 0, dp(6), 0);
        parent.addView(card, cp);
        FaceView face = new FaceView(this, male);
        card.addView(face, new LinearLayout.LayoutParams(dp(132), dp(174)));
        TextView name = label(male ? getString(R.string.male_character) : getString(R.string.female_character),
                18, Color.WHITE, true);
        name.setPadding(0, dp(10), 0, dp(3));
        card.addView(name);
        TextView choose = label(getString(R.string.tap_to_choose), 12, 0xFF8FDFFF, false);
        card.addView(choose);
    }

    private void chooseCharacter(String gender) {
        prefs.edit().putString(KEY_CHARACTER, gender).apply();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onboarding = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
        } else showVoiceHome();
    }

    private void showVoiceHome() {
        boolean male = "male".equals(prefs.getString(KEY_CHARACTER, "male"));
        LinearLayout root = page();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("MEMORI AI", 20, 0xFF7CDBFF, true);
        brand.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = smallButton("⚙");
        settings.setOnClickListener(v -> showServerSettings());
        header.addView(settings);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView mode = label(getString(R.string.voice_companion), 14, 0xFF9DBCE0, true);
        mode.setPadding(0, dp(14), 0, dp(2));
        root.addView(mode);
        FaceView face = new FaceView(this, male);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(220), dp(250));
        fp.topMargin = dp(12);
        root.addView(face, fp);

        TextView character = label(male ? getString(R.string.male_character) : getString(R.string.female_character),
                22, Color.WHITE, true);
        character.setPadding(0, dp(4), 0, dp(2));
        root.addView(character);
        status = label(getString(R.string.ready_to_listen), 14, 0xFF91B7D9, false);
        status.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(status);

        micButton = new Button(this);
        micButton.setText("🎙  " + getString(R.string.start_talking));
        micButton.setTextSize(17);
        micButton.setTextColor(Color.WHITE);
        micButton.setAllCaps(false);
        micButton.setBackground(panel(0xFF087BC4, 32, 0xFF62D7FF));
        micButton.setElevation(dp(8));
        micButton.setOnClickListener(v -> startListening());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(62));
        mp.setMargins(dp(8), dp(18), dp(8), dp(10));
        root.addView(micButton, mp);

        Button change = smallButton(getString(R.string.change_character));
        change.setOnClickListener(v -> showCharacterPicker());
        root.addView(change, new LinearLayout.LayoutParams(-2, dp(46)));

        TextView privacy = label(getString(R.string.mic_privacy_note), 11, 0xFF6F8CAC, false);
        privacy.setPadding(dp(12), dp(14), dp(12), 0);
        root.addView(privacy);
        setContentView(root);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(0xFFD6F2FF);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackground(panel(0xFF142D4D, 18, 0xFF28577F));
        return b;
    }

    private void startListening() {
        if (waitingForReply) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try {
            status.setText(getString(R.string.listening_now));
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (Exception e) {
            status.setText(getString(R.string.speech_unavailable));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) sendToAssistant(results.get(0));
                else status.setText(getString(R.string.ready_to_listen));
            } else if (status != null) status.setText(getString(R.string.ready_to_listen));
        }
    }

    private void sendToAssistant(String spoken) {
        String endpoint = prefs.getString(KEY_ENDPOINT, "");
        if (endpoint.isEmpty()) {
            status.setText(getString(R.string.server_not_configured));
            showServerSettings();
            return;
        }
        waitingForReply = true;
        micButton.setEnabled(false);
        status.setText(getString(R.string.thinking));
        network.execute(() -> {
            String reply;
            try { reply = requestReply(endpoint, spoken); }
            catch (Exception e) { reply = getString(R.string.connection_problem); }
            final String answer = reply;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                waitingForReply = false;
                micButton.setEnabled(true);
                status.setText(answer);
                if (tts != null && ttsReady) {
                    int language = tts.setLanguage(Locale.getDefault());
                    if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED)
                        tts.setLanguage(Locale.US);
                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "memori_voice_reply");
                }
            });
        });
    }

    private String requestReply(String endpoint, String message) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection)new URL(endpoint + "/chat").openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            JSONObject body = new JSONObject();
            body.put("message", message);
            body.put("language", Locale.getDefault().toLanguageTag());
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder response = new StringBuilder();
            if (stream != null) try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            String reply = new JSONObject(response.toString()).optString("reply", "").trim();
            if (reply.isEmpty()) throw new IllegalStateException("Empty reply");
            return reply;
        } finally { if (c != null) c.disconnect(); }
    }

    private void showServerSettings() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://your-ai-server.com");
        input.setText(prefs.getString(KEY_ENDPOINT, ""));
        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_server)
                .setMessage(R.string.ai_server_help)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.clear, (d, w) -> prefs.edit().remove(KEY_ENDPOINT).apply())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty() && !value.startsWith("https://")) {
                        Toast.makeText(this, R.string.https_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
                    prefs.edit().putString(KEY_ENDPOINT, value).apply();
                    if (status != null) status.setText(getString(R.string.ready_to_listen));
                }).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_AUDIO) {
            if (onboarding) {
                onboarding = false;
                showVoiceHome();
            }
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    private class FaceView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean male;
        FaceView(Activity context, boolean male) { super(context); this.male = male; setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), cx = w/2f;
            float scale = Math.min(w/220f, h/250f);
            canvas.save();
            canvas.scale(scale, scale, cx, h/2f);
            p.setShader(new RadialGradient(cx, 118, 112, new int[]{0x6652C8FF,0x222B8FE8,0x002B8FE8}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, 118, 112, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
            p.setColor(0xFF4FCBFF); canvas.drawCircle(cx, 118, 91, p);
            p.setColor(0x8856AFFF); p.setStrokeWidth(1); canvas.drawCircle(cx, 118, 99, p);
            p.setStyle(Paint.Style.FILL);
            // shoulders and clothing
            p.setShader(new LinearGradient(35, 180, 180, 250, 0xFF1C5E9B, 0xFF071A3C, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(39, 177, 181, 270), 48, 48, p); p.setShader(null);
            // neck
            p.setColor(0xFFB87960); canvas.drawRoundRect(new RectF(91, 150, 129, 198), 13, 13, p);
            // ears
            p.setColor(0xFFC98C70); canvas.drawOval(new RectF(64, 91, 83, 126), p);
            canvas.drawOval(new RectF(137, 91, 156, 126), p);
            // face
            p.setShader(new LinearGradient(65, 48, 153, 165, 0xFFF0C5A6, 0xFFB97860, Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(72, 43, 148, 163), p); p.setShader(null);
            // hair
            p.setColor(male ? 0xFF182338 : 0xFF241B2A);
            if (male) {
                canvas.drawOval(new RectF(69, 36, 151, 91), p);
                canvas.drawRect(70, 67, 82, 91, p); canvas.drawRect(138, 66, 150, 91, p);
            } else {
                canvas.drawOval(new RectF(62, 31, 158, 151), p);
                p.setColor(0xFFEFC4A5); canvas.drawOval(new RectF(73, 48, 147, 161), p);
                p.setColor(0xFF241B2A); canvas.drawOval(new RectF(68, 32, 152, 86), p);
                canvas.drawRoundRect(new RectF(60, 75, 78, 185), 10, 10, p);
                canvas.drawRoundRect(new RectF(142, 75, 160, 185), 10, 10, p);
            }
            // eyes, brows, nose and smile
            p.setColor(0xFF342A2A);
            canvas.drawRoundRect(new RectF(88, 96, 101, 100), 3, 3, p);
            canvas.drawRoundRect(new RectF(119, 96, 132, 100), 3, 3, p);
            p.setColor(0xFF6C4037); canvas.drawLine(110, 101, 106, 119, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            p.setColor(0xFF8B4C48); canvas.drawArc(new RectF(99, 121, 121, 137), 15, 150, false, p);
            p.setStyle(Paint.Style.FILL);
            if (male) {
                p.setColor(0xFF704638);
                canvas.drawOval(new RectF(80, 130, 140, 163), p);
                p.setColor(0xFFB97860); canvas.drawOval(new RectF(91, 128, 129, 145), p);
            }
            // blue rim accents
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFF63D9FF);
            canvas.drawArc(new RectF(30, 15, 190, 205), 205, 105, false, p);
            p.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
