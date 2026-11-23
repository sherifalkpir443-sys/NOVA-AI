package com.memori.ai;

import android.Manifest;
import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
    private static final int REQUEST_NOTIFICATIONS = 43;
    private static final String PREFS = "memori_settings";
    private static final String KEY_ENDPOINT = "chat_endpoint";
    private static final String KEY_CHARACTER = "selected_character";
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean ttsReady;
    private boolean onboarding = false;
    private boolean waitingForReply = false;
    private TextView status;
    private FaceView activeFace;
    private Button micButton;
    private Button backgroundButton;
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
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {
                        runOnUiThread(() -> { if (activeFace != null) activeFace.setSpeaking(true); });
                    }
                    @Override public void onDone(String utteranceId) {
                        runOnUiThread(() -> { if (activeFace != null) activeFace.setSpeaking(false); });
                    }
                    @Override public void onError(String utteranceId) {
                        runOnUiThread(() -> { if (activeFace != null) activeFace.setSpeaking(false); });
                    }
                });
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
                new int[]{0xFF063B83, 0xFF0756A8, 0xFF031B46});
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
        card.setBackground(panel(0xFF0B3972, 24, 0xFF48BFFF));
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
        activeFace = new FaceView(this, male);
        FaceView face = activeFace;
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

        backgroundButton = smallButton(WakeWordService.isRunning
                ? "إيقاف الاستعداد بالخلفية" : "تفعيل الاستعداد بالخلفية");
        backgroundButton.setOnClickListener(v -> toggleBackgroundListening());
        root.addView(backgroundButton, new LinearLayout.LayoutParams(-1, dp(48)));

        Button deviceControl = smallButton("التحكم في التطبيقات والكتابة");
        deviceControl.setOnClickListener(v -> showDeviceControls());
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(-1, dp(48));
        controlParams.topMargin = dp(8);
        root.addView(deviceControl, controlParams);

        Button updateButton = smallButton("فحص التحديثات وتنزيل آخر إصدار");
        updateButton.setOnClickListener(v -> openUpdates());
        root.addView(updateButton, new LinearLayout.LayoutParams(-1, dp(46)));

        Button change = smallButton(getString(R.string.change_character));
        change.setOnClickListener(v -> showCharacterPicker());
        root.addView(change, new LinearLayout.LayoutParams(-2, dp(46)));

        TextView privacy = label(getString(R.string.mic_privacy_note), 11, 0xFF6F8CAC, false);
        privacy.setPadding(dp(12), dp(14), dp(12), 0);
        root.addView(privacy);
        setContentView(root);
    }

    private void toggleBackgroundListening() {
        if (WakeWordService.isRunning) {
            stopService(new Intent(this, WakeWordService.class)
                    .setAction("com.memori.ai.STOP_WAKE_WORD"));
            backgroundButton.setText("تفعيل الاستعداد بالخلفية");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        Intent service = new Intent(this, WakeWordService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        backgroundButton.setText("إيقاف الاستعداد بالخلفية");
        Toast.makeText(this, "تم طلب تشغيل الاستعداد الصوتي المحلي", Toast.LENGTH_LONG).show();
    }

    private void showDeviceControls() {
        String[] options = {
                "تفعيل صلاحية التحكم والكتابة",
                "كتابة نص في تطبيق آخر بموافقتي",
                "إعدادات الهاتف",
                "إدارة التطبيقات",
                "فتح الآلة الحاسبة"
        };
        new AlertDialog.Builder(this)
                .setTitle("أدوات MEMORI للهاتف")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("صلاحية إمكانية الوصول")
                                .setMessage("السماح لـ MEMORI باستخدام خدمة إمكانية الوصول اختياري. بعد تفعيلها، لن يُكتب أي نص إلا بعد تأكيدك داخل التطبيق، وسيُدرج النص مرة واحدة في أول خانة كتابة تركز عليها في التطبيق الآخر. لا نقرأ محتوى الشاشة ولا نرسله للخادم.")
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("فتح إعدادات الصلاحية", (d, w) ->
                                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                                .show();
                    } else if (which == 1) {
                        if (!MemoriAccessibilityService.isConnected()) {
                            new AlertDialog.Builder(this)
                                    .setMessage("فعّل خدمة إمكانية الوصول أولاً، ثم ارجع هنا.")
                                    .setPositiveButton("فتح الإعدادات", (d, w) ->
                                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                                    .setNegativeButton("إلغاء", null).show();
                            return;
                        }
                        EditText textInput = new EditText(this);
                        textInput.setHint("اكتب النص اللي عايز MEMORI تدخله");
                        textInput.setMinLines(3);
                        new AlertDialog.Builder(this)
                                .setTitle("النص المطلوب")
                                .setView(textInput)
                                .setNegativeButton("إلغاء", null)
                                .setPositiveButton("متابعة", (d, w) -> {
                                    String text = textInput.getText().toString();
                                    if (text.trim().isEmpty()) return;
                                    new AlertDialog.Builder(this)
                                            .setTitle("تأكيد الكتابة")
                                            .setMessage("بعد موافقتك، افتح التطبيق المطلوب واضغط على خانة الكتابة. MEMORI هيدخل النص مرة واحدة في أول خانة مناسبة.")
                                            .setNegativeButton("إلغاء", null)
                                            .setPositiveButton("أوافق واكتب", (confirm, cw) -> {
                                                prefs.edit().putString("pending_text", text)
                                                        .putBoolean("pending_text_armed", true).apply();
                                                Toast.makeText(this, "افتح التطبيق المطلوب واضغط داخل خانة الكتابة.", Toast.LENGTH_LONG).show();
                                            }).show();
                                }).show();
                    } else if (which == 2) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } else if (which == 3) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                    } else {
                        openCalculator();
                    }
                }).show();
    }

    private void openCalculator() {
        Intent calculator = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_CALCULATOR);
        if (calculator.resolveActivity(getPackageManager()) != null) {
            startActivity(calculator);
        } else {
            Toast.makeText(this, "مش لاقي تطبيق آلة حاسبة مثبت على الجهاز.", Toast.LENGTH_LONG).show();
        }
    }

    private void openUpdates() {
        new AlertDialog.Builder(this)
                .setTitle("تحديث MEMORI AI")
                .setMessage("هتفتح صفحة بناء التطبيق على GitHub. افتح أحدث عملية ناجحة ونزّل ملف MEMORI-AI-debug-apk، وبعدها فك الضغط وثبّت APK. أندرويد هيطلب موافقتك على التثبيت.")
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("فتح صفحة التحديث", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/sherifalkpir443-sys/NOVA-AI/actions/workflows/android-build.yml"));
                    startActivity(intent);
                }).show();
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
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText(getString(R.string.speech_unavailable));
            return;
        }
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    runOnUiThread(() -> status.setText(getString(R.string.listening_now)));
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    runOnUiThread(() -> status.setText(getString(R.string.thinking)));
                }
                @Override public void onError(int error) {
                    runOnUiThread(() -> {
                        if (!waitingForReply && status != null)
                            status.setText(getString(R.string.ready_to_listen));
                    });
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    runOnUiThread(() -> {
                        if (matches != null && !matches.isEmpty()) sendToAssistant(matches.get(0));
                        else status.setText(getString(R.string.ready_to_listen));
                    });
                }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            speechRecognizer.startListening(intent);
            status.setText(getString(R.string.listening_now));
        } catch (Exception e) {
            status.setText(getString(R.string.speech_unavailable));
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
                    if (activeFace != null) activeFace.setSpeaking(true);
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
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_LONG).show();
            } else if (!onboarding && backgroundButton != null) {
                toggleBackgroundListening();
            }
        }
    }

    private class FaceView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean male;
        private boolean speaking = false;
        private float blink = 0f;
        private final Handler animationHandler = new Handler(Looper.getMainLooper());
        private final Runnable blinkLoop = new Runnable() {
            @Override public void run() {
                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f, 0f);
                animator.setDuration(190);
                animator.addUpdateListener(a -> {
                    blink = (float) a.getAnimatedValue();
                    invalidate();
                });
                animator.start();
                animationHandler.postDelayed(this, 3400);
            }
        };
        FaceView(Activity context, boolean male) {
            super(context); this.male = male;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            animationHandler.removeCallbacks(blinkLoop);
            animationHandler.postDelayed(blinkLoop, 900);
        }
        @Override protected void onDetachedFromWindow() {
            animationHandler.removeCallbacks(blinkLoop);
            super.onDetachedFromWindow();
        }
        void setSpeaking(boolean value) {
            speaking = value;
            invalidate();
        }
        private void fill(Canvas c, int color, float l, float t, float r, float b) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color);
            c.drawOval(new RectF(l,t,r,b),p);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight(), cx=w/2f;
            float scale=Math.min(w/220f,h/250f);
            canvas.save(); canvas.scale(scale,scale,cx,h/2f);

            // Deep blue holographic atmosphere and luminous rings
            p.setShader(new RadialGradient(cx,119,116,
                    new int[]{0xAA35BFFF,0x55318FF0,0x002B75D8},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,119,116,p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.4f); p.setColor(0xFF59D7FF);
            canvas.drawCircle(cx,119,96,p);
            p.setStrokeWidth(1f); p.setColor(0x7767BFFF); canvas.drawCircle(cx,119,106,p);
            p.setColor(0x995DEBFF); canvas.drawArc(new RectF(22,18,198,218),205,92,false,p);
            canvas.drawArc(new RectF(29,25,191,211),25,80,false,p);
            p.setStyle(Paint.Style.FILL);

            // shoulders, collar and neck with dimensional gradients
            p.setShader(new LinearGradient(36,178,184,260,
                    new int[]{0xFF2B83C8,0xFF123F79,0xFF071D43},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(32,178,188,270),58,58,p); p.setShader(null);
            p.setShader(new LinearGradient(88,151,132,198,0xFFE8B89A,0xFF8C5147,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(91,145,129,198),14,14,p); p.setShader(null);
            p.setColor(0xFFB97860);
            canvas.drawRoundRect(new RectF(86,177,134,204),12,12,p);
            p.setColor(0xFFB9DFFF);
            PathLike.drawCollar(canvas,p);

            // ears
            p.setShader(new LinearGradient(62,92,83,130,0xFFE9B99A,0xFF9E5D50,Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(61,91,84,132),p);
            canvas.drawOval(new RectF(136,91,159,132),p); p.setShader(null);

            // face silhouette: layered skin lighting and soft shadow
            p.setShader(new LinearGradient(66,43,154,170,
                    new int[]{0xFFFFE0C4,0xFFE7B99B,0xFFB87964,0xFF8F554B},
                    new float[]{0f,.38f,.78f,1f},Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(69,37,151,169),p); p.setShader(null);
            p.setShader(new RadialGradient(92,79,62,
                    new int[]{0x66FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(72,41,148,166),p); p.setShader(null);

            // hair framing the forehead and temples
            p.setShader(new LinearGradient(66,29,151,96,0xFF344052,0xFF090F20,Shader.TileMode.CLAMP));
            if(male) {
                canvas.drawOval(new RectF(67,27,153,91),p);
                canvas.drawRoundRect(new RectF(68,64,82,112),8,8,p);
                canvas.drawRoundRect(new RectF(138,64,152,112),8,8,p);
            } else {
                canvas.drawOval(new RectF(57,24,163,183),p);
                canvas.drawOval(new RectF(70,36,150,91),p);
                canvas.drawRoundRect(new RectF(57,72,77,190),12,12,p);
                canvas.drawRoundRect(new RectF(143,72,163,190),12,12,p);
            }
            p.setShader(null);
            // forehead highlight and cheeks
            p.setShader(new RadialGradient(93,75,32,0x55FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(77,47,132,104),p); p.setShader(null);
            p.setShader(new RadialGradient(83,126,22,0x33F47F82,0x00F47F82,Shader.TileMode.CLAMP));
            canvas.drawCircle(83,126,22,p); canvas.drawCircle(137,126,22,p); p.setShader(null);

            // brows and expressive eyes
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3.5f); p.setColor(0xFF49312D);
            canvas.drawArc(new RectF(82,82,105,97),205,125,false,p);
            canvas.drawArc(new RectF(115,82,138,97),210,125,false,p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0,94,0,106,0xFFFFFFFF,0xFFD8E7F2,Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(83,94,106,107),p);
            canvas.drawOval(new RectF(114,94,137,107),p); p.setShader(null);
            p.setColor(0xFF253A4B);
            canvas.drawCircle(95,100,5.5f,p); canvas.drawCircle(125,100,5.5f,p);
            p.setColor(0xFF101B2B);
            canvas.drawCircle(96,100,2.7f,p); canvas.drawCircle(126,100,2.7f,p);
            p.setColor(Color.WHITE); canvas.drawCircle(97,98,1.5f,p); canvas.drawCircle(127,98,1.5f,p);
            if (blink > 0f) {
                p.setShader(new LinearGradient(0,94,0,108,0xFFFFE0C4,0xFFE7B99B,Shader.TileMode.CLAMP));
                float lid = 13f * blink;
                canvas.drawRoundRect(new RectF(82,94,107,94+lid),6,6,p);
                canvas.drawRoundRect(new RectF(113,94,138,94+lid),6,6,p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f);
                p.setColor(0xFF8F554B);
                canvas.drawLine(83,94+lid,106,94+lid,p);
                canvas.drawLine(114,94+lid,137,94+lid,p);
                p.setStyle(Paint.Style.FILL);
            }

            // nose bridge and soft nostril shadow
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.1f); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x66864F47); canvas.drawLine(110,103,106,120,p);
            canvas.drawArc(new RectF(101,115,119,129),18,145,false,p);
            p.setStyle(Paint.Style.FILL);

            // mouth moves while TTS is speaking
            if(speaking) {
                float pulse=(float)Math.abs(Math.sin(System.nanoTime()/115_000_000.0));
                float mh=5f+pulse*11f;
                p.setColor(0xFF55232C); canvas.drawOval(new RectF(101,132,119,132+mh),p);
                p.setColor(0xFFE89A9A); canvas.drawOval(new RectF(105,134,115,134+mh*.35f),p);
                postInvalidateDelayed(70);
            } else {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f);
                p.setColor(0xFF98564D); canvas.drawArc(new RectF(99,128,121,143),15,150,false,p);
                p.setStyle(Paint.Style.FILL);
            }

            // subtle facial hair on male avatar
            if(male) {
                p.setShader(new LinearGradient(78,143,142,169,0xAA26303B,0x0026303B,Shader.TileMode.CLAMP));
                canvas.drawArc(new RectF(78,132,142,174),5,170,true,p); p.setShader(null);
                p.setColor(0xFFE0AD8E); canvas.drawOval(new RectF(91,133,129,150),p);
            }

            // blue luminous edge around the portrait
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f); p.setColor(0xFF70E4FF);
            canvas.drawArc(new RectF(29,17,191,218),205,130,false,p);
            p.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
    }

    private static class PathLike {
        static void drawCollar(Canvas canvas, Paint paint) {
            paint.setShader(null); paint.setColor(0xFFB8DFFF);
            android.graphics.Path path=new android.graphics.Path();
            path.moveTo(89,178); path.lineTo(110,194); path.lineTo(131,178);
            path.lineTo(124,211); path.lineTo(96,211); path.close();
            canvas.drawPath(path,paint);
        }
    }

    @Override protected void onDestroy() {
        network.shutdownNow();
        if (speechRecognizer != null) { speechRecognizer.cancel(); speechRecognizer.destroy(); speechRecognizer = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
