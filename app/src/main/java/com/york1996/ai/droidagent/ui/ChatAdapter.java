package com.york1996.ai.droidagent.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.york1996.ai.droidagent.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天列表适配器，支持 5 种消息类型
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<UiMessage> messages = new ArrayList<>();

    /** 当前正在流式写入的气泡位置，-1 表示无流式进行中 */
    private int streamingPosition = -1;

    // ───────────────────────── Data ─────────────────────────

    public void addMessage(UiMessage msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    /** 移除最后一条 TYPE_THINKING 占位符 */
    public void removeThinking() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).type == UiMessage.TYPE_THINKING) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    /**
     * 开始流式输出：移除 thinking 气泡，插入一条空 assistant 气泡并记录其位置。
     * 必须在主线程调用。
     */
    public void startStreamingMessage() {
        removeThinking();
        UiMessage msg = UiMessage.assistant("");
        messages.add(msg);
        streamingPosition = messages.size() - 1;
        notifyItemInserted(streamingPosition);
    }

    /**
     * 向当前流式气泡追加文字，并局部刷新该条目。
     * 必须在主线程调用。
     */
    public void appendStreamingToken(String token) {
        if (streamingPosition < 0 || streamingPosition >= messages.size()) return;
        messages.get(streamingPosition).content += token;
        notifyItemChanged(streamingPosition);
    }

    /** 流式结束，重置位置标记。 */
    public void finishStreaming() {
        streamingPosition = -1;
    }

    public List<UiMessage> getMessages() {
        return messages;
    }

    public void clear() {
        int size = messages.size();
        messages.clear();
        notifyItemRangeRemoved(0, size);
    }

    // ───────────────────────── RecyclerView ─────────────────────────

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case UiMessage.TYPE_USER:
                return new UserVH(inflater.inflate(R.layout.item_chat_user, parent, false));
            case UiMessage.TYPE_ASSISTANT:
                return new AssistantVH(inflater.inflate(R.layout.item_chat_assistant, parent, false));
            case UiMessage.TYPE_TOOL_CALL:
                return new ToolCallVH(inflater.inflate(R.layout.item_chat_tool_call, parent, false));
            case UiMessage.TYPE_TOOL_RESULT:
                return new ToolResultVH(inflater.inflate(R.layout.item_chat_tool_result, parent, false));
            case UiMessage.TYPE_THINKING:
            default:
                return new ThinkingVH(inflater.inflate(R.layout.item_chat_thinking, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UiMessage msg = messages.get(position);
        switch (msg.type) {
            case UiMessage.TYPE_USER:
                ((UserVH) holder).bind(msg);
                break;
            case UiMessage.TYPE_ASSISTANT:
                ((AssistantVH) holder).bind(msg);
                break;
            case UiMessage.TYPE_TOOL_CALL:
                ((ToolCallVH) holder).bind(msg);
                break;
            case UiMessage.TYPE_TOOL_RESULT:
                ((ToolResultVH) holder).bind(msg);
                break;
            case UiMessage.TYPE_THINKING:
                // 动画已在布局中定义
                break;
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ───────────────────────── ViewHolders ─────────────────────────

    static class UserVH extends RecyclerView.ViewHolder {
        TextView tvContent;
        UserVH(View v) {
            super(v);
            tvContent = v.findViewById(R.id.tv_content);
        }
        void bind(UiMessage msg) { tvContent.setText(msg.content); }
    }

    static class AssistantVH extends RecyclerView.ViewHolder {
        TextView tvContent;
        AssistantVH(View v) {
            super(v);
            tvContent = v.findViewById(R.id.tv_content);
        }
        void bind(UiMessage msg) { tvContent.setText(msg.content); }
    }

    static class ToolCallVH extends RecyclerView.ViewHolder {
        TextView tvToolName, tvArguments;
        ToolCallVH(View v) {
            super(v);
            tvToolName = v.findViewById(R.id.tv_tool_name);
            tvArguments = v.findViewById(R.id.tv_arguments);
        }
        void bind(UiMessage msg) {
            tvToolName.setText("Calling: " + msg.toolName);
            // 格式化 JSON 参数显示
            String args = msg.content;
            if (args != null && args.startsWith("{")) {
                try {
                    args = formatJson(args);
                } catch (Exception ignored) {}
            }
            tvArguments.setText(args);
        }
    }

    static class ToolResultVH extends RecyclerView.ViewHolder {
        TextView tvToolName, tvResult;
        View statusBar;
        ToolResultVH(View v) {
            super(v);
            tvToolName = v.findViewById(R.id.tv_tool_name);
            tvResult   = v.findViewById(R.id.tv_result);
            statusBar  = v.findViewById(R.id.view_status_bar);
        }
        void bind(UiMessage msg) {
            tvToolName.setText(msg.toolName + " result");
            tvResult.setText(msg.content);
            if (statusBar != null) {
                int color = msg.toolSuccess
                        ? 0xFF4CAF50  // green
                        : 0xFFF44336; // red
                statusBar.setBackgroundColor(color);
            }
        }
    }

    static class ThinkingVH extends RecyclerView.ViewHolder {
        ThinkingVH(View v) { super(v); }
    }

    /** 简单 JSON 格式化（缩进） */
    private static String formatJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (char c : json.toCharArray()) {
            if (c == '"' && (sb.length() == 0 || sb.charAt(sb.length() - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (!inString && (c == '{' || c == '[')) {
                sb.append(c).append('\n').append(repeat("  ", ++indent));
            } else if (!inString && (c == '}' || c == ']')) {
                sb.append('\n').append(repeat("  ", --indent)).append(c);
            } else if (!inString && c == ',') {
                sb.append(c).append('\n').append(repeat("  ", indent));
            } else if (!inString && c == ':') {
                sb.append(": ");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
