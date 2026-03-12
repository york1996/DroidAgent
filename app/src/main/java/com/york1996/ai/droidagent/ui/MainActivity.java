package com.york1996.ai.droidagent.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.york1996.ai.droidagent.R;
import com.york1996.ai.droidagent.databinding.ActivityMainBinding;

/**
 * DEMO 主界面：聊天 UI
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private AgentViewModel viewModel;
    private ChatAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 开启 edge-to-edge：内容延伸到状态栏和导航栏后面，insets 由下方统一处理
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        viewModel = new ViewModelProvider(this).get(AgentViewModel.class);
        viewModel.init();

        setupRecyclerView();
        setupInput();
        setupWindowInsets();
        observeViewModel();

        // 若 API Key 未配置，引导去设置
        if (viewModel.getConfig().getApiKey().isEmpty()) {
            showApiKeyHint();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页返回后重新加载配置
        viewModel.reload();
    }

    // ───────────────────────── Setup ─────────────────────────

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvChat.setLayoutManager(layoutManager);
        binding.rvChat.setAdapter(adapter);
    }

    /**
     * 统一处理系统窗口 insets：
     * - 状态栏高度 → AppBarLayout 顶部 padding（Toolbar 下移，避免被状态栏遮挡）
     * - 导航栏 + IME 高度 → inputContainer 底部 margin（跟随键盘弹出/收起动画）
     * - inputContainer 高度变化后同步更新 RecyclerView 底部 padding
     */
    private void setupWindowInsets() {
        final int dp8 = Math.round(8 * getResources().getDisplayMetrics().density);

        // 1. 状态栏 → AppBarLayout 顶部 padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, statusBarHeight, 0, 0);
            return insets;
        });

        // 2. 导航栏 / IME → inputContainer 底部 margin，令其始终悬浮于遮挡物上方
        ViewCompat.setOnApplyWindowInsetsListener(binding.inputContainer, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottomInset = Math.max(navBottom, imeBottom);

            CoordinatorLayout.LayoutParams lp =
                    (CoordinatorLayout.LayoutParams) v.getLayoutParams();
            lp.bottomMargin = bottomInset;
            v.setLayoutParams(lp);

            // 3. inputContainer 尺寸稳定后更新 RecyclerView 底部 padding
            //    确保最后一条消息不会被输入栏挡住
            v.post(() -> {
                int inputBarHeight = v.getHeight();
                binding.rvChat.setPadding(dp8, dp8, dp8, inputBarHeight + dp8);
            });

            return insets;
        });
    }

    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> sendMessage());

        binding.etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        // ── 普通消息（用户 / thinking / 工具卡片 / 非流式 assistant）──
        viewModel.getNewMessage().observe(this, msg -> {
            if (msg == null) return;
            if (msg.type != UiMessage.TYPE_THINKING) {
                adapter.removeThinking();
            }
            adapter.addMessage(msg);
            scrollToBottom();
        });

        // ── 流式：收到 true → 创建空 assistant 气泡 ──
        viewModel.getStartStreaming().observe(this, start -> {
            if (Boolean.TRUE.equals(start)) {
                adapter.startStreamingMessage();
                scrollToBottom();
            }
        });

        // ── 流式：收到 token → 追加文字 ──
        viewModel.getStreamingToken().observe(this, token -> {
            if (token == null) return;
            adapter.appendStreamingToken(token);
            scrollToBottom();
        });

        viewModel.getIsLoading().observe(this, loading -> {
            binding.btnSend.setEnabled(!loading);
            binding.etInput.setEnabled(!loading);
            binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (!Boolean.TRUE.equals(loading)) {
                adapter.finishStreaming(); // 确保流式标记重置
            }
        });

        viewModel.getErrorEvent().observe(this, error -> {
            if (error == null || error.isEmpty()) return;
            adapter.removeThinking();
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(error)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    // ───────────────────────── Actions ─────────────────────────

    private void sendMessage() {
        String text = binding.etInput.getText().toString().trim();
        if (text.isEmpty()) return;
        binding.etInput.setText("");
        viewModel.sendMessage(text);
    }

    private void scrollToBottom() {
        binding.rvChat.post(() ->
                binding.rvChat.smoothScrollToPosition(
                        Math.max(0, adapter.getItemCount() - 1)));
    }

    private void showApiKeyHint() {
        new AlertDialog.Builder(this)
                .setTitle("配置 API Key")
                .setMessage("请先在设置中填入 OpenAI 兼容接口的 API Key 和 Base URL，才能使用 Agent 功能。")
                .setPositiveButton("去设置", (d, w) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .setNegativeButton("稍后", null)
                .show();
    }

    // ───────────────────────── Menu ─────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_clear) {
            new AlertDialog.Builder(this)
                    .setTitle("清空对话")
                    .setMessage("确认清空当前对话历史（长期记忆不受影响）？")
                    .setPositiveButton("清空", (d, w) -> {
                        adapter.clear();
                        viewModel.clearHistory();
                        Toast.makeText(this, "对话已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
