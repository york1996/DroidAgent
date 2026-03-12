package com.york1996.ai.droidagent.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.york1996.ai.droidagent.R;
import com.york1996.ai.droidagent.databinding.ActivitySettingsBinding;

/**
 * 设置界面：配置 API Key、Base URL、模型名称
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.settings));
        }

        prefs = getSharedPreferences(AgentViewModel.PREFS_NAME, MODE_PRIVATE);
        loadSettings();

        binding.btnSave.setOnClickListener(v -> saveSettings());

        // 密码显示切换
        binding.cbShowApiKey.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                binding.etApiKey.setTransformationMethod(null);
            } else {
                binding.etApiKey.setTransformationMethod(new PasswordTransformationMethod());
            }
            binding.etApiKey.setSelection(binding.etApiKey.length());
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void loadSettings() {
        binding.etApiKey.setText(prefs.getString(AgentViewModel.KEY_API_KEY, ""));
        binding.etBaseUrl.setText(prefs.getString(AgentViewModel.KEY_BASE_URL, "https://api.openai.com"));
        binding.etChatModel.setText(prefs.getString(AgentViewModel.KEY_CHAT_MODEL, "gpt-4o-mini"));
        binding.etEmbModel.setText(prefs.getString(AgentViewModel.KEY_EMB_MODEL, "text-embedding-ada-002"));
        binding.switchStreaming.setChecked(prefs.getBoolean(AgentViewModel.KEY_STREAMING, false));
    }

    private void saveSettings() {
        String apiKey    = binding.etApiKey.getText().toString().trim();
        String baseUrl   = binding.etBaseUrl.getText().toString().trim();
        String chatModel = binding.etChatModel.getText().toString().trim();
        String embModel  = binding.etEmbModel.getText().toString().trim();
        boolean streaming = binding.switchStreaming.isChecked();

        if (apiKey.isEmpty()) {
            binding.etApiKey.setError("API Key 不能为空");
            return;
        }
        if (baseUrl.isEmpty()) {
            binding.etBaseUrl.setError("Base URL 不能为空");
            return;
        }

        prefs.edit()
                .putString(AgentViewModel.KEY_API_KEY, apiKey)
                .putString(AgentViewModel.KEY_BASE_URL, baseUrl)
                .putString(AgentViewModel.KEY_CHAT_MODEL, chatModel.isEmpty() ? "gpt-4o-mini" : chatModel)
                .putString(AgentViewModel.KEY_EMB_MODEL, embModel.isEmpty() ? "text-embedding-ada-002" : embModel)
                .putBoolean(AgentViewModel.KEY_STREAMING, streaming)
                .apply();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
