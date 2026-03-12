package com.york1996.ai.droidagent.tool;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件读写工具
 * 操作范围限定在 App 内部存储 (filesDir/agent_files/)，沙盒安全
 */
public class FileReadWriteTool implements Tool {

    private static final String TAG = "FileReadWriteTool";
    private static final String DIR_NAME = "agent_files";
    private static final int MAX_READ_CHARS = 8000; // 单次最多读取字符数

    @Override
    public String getName() { return "file_manager"; }

    @Override
    public String getDescription() {
        return "Read, write, list, or delete text files in the app's private storage. "
                + "Supported operations: read, write, append, list, delete.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();

        JsonObject op = new JsonObject();
        op.addProperty("type", "string");
        op.addProperty("description", "Operation: 'read', 'write', 'append', 'list', 'delete'");
        JsonArray opEnum = new JsonArray();
        opEnum.add("read"); opEnum.add("write"); opEnum.add("append");
        opEnum.add("list"); opEnum.add("delete");
        op.add("enum", opEnum);
        props.add("operation", op);

        JsonObject filename = new JsonObject();
        filename.addProperty("type", "string");
        filename.addProperty("description", "File name (without path). Required for read/write/append/delete.");
        props.add("filename", filename);

        JsonObject content = new JsonObject();
        content.addProperty("type", "string");
        content.addProperty("description", "Content to write or append. Required for write/append.");
        props.add("content", content);

        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("operation");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        String operation = params.has("operation") ? params.get("operation").getAsString() : "";
        File dir = getAgentDir(context);

        switch (operation) {
            case "list":   return listFiles(dir);
            case "read":   return readFile(dir, params);
            case "write":  return writeFile(dir, params, false);
            case "append": return writeFile(dir, params, true);
            case "delete": return deleteFile(dir, params);
            default: return ToolResult.error("Unknown operation: " + operation);
        }
    }

    private File getAgentDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private ToolResult listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return ToolResult.ok("No files found in agent storage.");
        }
        StringBuilder sb = new StringBuilder("Files in agent storage:\n");
        for (File f : files) {
            sb.append("• ").append(f.getName())
              .append(" (").append(f.length()).append(" bytes)\n");
        }
        return ToolResult.ok(sb.toString().trim());
    }

    private ToolResult readFile(File dir, JsonObject params) {
        if (!params.has("filename")) return ToolResult.error("Missing parameter: filename");
        String name = sanitize(params.get("filename").getAsString());
        File file = new File(dir, name);
        if (!file.exists()) return ToolResult.error("File not found: " + name);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1 && sb.length() < MAX_READ_CHARS) {
                sb.append((char) ch);
            }
            String content = sb.toString();
            if (content.length() == MAX_READ_CHARS) {
                content += "\n... [truncated at " + MAX_READ_CHARS + " chars]";
            }
            return ToolResult.ok("Content of \"" + name + "\":\n" + content);
        } catch (IOException e) {
            return ToolResult.error("Cannot read file: " + e.getMessage());
        }
    }

    private ToolResult writeFile(File dir, JsonObject params, boolean append) {
        if (!params.has("filename")) return ToolResult.error("Missing parameter: filename");
        if (!params.has("content"))  return ToolResult.error("Missing parameter: content");
        String name = sanitize(params.get("filename").getAsString());
        String content = params.get("content").getAsString();
        File file = new File(dir, name);

        try (FileWriter writer = new FileWriter(file, append)) {
            writer.write(content);
            String op = append ? "Appended" : "Written";
            return ToolResult.ok(op + " " + content.length() + " chars to \"" + name + "\".");
        } catch (IOException e) {
            return ToolResult.error("Cannot write file: " + e.getMessage());
        }
    }

    private ToolResult deleteFile(File dir, JsonObject params) {
        if (!params.has("filename")) return ToolResult.error("Missing parameter: filename");
        String name = sanitize(params.get("filename").getAsString());
        File file = new File(dir, name);
        if (!file.exists()) return ToolResult.error("File not found: " + name);
        return file.delete()
                ? ToolResult.ok("Deleted \"" + name + "\" successfully.")
                : ToolResult.error("Failed to delete \"" + name + "\".");
    }

    /** 防止路径穿越攻击 */
    private String sanitize(String name) {
        return name.replaceAll("[/\\\\\\.\\.:]", "_");
    }
}
