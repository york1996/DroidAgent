package com.york1996.ai.droidagent.tool;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/**
 * 当前位置查询工具
 * 通过 LocationManager 获取最近一次定位，再用 Geocoder 反向地理编码得到城市/区域名称。
 * 需要 ACCESS_FINE_LOCATION 或 ACCESS_COARSE_LOCATION 运行时权限。
 */
public class LocationTool implements Tool {

    @Override
    public String getName() { return "current_location"; }

    @Override
    public String getDescription() {
        return "Get the current geographic location of the Android device. "
                + "Returns latitude, longitude, city name, district and address. "
                + "Use this tool when the user asks about local weather, nearby places, "
                + "or anything that requires knowing the current location.";
    }

    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public ToolResult execute(JsonObject params, Context context) {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            return ToolResult.error(
                    "Location permission not granted. Please enable location permission in system settings.");
        }

        try {
            LocationManager lm = (LocationManager)
                    context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return ToolResult.error("Location service unavailable on this device");
            }

            Location best = pickBestLastLocation(lm);
            if (best == null) {
                return ToolResult.error(
                        "Unable to determine current location. "
                        + "Make sure location services (GPS / Wi-Fi) are enabled on the device.");
            }

            double lat = best.getLatitude();
            double lng = best.getLongitude();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US,
                    "Current Location:\n• Latitude: %.6f\n• Longitude: %.6f", lat, lng));

            appendGeocoderInfo(context, lat, lng, sb);

            return ToolResult.ok(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to get location: " + e.getMessage());
        }
    }

    @SuppressWarnings("MissingPermission")
    private Location pickBestLastLocation(LocationManager lm) {
        Location best = null;
        for (String provider : new String[]{
                LocationManager.FUSED_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return best;
    }

    private void appendGeocoderInfo(Context context, double lat, double lng, StringBuilder sb) {
        if (!Geocoder.isPresent()) return;
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses == null || addresses.isEmpty()) return;

            Address addr = addresses.get(0);
            String country  = addr.getCountryName();
            String admin    = addr.getAdminArea();
            String city     = addr.getLocality();
            String district = addr.getSubLocality();
            String street   = addr.getThoroughfare();

            if (country != null)  sb.append("\n• Country: ").append(country);
            if (admin != null)    sb.append("\n• Province/State: ").append(admin);
            if (city != null)     sb.append("\n• City: ").append(city);
            if (district != null) sb.append("\n• District: ").append(district);
            if (street != null)   sb.append("\n• Street: ").append(street);
        } catch (Exception ignored) {
        }
    }
}
