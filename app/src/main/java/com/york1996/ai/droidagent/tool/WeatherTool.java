package com.york1996.ai.droidagent.tool;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 天气查询工具 —— 使用 wttr.in 免费接口（无需 API Key）
 */
public class WeatherTool implements Tool {

    private static final String TAG = "WeatherTool";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() { return "weather"; }

    @Override
    public String getDescription() {
        return "Get current weather and forecast for a city. "
                + "Returns temperature, humidity, wind speed and weather description. "
                + "IMPORTANT: If the user does not explicitly mention a city name, "
                + "you MUST call the 'current_location' tool first to get the current city, "
                + "then use that city name here. Never guess or reuse a city from past conversations.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject props = new JsonObject();
        JsonObject city = new JsonObject();
        city.addProperty("type", "string");
        city.addProperty("description", "City name only, no suffix like 市/区/省. e.g. \"Beijing\", \"珠海\", \"London\"");
        props.add("city", city);
        schema.add("properties", props);

        JsonArray required = new JsonArray();
        required.add("city");
        schema.add("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        if (!params.has("city")) {
            return ToolResult.error("Missing parameter: city");
        }
        String city = params.get("city").getAsString();
        try {
            String url = "https://wttr.in/" + Uri.encode(city) + "?format=j1";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "DroidAgent/1.0")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return ToolResult.error("Weather service unavailable (HTTP " + response.code() + ")");
                }
                String body = response.body().string();
                return parseWeather(city, body);
            }
        } catch (Exception e) {
            Log.e(TAG, "Weather query failed", e);
            return ToolResult.error("Weather query failed: " + e.getMessage());
        }
    }

    private ToolResult parseWeather(String city, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject current = root.getAsJsonArray("current_condition").get(0).getAsJsonObject();

            String tempC = current.get("temp_C").getAsString();
            String feelsLikeC = current.get("FeelsLikeC").getAsString();
            String humidity = current.get("humidity").getAsString();
            String windKmph = current.get("windspeedKmph").getAsString();
            String windDir = current.get("winddir16Point").getAsString();
            String description = current.getAsJsonArray("weatherDesc")
                    .get(0).getAsJsonObject().get("value").getAsString();
            String visibility = current.get("visibility").getAsString();
            String uvIndex = current.get("uvIndex").getAsString();

            // 3天预报
            StringBuilder forecast = new StringBuilder();
            JsonArray weather = root.getAsJsonArray("weather");
            String[] dayLabels = {"Today", "Tomorrow", "Day 3"};
            for (int i = 0; i < Math.min(weather.size(), 3); i++) {
                JsonObject day = weather.get(i).getAsJsonObject();
                String date = day.get("date").getAsString();
                String maxC = day.get("maxtempC").getAsString();
                String minC = day.get("mintempC").getAsString();
                String dayDesc = day.getAsJsonArray("hourly").get(4)
                        .getAsJsonObject().getAsJsonArray("weatherDesc")
                        .get(0).getAsJsonObject().get("value").getAsString();
                forecast.append(String.format("\n  %s (%s): %s~%s°C, %s",
                        dayLabels[i], date, minC, maxC, dayDesc));
            }

            String result = String.format(
                    "Weather in %s:\n"
                    + "• Condition: %s\n"
                    + "• Temperature: %s°C (feels like %s°C)\n"
                    + "• Humidity: %s%%\n"
                    + "• Wind: %s km/h %s\n"
                    + "• Visibility: %s km\n"
                    + "• UV Index: %s\n"
                    + "\n3-Day Forecast:%s",
                    city, description, tempC, feelsLikeC,
                    humidity, windKmph, windDir, visibility, uvIndex,
                    forecast.toString()
            );

            return ToolResult.ok(result);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse weather data: " + e.getMessage());
        }
    }
}
