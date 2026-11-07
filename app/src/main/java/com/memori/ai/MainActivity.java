package com.memori.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 41;
    private static final int REQUEST_AUDIO_PERMISSION = 42;
    private TextView conversation;
    private EditText messageInput;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF080D18);
        getWindow().setNavigationBarColor(0xFF080D18);
        buildInterface();
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(java.util.Locale.getDefault());
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                           result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 20);
        root.setBackgroundColor(0xFF080D18);

        TextView title = new TextView(this);
        title.setText("MEMORI AI");
        title.setTextColor(0xFF72D6FF);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 4);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.app_subtitle));
        subtitle.setTextColor(0xFFB8C5D8);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 20);
        root.addView(subtitle);

        TextView avatar = new TextView(this);
        avatar.setText("◉");
        avatar.setTextColor(0xFF72D6FF);
        avatar.setTextSize(72);
        avatar.setGravity(Gravity.CENTER);
        root.addView(avatar, new LinearLayout.LayoutParams(-1, 150));

        ScrollView scroll = new ScrollView(this);
        conversation = new TextView(this);
        conversation.setText("MEMORI: أهلاً بيك! أنا MEMORI. تقدر تكتبلي أو تضغط على زر الصوت.\n\nملاحظة: الاتصال بخدمة الذكاء الاصطناعي لسه محتاج إعداد مفتاح الخدمة.");
        conversation.setTextColor(0xFFF2F6FC);
        conversation.setTextSize(16);
        conversation.setGravity(Gravity.RIGHT);
        conversation.setTextDirection(View.TEXT_DIRECTION_RTL);
        conversation.setPadding(16, 16, 16, 16);
        scroll.addView(conversation);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        root.addView(scroll, scrollParams);

        messageInput = new EditText(this);
        messageInput.setSingleLine(true);
        messageInput.setHint(getString(R.string.message_hint));
        messageInput.setTextColor(0xFFFFFFFF);
        messageInput.setHintTextColor(0xFF91A0B5);
        messageInput.setTextDirection(View.TEXT_DIRECTION_RTL);
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button send = new Button(this);
        send.setText(R.string.send);
        send.setOnClickListener(v -> sendMessage());
        Button voice = new Button(this);
        voice.setText("🎙 " + getString(R.string.voice));
        voice.setOnClickListener(v -> startVoiceInput());
        actions.addView(send, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(voice, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        setContentView(root);
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        append(getString(R.string.you_prefix) + text);
        messageInput.setText("");
        append(getString(R.string.demo_reply));
        speak(getString(R.string.demo_reply));
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag());
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
        if (requestCode == REQUEST_AUDIO_PERMISSION && grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) launchRecognizer();
        else if (requestCode == REQUEST_AUDIO_PERMISSION) append(getString(R.string.mic_permission_required));
    }

    private void append(String line) {
        conversation.append("\n\n" + line);
    }

    private void speak(String text) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "memori_reply");
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
