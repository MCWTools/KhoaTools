package khoa.tools.client.feature.customitemtab;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import khoa.tools.KhoaTools;
import khoa.tools.client.mixin.CreativeModeInventoryScreenAccessor;
import khoa.tools.client.mixin.CreativeModeTabAccessor;
import khoa.tools.client.mixin.KeyMappingKeyAccessor;

/**
 * User made creative inventory tabs. Each one is a real {@link CreativeModeTab} added to the creative tab registry at
 * runtime (the registry is unfrozen for the duration of the call), so it shows up on the next page of the creative
 * inventory immediately, without rejoining. Items are stored as JSON next to the other KhoaTools config.
 */
public final class CustomTabs {
	public static final String NAMESPACE = "khoatools";
	/** New names: one word, no spaces. */
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");
	/** Names already saved by an older version may also contain dots. */
	private static final Pattern NAME_LOAD = Pattern.compile("[A-Za-z0-9_.\\-]{1,32}");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Fabric API's creative tab paging: 10 tabs per page, 5 on the top row and 5 on the bottom row. */
	private static final int TABS_PER_PAGE = 10;

	/** Default key: Left Alt. Registered in the Controls screen under the KhoaTools category. */
	public static KeyMapping addKey;

	public static final class Entry {
		public String name;
		/** Registry path of the tab; fixed for the session (a rename only changes the title). */
		public final String slug;
		final List<String> items = new ArrayList<>();
		net.minecraft.world.item.Item iconItem;
		boolean alive = true;
		ResourceKey<CreativeModeTab> key;
		CreativeModeTab tab;
		volatile List<ItemStack> built = List.of();

		Entry(String name, String slug) {
			this.name = name;
			this.slug = slug;
		}

		public int size() {
			return items.size();
		}
	}

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static final Map<CreativeModeTab, Entry> BY_TAB = new IdentityHashMap<>();
	private static Entry editing;

	private CustomTabs() {
	}

	// ------------------------------------------------------------------ state queries

	public static boolean isEditing() {
		return editing != null;
	}

	public static String editingName() {
		return editing == null ? "" : editing.name;
	}

