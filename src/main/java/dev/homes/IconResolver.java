package dev.homes;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Turns a Material into an inline sprite (Minecraft 1.21.9+ "object" text component),
 * so no resource pack is needed. Blocks use their block texture, other items their item texture.
 * Banners fall back to the wool of the same colour; derived blocks (stairs, slabs, ...) use their base block.
 */
public final class IconResolver {
    public record Entry(Material material, String name, String lower) { }

    private static final Key ITEMS = Key.key("minecraft", "items");
    private static final Key BLOCKS = Key.key("minecraft", "blocks");

    private static final Map<String, String> BUILTIN = new HashMap<>();
    private static final String[] SUFFIXES = {
            "stairs", "slab", "wall", "fence_gate", "fence", "button", "pressure_plate", "carpet", "pane"
    };
    private static final Set<String> ITEM_TEXTURE_BLOCKS = Set.of(
            "brewing_stand", "cauldron", "hopper", "flower_pot", "campfire", "soul_campfire",
            "lantern", "soul_lantern", "bell", "repeater", "comparator", "cake", "barrier",
            "light", "structure_void", "sugar_cane", "kelp", "bamboo", "sea_pickle",
            "nether_wart", "pointed_dripstone", "wheat");

    private static void b(String key, String tex) { BUILTIN.put(key, "blocks:block/" + tex); }
    private static void i(String key, String tex) { BUILTIN.put(key, "items:item/" + tex); }

