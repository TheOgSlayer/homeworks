package dev.homes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Downloads Mojang's client jar for the running Minecraft version (once), reads the real item
 * definitions / models / texture list, and works out which texture each item uses, so icons
 * are correct for every version instead of being guessed.
 */
public final class AssetIndexer {
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String[] PREFERRED = {
            "layer0", "all", "side", "front", "end", "top", "texture", "north", "particle",
            "up", "south", "east", "west", "bottom", "down"};

    private AssetIndexer() { }

    private static String get(HttpClient c, String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build();
        return c.send(r, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** @return map material key -> "atlas:sprite", or null when it could not be built. */
    public static Map<String, String> build(File dataFolder, String mcVersion, Collection<String> keys) throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build();
        JsonObject manifest = JsonParser.parseString(get(http, MANIFEST)).getAsJsonObject();
        String versionUrl = null;
        for (JsonElement e : manifest.getAsJsonArray("versions")) {
            JsonObject v = e.getAsJsonObject();
            if (v.get("id").getAsString().equals(mcVersion)) { versionUrl = v.get("url").getAsString(); break; }
        }
        if (versionUrl == null) return null;
        JsonObject vj = JsonParser.parseString(get(http, versionUrl)).getAsJsonObject();
        String clientUrl = vj.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString();

        File jar = new File(dataFolder, "client-download.tmp");
        HttpRequest req = HttpRequest.newBuilder(URI.create(clientUrl)).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = resp.body()) {
            Files.copy(in, jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipFile zf = new ZipFile(jar)) {
            return index(zf, keys);
        } finally {
            jar.delete();
        }
    }

    private static Map<String, String> index(ZipFile zf, Collection<String> keys) {
        Set<String> textures = new HashSet<>();
        String tp = "assets/minecraft/textures/";
        for (Enumeration<? extends ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
            String n = en.nextElement().getName();
            if (n.startsWith(tp) && n.endsWith(".png")) {
                String t = n.substring(tp.length(), n.length() - 4);
                if (t.startsWith("block/") || t.startsWith("item/")) textures.add(t);
            }
        }
        boolean itemsAtlas = zf.getEntry("assets/minecraft/atlases/items.json") != null;
        Map<String, String> out = new HashMap<>();
        for (String key : keys) {
            try {
                String tex = textureFor(zf, key, textures);
                if (tex == null) continue;
                String atlas = tex.startsWith("item/") && itemsAtlas ? "items" : "blocks";
                out.put(key, atlas + ":" + tex);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static JsonObject readJson(ZipFile zf, String path) {
        ZipEntry e = zf.getEntry(path);
        if (e == null) return null;
        try (InputStream in = zf.getInputStream(e)) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String strip(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String findModel(JsonElement e) {
        if (e == null) return null;
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String k : new String[]{"model", "fallback", "on_false", "base"}) {
                JsonElement c = o.get(k);
                if (c == null) continue;
                if (c.isJsonPrimitive()) {
                    if (k.equals("model") || k.equals("base")) return c.getAsString();
                } else {
                    String r = findModel(c);
                    if (r != null) return r;
                }
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                String r = findModel(en.getValue());
                if (r != null) return r;
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (JsonElement x : a) {
                String r = findModel(x);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static String textureFor(ZipFile zf, String key, Set<String> textures) {
        String model = null;
        JsonObject def = readJson(zf, "assets/minecraft/items/" + key + ".json");
        if (def != null) model = findModel(def);
        if (model == null) model = "minecraft:item/" + key;

        Map<String, String> tex = new LinkedHashMap<>();
        String cur = strip(model);
        for (int depth = 0; cur != null && depth < 12; depth++) {
            JsonObject m = readJson(zf, "assets/minecraft/models/" + cur + ".json");
            if (m == null) break;
            if (m.has("textures") && m.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> en : m.getAsJsonObject("textures").entrySet()) {
                    if (en.getValue().isJsonPrimitive()) tex.putIfAbsent(en.getKey(), en.getValue().getAsString());
                }
            }
            cur = m.has("parent") ? strip(m.get("parent").getAsString()) : null;
        }
        for (String p : PREFERRED) {
            String r = resolve(tex, p, textures);
            if (r != null) return r;
        }
        for (String k : tex.keySet()) {
            String r = resolve(tex, k, textures);
            if (r != null) return r;
        }
        return null;
    }

    private static String resolve(Map<String, String> tex, String name, Set<String> textures) {
        String v = tex.get(name);
        for (int i = 0; v != null && v.startsWith("#") && i < 10; i++) v = tex.get(v.substring(1));
        if (v == null || v.startsWith("#")) return null;
        v = strip(v);
        return textures.contains(v) ? v : null;
    }
}