	public static List<String> names() {
		List<String> out = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.alive) {
				out.add(e.name);
			}
		}
		return out;
	}

	/** Used by the shouldDisplay mixin: our tabs are shown while they are alive, even when empty. Null = not our tab. */
	public static Boolean visibilityOf(CreativeModeTab tab) {
		Entry e = BY_TAB.get(tab);
		return e == null ? null : e.alive;
	}

	public static boolean addKeyDown() {
		Minecraft mc = Minecraft.getInstance();
		if (addKey == null || mc.getWindow() == null) {
			return false;
		}
		InputConstants.Key key = ((KeyMappingKeyAccessor) addKey).khoa$getKey();
		return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
	}

	public static String addKeyName() {
		return addKey == null ? "ALT" : addKey.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
	}

	// ------------------------------------------------------------------ commands

	/** Title shown on the tab: words split on _ or -, each capitalized (wool_stuff -> Wool Stuff, wools -> Wools). */
	public static String displayName(String name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("[_\\-]+")) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.length() == 0 ? name : sb.toString();
	}

	private static final String NAME_RULE = "Tab name: one word, no spaces, 1-32 characters from A-Z a-z 0-9 _ - (shown capitalized; _ and - become spaces)";

	private static boolean slugUsed(String slug) {
		for (Entry e : ENTRIES) {
			if (e.slug.equals(slug)) {
				return true;
			}
		}
		return false;
	}

	/** Returns an error text, or null on success. */
	public static String create(String name) {
		if (!NAME.matcher(name).matches()) {
			return NAME_RULE;
		}
		Entry existing = find(name);
		if (existing != null) {
			return "Custom tab '" + existing.name + "' already exists";
		}
		String slug = name.toLowerCase(Locale.ROOT);
		Entry dead = null;
		for (Entry e : ENTRIES) {
			if (e.slug.equals(slug) && !e.alive) {
				dead = e;
			}
		}
		Entry target;
		if (dead != null) { // deleted earlier in this session: bring the registered tab back, empty
			dead.name = name;
			dead.items.clear();
			dead.iconItem = null;
			dead.alive = true;
			((CreativeModeTabAccessor) dead.tab).khoa$setDisplayName(Component.literal(displayName(name)));
			((CreativeModeTabAccessor) dead.tab).khoa$setIconItemStack(null);
			target = dead;
		} else {
			String use = slug;
			for (int i = 2; slugUsed(use); i++) { // slug still owned by a renamed tab
				use = slug + "_" + i;
			}
			target = new Entry(name, use);
			ENTRIES.add(target);
			register(target);
		}
		layout();
		refresh(target);
		persist();
		editing = target;
		return null;
	}

	public static String rename(String oldName, String newName) {
		if (!NAME.matcher(newName).matches()) {
			return NAME_RULE;
		}
		Entry e = find(oldName);
		if (e == null) {
			return "No custom tab named '" + oldName + "'";
		}
		Entry other = find(newName);
		if (other != null && other != e) {
			return "Custom tab '" + other.name + "' already exists";
		}
		e.name = newName;
		((CreativeModeTabAccessor) e.tab).khoa$setDisplayName(Component.literal(displayName(newName)));
		persist();
		return null;
	}

	public static String icon(String name, Identifier itemId) {
		Entry e = find(name);
		if (e == null) {
			return "No custom tab named '" + name + "'";
		}
		net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
		if (item == null || item == Items.AIR) {
			return "Unknown item '" + itemId + "'";
		}
		e.iconItem = item;
		((CreativeModeTabAccessor) e.tab).khoa$setIconItemStack(null);
		persist();
		return null;
	}

	/** Rebuilds the item list of a tab right now (when in a world) so the creative screen never shows stale contents. */
	private static void refresh(Entry e) {
		if (e.tab != null && Minecraft.getInstance().level != null) {
			prepare(e.tab);
			redrawCreativeScreen();
		}
	}

	/** If the creative inventory is open on one of our tabs, reselect it so the item grid is reloaded right now. */
	private static void redrawCreativeScreen() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen instanceof CreativeModeInventoryScreen screen) {
			CreativeModeTab shown = CreativeModeInventoryScreenAccessor.khoa$getSelectedTab();
			if (BY_TAB.containsKey(shown)) {
				((CreativeModeInventoryScreenAccessor) screen).khoa$selectTab(shown);
			}
		}
	}

	/** Rebuilds every custom tab from its saved items and redraws the open creative screen. Returns the number of tabs rebuilt. */
	public static int forceRefresh() {
		int n = 0;
		for (Entry e : ENTRIES) {
			if (e.alive && e.tab != null) {
				prepare(e.tab);
				n++;
			}
		}
		redrawCreativeScreen();
		return n;
	}

	public static String edit(String name) {
		Entry e = find(name);
		if (e == null || !e.alive) {
			return "No custom tab named '" + name + "'. Use customitemtab create " + name;
		}
		editing = e;
		return null;
	}

	/** Ends edit mode. Items are written to disk as they are added, so this only stops the Alt click. */
	public static String save() {
		persist();
		if (editing == null) {
			return null;
		}
		String name = editing.name;
		editing = null;
		return name;
	}

	public static String delete(String name) {
		Entry e = find(name);
		if (e == null || !e.alive) {
			return "No custom tab named '" + name + "'";
		}
		if (editing == e) {
			editing = null;
		}
		e.alive = false;
		e.items.clear();
		e.built = List.of();
		if (CreativeModeInventoryScreenAccessor.khoa$getSelectedTab() == e.tab) {
			CreativeModeInventoryScreenAccessor.khoa$setSelectedTab(CreativeModeTabs.getDefaultTab());
		}
		layout();
		persist();
		return null;
	}

	/** One line per alive tab: name, item count, edit marker. */
	public static List<String> summary() {
		List<String> out = new ArrayList<>();
		for (Entry e : ENTRIES) {
			if (e.alive) {
				out.add(e.name + " (" + e.items.size() + " items)" + (e == editing ? " [editing]" : ""));
			}
		}
		return out;
	}

	/** Item names of one tab (read from the saved JSON), or null when the tab does not exist. */
	public static List<String> itemNames(String name) {
		Entry e = find(name);
		Minecraft mc = Minecraft.getInstance();
		if (e == null || !e.alive) {
			return null;
		}
		List<String> out = new ArrayList<>();
		if (mc.level == null) {
			return out;
		}
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
		for (String json : e.items) {
			ItemStack stack = ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
			out.add(stack.isEmpty() ? "(unreadable item)" : stack.getHoverName().getString());
		}
		return out;
	}

	/** Alive tab with this name (case-insensitive), or null. */
	private static Entry find(String name) {
		for (Entry e : ENTRIES) {
			if (e.alive && e.name.equalsIgnoreCase(name)) {
				return e;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ edit mode hooks

	public static void onCreativeScreenOpened() {
		rebuildAll();
	}

	/** Leaving the world / closing the game ends edit mode. Items are written to disk as they are added. */
	public static void onDisconnect() {
		save();
	}

	/** True for tabs made by this feature. */
	public static boolean isCustomTab(CreativeModeTab tab) {
		return tab != null && BY_TAB.containsKey(tab);
	}

	/** The tab being edited, or null. */
	public static CreativeModeTab editingTab() {
		return editing == null ? null : editing.tab;
	}

	/** Display title of one of our tabs, or "" for other tabs. */
	public static String customTabTitle(CreativeModeTab tab) {
		Entry e = tab == null ? null : BY_TAB.get(tab);
		return e == null ? "" : displayName(e.name);
	}

	/** Alt + click on an item while one of our tabs is shown: take that item out of the tab. */
	public static void removeItem(CreativeModeTab tab, ItemStack clicked) {
		Minecraft mc = Minecraft.getInstance();
		Entry e = tab == null ? null : BY_TAB.get(tab);
		if (e == null || mc.level == null || clicked.isEmpty()) {
			return;
		}
		ItemStack one = clicked.copyWithCount(1);
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
		JsonElement json = ItemStack.CODEC.encodeStart(ops, one).result().orElse(null);
		boolean removed = json != null && e.items.remove(json.toString());
		if (removed) {
			persist();
			refresh(e);
		}
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[CustomItemTab] " + (removed ? "Removed " + one.getHoverName().getString() + " from '" + e.name + "' ("
				+ e.items.size() + " items left)." : one.getHoverName().getString() + " is not in '" + e.name + "'.")));
		}
	}

	/** Alt + click on an item in the creative inventory. */
	public static void addItem(ItemStack clicked) {
		Minecraft mc = Minecraft.getInstance();
		Entry e = editing;
		if (e == null || mc.level == null || clicked.isEmpty()) {
			return;
		}
		ItemStack one = clicked.copyWithCount(1);
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
		JsonElement json = ItemStack.CODEC.encodeStart(ops, one).result().orElse(null);
		if (json == null) {
			return;
		}
		String text = json.toString();
		if (e.items.contains(text)) {
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("[CustomItemTab] " + one.getHoverName().getString() + " is already in '" + e.name + "'."));
			}
			return;
		}
		e.items.add(text);
		persist();
		refresh(e);
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[CustomItemTab] Added " + one.getHoverName().getString() + " to '" + e.name + "' (" + e.items.size() + " items)."));
		}
	}

	// ------------------------------------------------------------------ tabs

	private static void register(Entry e) {
		ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(NAMESPACE, "customtab/" + e.slug));
		CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
			.title(Component.literal(displayName(e.name)))
			.icon(() -> e.iconItem != null ? new ItemStack(e.iconItem) : e.built.isEmpty() ? new ItemStack(Items.BOOK) : e.built.get(0))
			.displayItems((params, output) -> {
				List<ItemStack> built = new ArrayList<>();
				RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, params.holders());
				for (String json : e.items) {
					try {
						ItemStack stack = ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
						if (!stack.isEmpty()) {
							built.add(stack);
						}
					} catch (RuntimeException ex) {
						KhoaTools.LOGGER.warn("Custom tab {}: skipped an item that could not be read", e.name, ex);
					}
				}
				sortLikeVanilla(built);
				for (ItemStack stack : built) {
					output.accept(stack);
				}
				e.built = built;
			})
			.build();
		registerUnfrozen(key, tab);
		e.key = key;
		e.tab = tab;
		BY_TAB.put(tab, e);
	}

	/** The creative tab registry is frozen after startup; open it just long enough to add one entry. */
	private static void registerUnfrozen(ResourceKey<CreativeModeTab> key, CreativeModeTab tab) {
		Registry<CreativeModeTab> registry = BuiltInRegistries.CREATIVE_MODE_TAB;
		try {
			Field frozen = MappedRegistry.class.getDeclaredField("frozen");
			frozen.setAccessible(true);
			boolean was = frozen.getBoolean(registry);
			frozen.setBoolean(registry, false);
			try {
				Registry.register(registry, key, tab);
			} finally {
				frozen.setBoolean(registry, was);
			}
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Could not add a creative tab at runtime", ex);
		}
	}

	/** Gives every alive custom tab its place after the tabs of other mods: page, row and column like Fabric's pagination does. */
	private static void layout() {
		int others = 0;
		for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
			Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(t);
			if (id != null && !id.getNamespace().equals("minecraft") && !id.getNamespace().equals(NAMESPACE)) {
				others++;
			}
		}
		int index = others;
		for (Entry e : ENTRIES) {
			if (!e.alive || e.tab == null) {
				continue;
			}
			int pos = index % TABS_PER_PAGE;
			CreativeModeTab.Row row = pos < 5 ? CreativeModeTab.Row.TOP : CreativeModeTab.Row.BOTTOM;
			setPage(e.tab, index / TABS_PER_PAGE + 1);
			CreativeModeTabAccessor acc = (CreativeModeTabAccessor) e.tab;
			acc.khoa$setRow(row);
			acc.khoa$setColumn(pos < 5 ? pos : pos - 5);
			index++;
		}
	}

	/**
	 * Orders items like the normal creative inventory: by the position of their item in the vanilla tabs (tab order, then order
	 * inside the tab), so e.g. all wool colors stay together in the usual order. Items that appear in no tab go last. Stable, so
	 * several stacks of one item keep the order they were added in.
	 */
	private static void sortLikeVanilla(List<ItemStack> stacks) {
		Map<net.minecraft.world.item.Item, Integer> rank = new IdentityHashMap<>();
		int n = 0;
		for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
			if (BY_TAB.containsKey(t)) {
				continue;
			}
			for (ItemStack s : t.getDisplayItems()) {
				if (rank.putIfAbsent(s.getItem(), n) == null) {
					n++;
				}
			}
		}
		if (rank.isEmpty()) {
			return;
		}
		stacks.sort(java.util.Comparator.comparingInt(s -> rank.getOrDefault(s.getItem(), Integer.MAX_VALUE)));
	}

	/** Fabric API keeps the creative page of a tab in an interface it injects into CreativeModeTab (not exposed at compile time). */
	private static void setPage(CreativeModeTab tab, int page) {
		try {
			Class.forName("net.fabricmc.fabric.impl.creativetab.FabricCreativeModeTabImpl").getMethod("fabric_setPage", int.class).invoke(tab, page);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			KhoaTools.LOGGER.warn("Could not set the creative page of tab {}; it may appear on page 1", tab.getDisplayName().getString(), ex);
		}
	}

	/** Called right before the creative screen shows one of our tabs: rebuild its item list from the saved items. */
	public static void prepare(CreativeModeTab tab) {
		Entry e = BY_TAB.get(tab);
		Minecraft mc = Minecraft.getInstance();
		if (e == null || mc.player == null || mc.level == null) {
			return;
		}
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess()));
		((CreativeModeTabAccessor) tab).khoa$setIconItemStack(null);
	}

	/** Refreshes the icons of all tabs after the item lists were loaded or changed. */
	public static void rebuildAll() {
		for (Entry e : ENTRIES) {
			if (e.alive && e.tab != null) {
				prepare(e.tab);
			}
		}
	}

	// ------------------------------------------------------------------ persistence

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("khoatools-customtabs.json");
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			for (JsonElement el : root.getAsJsonArray("tabs")) {
				JsonObject o = el.getAsJsonObject();
				String name = o.get("name").getAsString();
				if (!NAME_LOAD.matcher(name).matches() || find(name) != null) {
					continue;
				}
				String slug = name.toLowerCase(Locale.ROOT);
				for (int i = 2; slugUsed(slug); i++) {
					slug = name.toLowerCase(Locale.ROOT) + "_" + i;
				}
				Entry e = new Entry(name, slug);
				if (o.has("icon")) {
					Identifier iconId = Identifier.tryParse(o.get("icon").getAsString());
					if (iconId != null) {
						e.iconItem = BuiltInRegistries.ITEM.getOptional(iconId).orElse(null);
					}
				}
				for (JsonElement item : o.getAsJsonArray("items")) {
					e.items.add(item.toString());
				}
				ENTRIES.add(e);
				register(e);
			}
			layout();
		} catch (IOException | RuntimeException ex) {
			KhoaTools.LOGGER.error("Could not read {}", f, ex);
		}
	}

	private static void persist() {
		JsonArray tabs = new JsonArray();
		for (Entry e : ENTRIES) {
			if (!e.alive) {
				continue;
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name);
			if (e.iconItem != null) {
				o.addProperty("icon", BuiltInRegistries.ITEM.getKey(e.iconItem).toString());
			}
			JsonArray items = new JsonArray();
			for (String item : e.items) {
				items.add(JsonParser.parseString(item));
			}
			o.add("items", items);
			tabs.add(o);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("tabs", tabs);
		Path f = file();
		Path tmp = f.resolveSibling("khoatools-customtabs.json.tmp");
		try {
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, w);
			}
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			KhoaTools.LOGGER.error("Could not save {}", f, ex);
		}
	}
}
