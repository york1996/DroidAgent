package com.york1996.ai.droidagent.tool;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.google.gson.JsonObject;

/**
 * 手机电量查询工具
 * 使用 ACTION_BATTERY_CHANGED 粘性广播读取电池状态，无需任何权限
 */
public class BatteryTool implements Tool {

    @Override
    public String getName() { return "battery_info"; }

    @Override
    public String getDescription() {
        return "Get the current battery level and status of the Android device. "
                + "Returns charge percentage, charging status, health, temperature and voltage.";
    }

    @Override
    public JsonObject getParametersSchema() {
        // 无参数
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        try {
            // ACTION_BATTERY_CHANGED 是粘性广播，registerReceiver(null, ...) 可直接读取最新值
            Intent intent = context.registerReceiver(
                    null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

            if (intent == null) {
                return ToolResult.error("Unable to read battery information");
            }

            // 电量百分比
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int percent = (level >= 0 && scale > 0) ? (int) (level * 100f / scale) : -1;

            // 充电状态
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String statusStr = switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging";
                case BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging";
                case BatteryManager.BATTERY_STATUS_FULL        -> "Full";
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING-> "Not charging";
                default                                        -> "Unknown";
            };

            // 充电方式
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            String pluggedStr = switch (plugged) {
                case BatteryManager.BATTERY_PLUGGED_AC     -> "AC adapter";
                case BatteryManager.BATTERY_PLUGGED_USB    -> "USB";
                case BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless";
                case 0                                     -> "Unplugged";
                default                                    -> "Unknown";
            };

            // 电池健康状态
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            String healthStr = switch (health) {
                case BatteryManager.BATTERY_HEALTH_GOOD            -> "Good";
                case BatteryManager.BATTERY_HEALTH_OVERHEAT        -> "Overheat";
                case BatteryManager.BATTERY_HEALTH_DEAD            -> "Dead";
                case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE    -> "Over voltage";
                case BatteryManager.BATTERY_HEALTH_COLD            -> "Cold";
                default                                            -> "Unknown";
            };

            // 温度（单位：0.1°C）
            int tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            String tempStr = tempRaw >= 0
                    ? String.format("%.1f°C", tempRaw / 10f)
                    : "Unknown";

            // 电压（单位：mV）
            int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            String voltageStr = voltage > 0 ? voltage + " mV" : "Unknown";

            String result = String.format(
                    "Battery Information:\n"
                    + "• Level: %s%%\n"
                    + "• Status: %s\n"
                    + "• Power source: %s\n"
                    + "• Health: %s\n"
                    + "• Temperature: %s\n"
                    + "• Voltage: %s",
                    percent >= 0 ? percent : "Unknown",
                    statusStr, pluggedStr, healthStr, tempStr, voltageStr
            );

            return ToolResult.ok(result);

        } catch (Exception e) {
            return ToolResult.error("Failed to read battery info: " + e.getMessage());
        }
    }
}
