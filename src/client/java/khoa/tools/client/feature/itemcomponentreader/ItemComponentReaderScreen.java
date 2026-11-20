package khoa.tools.client.feature.itemcomponentreader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

/** JSON component reader/editor. Tree on the left (shows values, click a leaf to edit it), raw JSON on the right. */
public final class ItemComponentReaderScreen extends Screen {
	private static final String EMPTY = "{\n  \"item\": \"minecraft:air\"\n}";
	private static final int TREE_TOP = 58;

	private final Minecraft mc;
	private ItemStack stack;
	private int slotIndex; // player inventory index, -1 = not from player inventory
	private MultiLineEditBox editor;
	private EditBox valueBox;
	private JsonElement root = new JsonObject();
	private final List<TreeNode> nodes = new ArrayList<>();
	private final List<Button> rows = new ArrayList<>();
	private final Set<String> expanded = new HashSet<>(Set.of("", "components"));
	private List<String> selected = List.of();
	private int scroll;
	private int editorX;
	private String status = "";
	private boolean statusOk = true;

	public ItemComponentReaderScreen(Minecraft mc, ItemStack fallback) {
		super(Component.literal("Item Component Reader"));
		this.mc = mc;
		ItemStack hovered = ItemComponentReaderState.lastStack();
		int hoveredSlot = ItemComponentReaderState.lastInventorySlot();
		ItemComponentReaderState.clear(); // a hover is used once; next time the held item is read
		if (hovered.isEmpty()) {
			this.stack = fallback.copy();
			this.slotIndex = mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot();
		} else {
			this.stack = hovered;
			this.slotIndex = hoveredSlot;
		}
	}

	@Override protected void init() {
		String keep = editor != null ? editor.getValue() : json();
		clearWidgets();
		rows.clear();
		editorX = Math.max(250, width / 2 + 5);
		int leftW = editorX - 18;
		editor = addRenderableWidget(MultiLineEditBox.builder().setX(editorX).setY(45).build(font, Math.max(220, width - editorX - 10), Math.max(120, height - 85), Component.literal("JSON")));
		editor.setValue(keep);
		valueBox = addRenderableWidget(new EditBox(font, 10, height - 58, leftW - 50, 20, Component.literal("Value")));
		valueBox.setMaxLength(32767);
		addRenderableWidget(Button.builder(Component.literal("Set"), b -> applyValue()).bounds(leftW - 36, height - 58, 44, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Add"), b -> addProperty()).bounds(10, height - 32, 55, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Remove"), b -> removeProperty()).bounds(70, height - 32, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width - 150, height - 32, 60, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(width - 82, height - 32, 70, 20).build());
		editor.setValueListener(s -> refreshTree());
		refreshTree();
	}

	// ---------------------------------------------------------------- tree

	private record TreeNode(String key, List<String> path, int depth, boolean container, JsonElement value) {
		String id() { return String.join("\u0001", path); }
	}

	private static String id(List<String> path) { return String.join("\u0001", path); }

	private void refreshTree() {
		if (valueBox == null) return;
		try {
			root = JsonParser.parseString(editor.getValue());
		} catch (RuntimeException e) {
			setStatus("JSON is invalid, the tree shows the last valid version", false);
		}
		nodes.clear();
		flatten(root, "root", List.of(), 0);
		rebuildRows();
	}