    static {
        b("grass_block", "grass_block_side"); b("mycelium", "mycelium_side"); b("podzol", "podzol_side");
        b("dirt_path", "dirt_path_side"); b("crafting_table", "crafting_table_front");
        b("furnace", "furnace_front"); b("blast_furnace", "blast_furnace_front"); b("smoker", "smoker_front");
        b("dispenser", "dispenser_front"); b("dropper", "dropper_front"); b("observer", "observer_front");
        b("piston", "piston_side"); b("sticky_piston", "piston_side"); b("tnt", "tnt_side");
        b("pumpkin", "pumpkin_side"); b("melon", "melon_side"); b("hay_block", "hay_block_side");
        b("bone_block", "bone_block_side"); b("quartz_block", "quartz_block_side");
        b("smooth_quartz", "quartz_block_bottom"); b("smooth_sandstone", "sandstone_top");
        b("smooth_red_sandstone", "red_sandstone_top"); b("basalt", "basalt_side");
        b("polished_basalt", "polished_basalt_side"); b("cactus", "cactus_side"); b("snow", "snow");
        b("enchanting_table", "enchanting_table_side"); b("jukebox", "jukebox_side");
        b("barrel", "barrel_side"); b("loom", "loom_front"); b("composter", "composter_side");
        b("target", "target_side"); b("lodestone", "lodestone_side"); b("respawn_anchor", "respawn_anchor_side0");
        b("command_block", "command_block_front"); b("chain_command_block", "chain_command_block_front");
        b("repeating_command_block", "repeating_command_block_front"); b("jigsaw", "jigsaw_side");
        b("tall_grass", "tall_grass_top"); b("large_fern", "large_fern_top"); b("sunflower", "sunflower_front");
        b("lilac", "lilac_top"); b("rose_bush", "rose_bush_top"); b("peony", "peony_top");
        b("pitcher_plant", "pitcher_plant_top"); b("scaffolding", "scaffolding_top");
        b("big_dripleaf", "big_dripleaf_top"); b("reinforced_deepslate", "reinforced_deepslate_side");
        b("suspicious_sand", "suspicious_sand_0"); b("suspicious_gravel", "suspicious_gravel_0");
        b("trial_spawner", "trial_spawner_side_inactive"); b("vault", "vault_front_off");
        b("crafter", "crafter_north"); b("cartography_table", "cartography_table_side1");
        b("fletching_table", "fletching_table_front"); b("smithing_table", "smithing_table_front");
        b("lectern", "lectern_front"); b("bee_nest", "bee_nest_side"); b("beehive", "beehive_side");
        b("sculk_catalyst", "sculk_catalyst_side"); b("sculk_shrieker", "sculk_shrieker_side");
        b("sculk_sensor", "sculk_sensor_side"); b("calibrated_sculk_sensor", "calibrated_sculk_sensor_top");
        b("glass_pane", "glass"); b("heavy_weighted_pressure_plate", "iron_block");
        b("light_weighted_pressure_plate", "gold_block"); b("petrified_oak_slab", "oak_planks");
        b("chest", "oak_planks"); b("trapped_chest", "oak_planks"); b("ender_chest", "obsidian");
        b("shulker_box", "purple_concrete"); b("shield", "oak_planks"); b("conduit", "prismarine_bricks");
        b("decorated_pot", "terracotta"); b("skeleton_skull", "bone_block_side");
        b("wither_skeleton_skull", "coal_block"); b("zombie_head", "moss_block");
        b("creeper_head", "green_concrete_powder"); b("dragon_head", "purpur_block");
        b("piglin_head", "pink_terracotta"); b("player_head", "brown_terracotta");
        b("ancient_debris", "ancient_debris_side"); b("azalea", "azalea_side");
        b("flowering_azalea", "flowering_azalea_side"); b("snow_block", "snow"); b("magma_block", "magma");
        b("end_portal_frame", "end_portal_frame_side"); b("stonecutter", "stonecutter_side");
        b("grindstone", "grindstone_side"); b("honey_block", "honey_block_side");
        b("dried_kelp_block", "dried_kelp_side"); b("daylight_detector", "daylight_detector_top");
        b("mangrove_roots", "mangrove_roots_side"); b("muddy_mangrove_roots", "muddy_mangrove_roots_side");
        b("sniffer_egg", "sniffer_egg_not_cracked");
        b("anvil", "anvil"); b("chipped_anvil", "chipped_anvil_top"); b("damaged_anvil", "damaged_anvil_top");
        b("chiseled_bookshelf", "chiseled_bookshelf_side"); b("dried_ghast", "light_gray_concrete");
        b("infested_deepslate", "deepslate");
        i("light", "light_15"); i("enchanted_golden_apple", "golden_apple");
        b("ochre_froglight", "ochre_froglight_side"); b("verdant_froglight", "verdant_froglight_side");
        b("pearlescent_froglight", "pearlescent_froglight_side");
        b("pitcher_plant", "pitcher_crop_top_stage_4");
        b("sniffer_egg", "sniffer_egg_not_cracked_top");
        b("purpur_pillar", "purpur_pillar"); b("quartz_pillar", "quartz_pillar");
        i("compass", "compass_00"); i("clock", "clock_00"); i("recovery_compass", "recovery_compass_00");
        i("crossbow", "crossbow_standby"); i("debug_stick", "stick");
    }

