package com.memori.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
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
    private static final int REQUEST_AUDIO_PERMISSION = 42;
    private static final String PREFS = "memori_settings";
    private static final String KEY_ENDPOINT = "chat_endpoint";
    private static final String KEY_CHARACTER = "selected_character";
    private boolean onboardingPermissionRequest = false;

    private TextView conversation;
    private EditText messageInput;
    private Button sendButton;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(0xFF080D18);
        getWindow().setNavigationBarColor(0xFF080D18);
        if (preferences.getString(KEY_CHARACTER, null) == null) buildCharacterSelection();
        else buildInterface();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                int result = tts.setLanguage(Locale.getDefault());
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void buildCharacterSelection() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(32), dp(24), dp(28));
        root.setBackgroundColor(0xFF070B16);

        TextView brand = new TextView(this);
        brand.setText("MEMORI AI");
        brand.setTextColor(0xFF83DFFF);
        brand.setTextSize(17);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        TextView headline = new TextView(this);
        headline.setText("اختار الشخصية اللي تحب تتكلم معاها");
        headline.setTextColor(0xFFF5F7FC);
        headline.setTextSize(23);
        headline.setTypeface(null, android.graphics.Typeface.BOLD);
        headline.setGravity(Gravity.CENTER);
        headline.setPadding(0, dp(26), 0, dp(8));
        root.addView(headline);

        TextView hint = new TextView(this);
        hint.setText("تقدر تغيّر اختيارك في أي وقت");
        hint.setTextColor(0xFF9AAAC2);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(30));
        root.addView(hint);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        addCharacterCard(choices, "👨🏻", "ذكر", "male");
        addCharacterCard(choices, "👩🏻", "أنثى", "female");
        root.addView(choices, new LinearLayout.LayoutParams(-1, dp(230)));

        TextView footer = new TextView(this);
        footer.setText("بعد الاختيار هنطلب إذن الميكروفون عشان تقدر تتكلم بالصوت");
        footer.setTextColor(0xFF8494AD);
        footer.setTextSize(13);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(8), dp(26), dp(8), 0);
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private void addCharacterCard(LinearLayout parent, String face, String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackgroundColor(0xFF151F32);
        card.setPadding(dp(10), dp(16), dp(10), dp(16));
        card.setElevation(dp(5));
        card.setOnClickListener(v -> selectCharacter(value));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(7), 0, dp(7), 0);
        parent.addView(card, params);

        TextView avatar = new TextView(this);
        avatar.setText(face);
        avatar.setTextSize(62);
        avatar.setGravity(Gravity.CENTER);
        card.addView(avatar, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(0xFFF5F7FC);
        name.setTextSize(18);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));
    }

    private void selectCharacter(String character) {
        preferences.edit().putString(KEY_CHARACTER, character).apply();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onboardingPermissionRequest = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
        } else {
            buildInterface();
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.setBackgroundColor(0xFF080D18);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText("MEMORI AI");
        title.setTextColor(0xFF72D6FF);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = new Button(this);
        settings.setText(R.string.settings);
        settings.setOnClickListener(v -> showEndpointSettings());
        header.addView(settings, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.app_subtitle));
        subtitle.setTextColor(0xFFB8C5D8);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        root.addView(subtitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        conversation = new TextView(this);
        conversation.setText(getString(R.string.welcome_message));
        conversation.setTextColor(0xFFF2F6FC);
        conversation.setTextSize(16);
        conversation.setGravity(Gravity.START);
        conversation.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        conversation.setPadding(dp(14), dp(14), dp(14), dp(14));
        scroll.addView(conversation);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        messageInput = new EditText(this);
        messageInput.setSingleLine(true);
        messageInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setHint(getString(R.string.message_hint));
        messageInput.setTextColor(0xFFFFFFFF);
        messageInput.setHintTextColor(0xFF91A0B5);
        messageInput.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        sendButton = new Button(this);
        sendButton.setText(R.string.send);
        sendButton.setOnClickListener(v -> sendMessage());
        Button voice = new Button(this);
        voice.setText("🎙 " + getString(R.string.voice));
        voice.setOnClickListener(v -> startVoiceInput());
        actions.addView(sendButton, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(voice, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        setContentView(root);
    }

    private void showEndpointSettings() {
        EditText endpointInput = new EditText(this);
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("https://your-domain.example");
        endpointInput.setText(preferences.getString(KEY_ENDPOINT, ""));
        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_server)
                .setMessage(R.string.ai_server_help)
                .setView(endpointInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.clear, (dialog, which) -> {
                    preferences.edit().remove(KEY_ENDPOINT).apply();
                    append(getString(R.string.server_removed));
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String endpoint = endpointInput.getText().toString().trim();
                    if (!endpoint.isEmpty() && !endpoint.startsWith("https://")) {
                        Toast.makeText(this, R.string.https_required, Toast.LENGTH_LONG).show();
                        return;
                    }
                    preferences.edit().putString(KEY_ENDPOINT, trimTrailingSlash(endpoint)).apply();
                    append(getString(R.string.server_saved));
                }).show();
    }

    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        String endpoint = preferences.getString(KEY_ENDPOINT, "");
        if (endpoint.isEmpty()) {
            append(getString(R.string.you_prefix) + text);
            messageInput.setText("");
            append(getString(R.string.configure_server));
            showEndpointSettings();
            return;
        }
        append(getString(R.string.you_prefix) + text);
        messageInput.setText("");
        setSending(true);
        networkExecutor.execute(() -> {
            String reply;
            try {
                reply = requestAssistantReply(endpoint, text);
            } catch (Exception error) {
                reply = getString(R.string.connection_error) + " " + safeMessage(error);
            }
            final String result = reply;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                append(result);
                speak(result);
                setSending(false);
            });
        });
    }

    private String requestAssistantReply(String endpoint, String message) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint + "/chat");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            payload.put("language", Locale.getDefault().toLanguageTag());
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ?
                    connection.getInputStream() : connection.getErrorStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(getString(R.string.server_error) + " " + status);
            }
            JSONObject response = new JSONObject(body.toString());
            String answer = response.optString("reply", "").trim();
            if (answer.isEmpty()) throw new IllegalStateException(getString(R.string.invalid_server_response));
            return answer;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private void setSending(boolean sending) {
        sendButton.setEnabled(!sending);
        sendButton.setText(sending ? R.string.working : R.string.send);
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
            return;
        }
        launchRecognizer();
    }

    private void launchRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_prompt));
        try { startActivityForResult(intent, REQUEST_SPEECH); }
        catch (Exception e) { append(getString(R.string.speech_unavailable)); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                messageInput.setText(results.get(0));
                sendMessage();
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO_PERMISSION && onboardingPermissionRequest) {
            onboardingPermissionRequest = false;
            buildInterface();
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_AUDIO_PERMISSION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) launchRecognizer();
        else if (requestCode == REQUEST_AUDIO_PERMISSION) append(getString(R.string.mic_permission_required));
    }

    private void append(String line) {
        if (conversation == null) return;
        conversation.append("\n\n" + line);
    }

    private void speak(String text) {
        if (ttsReady && tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "memori_reply");
    }

    @Override protected void onDestroy() {
        networkExecutor.shutdownNow();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