	private void flatten(JsonElement value, String key, List<String> path, int depth) {
		boolean container = value.isJsonObject() || value.isJsonArray();
		nodes.add(new TreeNode(key, path, depth, container, value));
		if (!container || !expanded.contains(id(path))) return;
		if (value.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet()) flatten(e.getValue(), e.getKey(), child(path, e.getKey()), depth + 1);
		} else {
			JsonArray arr = value.getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) flatten(arr.get(i), "[" + i + "]", child(path, String.valueOf(i)), depth + 1);
		}
	}

	private static List<String> child(List<String> path, String token) {
		List<String> p = new ArrayList<>(path);
		p.add(token);
		return p;
	}

	private int visibleRows() { return Math.max(1, (height - 66 - TREE_TOP) / 20); }

	private void rebuildRows() {
		for (Button b : rows) removeWidget(b);
		rows.clear();
		int visible = visibleRows();
		scroll = Math.max(0, Math.min(scroll, nodes.size() - visible));
		int y = TREE_TOP;
		for (int i = scroll; i < nodes.size() && i < scroll + visible; i++) {
			TreeNode node = nodes.get(i);
			int x = 8 + Math.min(node.depth(), 8) * 10;
			boolean sel = node.path().equals(selected);
			String marker = node.container() ? (expanded.contains(node.id()) ? "- " : "+ ") : "  ";
			String label = node.container()
				? node.key() + (node.value().isJsonObject() ? " {" + node.value().getAsJsonObject().size() + "}" : " [" + node.value().getAsJsonArray().size() + "]")
				: node.key() + ": " + node.value();
			Button row = Button.builder(Component.literal((sel ? "> " : "") + marker + label), b -> onRowClick(node))
				.bounds(x, y, Math.max(100, editorX - 18 - x), 18).build();
			rows.add(addRenderableWidget(row));
			y += 20;
		}
	}

	private void onRowClick(TreeNode node) {
		selected = node.path();
		if (node.container()) {
			if (!expanded.add(node.id())) expanded.remove(node.id());
		}
		String text = node.value().isJsonPrimitive() && node.value().getAsJsonPrimitive().isString() ? node.value().getAsString() : node.value().toString();
		valueBox.setValue(text);
		nodes.clear();
		flatten(root, "root", List.of(), 0);
		rebuildRows();
	}

	@Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (x < editorX - 8) {
			scroll += dy > 0 ? -1 : 1;
			rebuildRows();
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	// ---------------------------------------------------------------- editing

	private static JsonElement find(JsonElement root, List<String> path) {
		JsonElement cur = root;
		for (String token : path) {
			if (cur == null) return null;
			if (cur.isJsonObject()) cur = cur.getAsJsonObject().get(token);
			else if (cur.isJsonArray()) {
				try { int i = Integer.parseInt(token); cur = i >= 0 && i < cur.getAsJsonArray().size() ? cur.getAsJsonArray().get(i) : null; }
				catch (NumberFormatException e) { return null; }
			} else return null;
		}
		return cur;
	}

	private JsonElement currentModel() {
		try { return JsonParser.parseString(editor.getValue()); }
		catch (RuntimeException e) { setStatus("Editor JSON is invalid", false); return null; }
	}

	private void applyValue() {
		JsonElement model = currentModel();
		if (model == null) return;
		if (selected.isEmpty()) { setStatus("Pick a value in the tree first", false); return; }
		JsonElement parent = find(model, selected.subList(0, selected.size() - 1));
		String token = selected.get(selected.size() - 1);
		JsonElement old = find(model, selected);
		JsonElement nv = parseValue(valueBox.getValue(), old);
		if (parent != null && parent.isJsonObject()) parent.getAsJsonObject().add(token, nv);
		else if (parent != null && parent.isJsonArray()) {
			try { parent.getAsJsonArray().set(Integer.parseInt(token), nv); } catch (RuntimeException e) { return; }
		} else return;
		editor.setValue(pretty(model));
		setStatus("Value changed, press Save to apply", true);
	}

	private static JsonElement parseValue(String text, JsonElement old) {
		String t = text.trim();
		boolean structured = t.startsWith("{") || t.startsWith("[") || t.startsWith("\"");
		if (old != null && old.isJsonPrimitive() && old.getAsJsonPrimitive().isString() && !structured) return new JsonPrimitive(text);
		try { return JsonParser.parseString(t); }
		catch (RuntimeException e) { return new JsonPrimitive(text); }
	}

	private void addProperty() {
		JsonElement model = currentModel();
		if (model == null) return;
		JsonElement target = find(model, selected);
		if (target != null && target.isJsonObject()) {
			String key = "new_property";
			int i = 2;
			while (target.getAsJsonObject().has(key)) key = "new_property_" + i++;
			target.getAsJsonObject().add(key, new JsonObject());
			expanded.add(id(selected));
		} else if (target != null && target.isJsonArray()) {
			target.getAsJsonArray().add(new JsonObject());
			expanded.add(id(selected));
		} else return;
		editor.setValue(pretty(model));
	}

	private void removeProperty() {
		if (selected.isEmpty()) return;
		JsonElement model = currentModel();
		if (model == null) return;
		JsonElement parent = find(model, selected.subList(0, selected.size() - 1));
		String token = selected.get(selected.size() - 1);
		if (parent != null && parent.isJsonObject()) parent.getAsJsonObject().remove(token);
		else if (parent != null && parent.isJsonArray()) {
			try { parent.getAsJsonArray().remove(Integer.parseInt(token)); } catch (RuntimeException e) { return; }
		}
		selected = List.of();
		editor.setValue(pretty(model));
	}

	// ---------------------------------------------------------------- save

	private String json() {
		if (stack.isEmpty() || mc.level == null) return EMPTY;
		var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
		try {
			return pretty(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(error -> new IllegalStateException(error)));
		} catch (RuntimeException overstacked) {
			// Count is above max_stack_size: the codec refuses it, so encode a legal copy and put the real count back.
			ItemStack legal = stack.copy();
			legal.setCount(Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize())));
			JsonElement el = ItemStack.CODEC.encodeStart(ops, legal).getOrThrow(error -> new IllegalStateException(error));
			if (el.isJsonObject()) el.getAsJsonObject().addProperty("count", stack.getCount());
			return pretty(el);
		}
	}

	/** A count above the stack's max_stack_size is rejected by the codec, so clamp it to the component value. */
	private static void clampCount(JsonElement value) {
		if (!value.isJsonObject()) return;
		JsonObject obj = value.getAsJsonObject();
		int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
		if (obj.has("components") && obj.get("components").isJsonObject()) {
			JsonElement max = obj.getAsJsonObject("components").get("minecraft:max_stack_size");
			if (max != null && max.isJsonPrimitive() && max.getAsJsonPrimitive().isNumber()) count = Math.min(count, Math.max(1, max.getAsInt()));
		}
		obj.addProperty("count", count);
	}

	private void save() {
		if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
		JsonElement value;
		ItemStack updated;
		int desired;
		try {
			value = JsonParser.parseString(editor.getValue());
			desired = value.isJsonObject() && value.getAsJsonObject().has("count") ? Math.max(1, Math.min(99, value.getAsJsonObject().get("count").getAsInt())) : 1;
			JsonElement legal = value.deepCopy();
			clampCount(legal); // the codec only accepts count <= max_stack_size
			updated = ItemStack.CODEC.parse(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), legal)
				.getOrThrow(error -> new IllegalArgumentException(error));
		} catch (RuntimeException e) {
			setStatus("Invalid item: " + e.getMessage(), false);
			return;
		}

		int target = slotIndex;
		boolean viaModify = false;
		boolean localOnly = false;
		var server = mc.getSingleplayerServer();
		if (server != null) {
			// Singleplayer: write straight into the server-side inventory. Skips the packet checks, so an overstacked item
			// (count 64, max_stack_size 1) is possible: make a 64 stack, then change max_stack_size to 1 and Save.
			updated.setCount(desired);
			if (target < 0) target = firstEmptySlot();
			if (target < 0) target = mc.player.getInventory().getSelectedSlot();
			final int slot = target;
			final ItemStack copy = updated.copy();
			final java.util.UUID id = mc.player.getUUID();
			server.execute(() -> {
				var sp = server.getPlayerList().getPlayer(id);
				if (sp == null) return;
				sp.getInventory().setItem(slot, copy);
				sp.inventoryMenu.broadcastChanges();
				sp.containerMenu.broadcastChanges();
			});
			slotIndex = target;
		} else if (desired > updated.getMaxStackSize() && mc.player.isCreative()) {
			// Creative: no command needed. Put the overstacked stack into the client inventory only (a packet with
			// count > max would be refused), then drag it out in the creative inventory like a normal stack.
			updated.setCount(desired);
			if (target < 0) target = firstEmptySlot();
			if (target < 0) target = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setItem(target, updated.copy());
			sendModify(value, target); // makes it real on the server when you have op; otherwise only the client copy stays
			slotIndex = target;
			localOnly = true;
		} else if (desired > updated.getMaxStackSize() && target >= 0) {
			sendModify(value, target);
			updated.setCount(Math.max(1, stack.getCount())); // the server keeps the count it already has
			viaModify = true;
		} else if (mc.player.isCreative()) {
			if (target < 0) target = firstEmptySlot();
			if (target < 0) target = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setItem(target, updated.copy());
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(menuSlot(target), updated.copy()));
			slotIndex = target;
		} else {
			// Survival on a server: the server owns the inventory, so ask it with a command (needs permission).
			String item = itemString(value.getAsJsonObject());
			int count = updated.getCount();
			String cmd = target < 0 ? "give @s " + item + " " + count : "item replace entity @s " + commandSlot(target) + " with " + item + " " + count;
			mc.getConnection().sendCommand(cmd);
		}
		stack = updated;
		editor.setValue(json());
		if (localOnly) { setStatus("Overstack: sent /item modify (real on the server only with op, else client side only)", true); return; }
		boolean clamped = server == null && !viaModify && updated.getCount() != desired;
		if (viaModify) { setStatus("Sent /item modify (needs op). The count stays " + updated.getCount(), true); return; }
		setStatus(clamped ? "Saved, count lowered to " + updated.getCount() + " (overstack needs singleplayer)" : "Saved (count " + updated.getCount() + ")", !clamped);
	}

	/** Real server-side overstack: /item modify is not checked against max_stack_size (needs op). */
	private void sendModify(JsonElement value, int slot) {
		JsonObject obj = value.getAsJsonObject();
		JsonObject comps = obj.has("components") && obj.get("components").isJsonObject() ? obj.getAsJsonObject("components") : new JsonObject();
		String snbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, comps).toString();
		mc.getConnection().sendCommand("item modify entity @s " + commandSlot(slot) + " {function:\"minecraft:set_components\",components:" + snbt + "}");
	}

	private int firstEmptySlot() {
		for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) return i;
		return -1;
	}

	/** Player inventory index -> inventory menu slot id (what the creative packet wants). */
	private static int menuSlot(int inv) {
		if (inv < 9) return 36 + inv;
		if (inv < 36) return inv;
		if (inv < 40) return 44 - inv;
		return 45;
	}

	private static String commandSlot(int inv) {
		if (inv < 9) return "hotbar." + inv;
		if (inv < 36) return "inventory." + (inv - 9);
		return switch (inv) { case 36 -> "armor.feet"; case 37 -> "armor.legs"; case 38 -> "armor.chest"; case 39 -> "armor.head"; default -> "weapon.offhand"; };
	}

	/** id[component=snbt,!removed] - the syntax /item and /give accept. */
	private static String itemString(JsonObject obj) {
		StringBuilder sb = new StringBuilder(obj.get("id").getAsString());
		if (obj.has("components") && obj.get("components").isJsonObject() && obj.getAsJsonObject("components").size() > 0) {
			sb.append('[');
			boolean first = true;
			for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("components").entrySet()) {
				if (!first) sb.append(',');
				first = false;
				if (e.getKey().startsWith("!")) sb.append(e.getKey());
				else sb.append(e.getKey()).append('=').append(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, e.getValue()));
			}
			sb.append(']');
		}
		return sb.toString();
	}

	private void setStatus(String text, boolean ok) {
		status = text;
		statusOk = ok;
		if (mc.gui != null) mc.gui.setOverlayMessage(Component.literal(text), false);
	}

	private static String pretty(JsonElement value) { return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(value); }

	private String targetName() {
		if (slotIndex < 0) return "new copy in your inventory";
		if (slotIndex < 9) return "hotbar " + (slotIndex + 1);
		if (slotIndex < 36) return "inventory slot " + (slotIndex - 8);
		return slotIndex == 40 ? "offhand" : "armor slot";
	}

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		extractTransparentBackground(g);
		g.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
		g.text(font, "Item: " + (stack.isEmpty() ? "empty" : stack.getItem()) + "  Amount: " + (stack.isEmpty() ? 0 : stack.getCount()) + "  -> " + targetName(), 10, 28, 0xFFCCCCCC);
		g.text(font, "Tree (click a value, edit below, press Set)", 10, 45, 0xFFFFFFFF);
		g.text(font, "JSON editor", editorX, 28, 0xFFFFFFFF);
		if (!status.isEmpty()) g.text(font, status, 150, height - 26, statusOk ? 0xFF88FF88 : 0xFFFF7777);
		super.extractRenderState(g, mouseX, mouseY, delta);
	}
}