    private static final Set<String> BED_COLORS = Set.of("white", "orange", "magenta", "light_blue", "yellow",
            "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private final Map<String, String> custom = new HashMap<>();
    private volatile Map<String, String> resolved = Map.of();
    private volatile boolean packBeds = true;

    /** true = beds use the sprites from the HomesIcons resource pack; false = vanilla bed sprite. */
    public void setPackBeds(boolean v) { this.packBeds = v; }

    private String bedSpec(String color) {
        return packBeds ? "blocks:homes_bed/" + color : "beds:entity/bed/" + color;
    }
    private final List<Entry> entries = new ArrayList<>();

    public IconResolver() {
        for (Material m : Material.values()) {
            if (m.isLegacy() || m.isAir() || !m.isItem()) continue;
            String name = prettyName(m);
            entries.add(new Entry(m, name, name.toLowerCase(Locale.ROOT)));
        }
        entries.sort(Comparator.comparing(Entry::lower));
    }

    /** Item keys whose icons should be looked up. */
    public List<String> keys() {
        List<String> k = new ArrayList<>();
        for (Entry e : entries) k.add(e.material().getKey().getKey());
        return k;
    }

    public void setResolved(Map<String, String> map) {
        this.resolved = Map.copyOf(map);
    }

    public void loadOverrides(ConfigurationSection section) {
        custom.clear();
        if (section == null) return;
        for (String k : section.getKeys(false)) {
            String v = section.getString(k);
            if (v != null) custom.put(k.toLowerCase(Locale.ROOT), v);
        }
    }

    public static String prettyName(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.getKey().getKey().split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public List<Entry> search(String query) {
        if (query == null || query.isBlank()) return entries;
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Entry> out = new ArrayList<>();
        outer:
        for (Entry e : entries) {
            for (String t : tokens) if (!e.lower().contains(t)) continue outer;
            out.add(e);
        }
        return out;
    }

    /** Inline icon component for a material, or the default bed when material is null. */
    public Component icon(Material m) {
        String spec = m == null ? bedSpec("white") : spec(m);
        Key atlas = spec.startsWith("items:") ? ITEMS
                : spec.startsWith("beds:") ? Key.key("minecraft", "beds") : BLOCKS;
        Key sprite = Key.key(spec.substring(spec.indexOf(':') + 1));
        return Component.object(ObjectContents.sprite(atlas, sprite));
    }

    private String spec(Material m) {
        String key = m.getKey().getKey();
        String c = custom.get(key);
        if (c != null) return c;
        if (key.endsWith("_bed") && BED_COLORS.contains(key.substring(0, key.length() - 4))) {
            return bedSpec(key.substring(0, key.length() - 4));
        }
        if (key.endsWith("_banner") && !key.contains("pattern")) {
            return "blocks:block/" + key.substring(0, key.length() - "_banner".length()) + "_wool";
        }
        String r = resolved.get(key);
        if (r != null) return r;
        String o = BUILTIN.get(key);
        if (o != null) return o;
        if (key.startsWith("infested_")) {
            Material base = Material.matchMaterial(key.substring("infested_".length()));
            if (base != null) return spec(base);
        }
        if (key.contains("copper_chest") || key.contains("copper_golem_statue")) {
            String k = key.startsWith("waxed_") ? key.substring(6) : key;
            for (String ox : new String[]{"exposed_", "weathered_", "oxidized_"}) {
                if (k.startsWith(ox)) return "blocks:block/" + ox + "copper";
            }
            return "blocks:block/copper_block";
        }
        if (key.endsWith("_chain") || key.endsWith("lantern")) {
            return "items:item/" + key;
        }
        if (key.endsWith("_banner")) {
            return "blocks:block/" + key.substring(0, key.length() - "_banner".length()) + "_wool";
        }
        if (key.endsWith("_shulker_box")) {
            return "blocks:block/" + key.substring(0, key.length() - "_shulker_box".length()) + "_concrete";
        }
        if (!m.isBlock()) return "items:item/" + key;
        if (key.endsWith("_bed")) {
            // bed sprite from the blocks atlas (the flat unfolded bed texture)
            return "blocks:entity/bed/" + key.substring(0, key.length() - "_bed".length());
        }
        if (key.endsWith("_door") || key.endsWith("_sign")
                || ITEM_TEXTURE_BLOCKS.contains(key)) {
            return "items:item/" + key;
        }
        String base = deriveBase(key);
        String o2 = BUILTIN.get(base);
        return o2 != null ? o2 : "blocks:block/" + base;
    }

    private static String deriveBase(String key) {
        String k = key.startsWith("waxed_") ? key.substring(6) : key;
        if (k.endsWith("_wood")) return k.substring(0, k.length() - 5) + "_log";
        if (k.endsWith("_hyphae")) return k.substring(0, k.length() - 7) + "_stem";
        for (String suf : SUFFIXES) {
            String tail = "_" + suf;
            if (k.endsWith(tail)) {
                return resolveStem(k.substring(0, k.length() - tail.length()));
            }
        }
        return k;
    }

    private static String resolveStem(String stem) {
        String[] candidates = {stem + "_planks", stem, stem + "s", stem + "_block", stem + "_wool"};
        for (String cand : candidates) {
            Material mat = Material.matchMaterial(cand);
            if (mat != null && mat.isBlock()) return cand;
        }
        return stem;
    }
}
