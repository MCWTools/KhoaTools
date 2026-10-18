package khoa.tools.client.gui;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import com.mojang.blaze3d.platform.InputConstants;

import khoa.tools.KhoaTools;
import khoa.tools.client.config.KhoaCommandBinds;
import khoa.tools.client.config.KhoaKeybinds;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.KhoaFeatures;
import khoa.tools.setting.Setting;

/**
 * /khoatools GUI: two tabs (ClientsideTools / ServersideTools), a search bar and a 2-column grid of cards.
 * Click a card = activate the feature (green while active). Click its Config button = description + settings.
 * Everything is generic: it only reads {@link KhoaFeature}, so new modules show up by
 * themselves once they are registered in KhoaFeatures.
 * The background is fully transparent and the game keeps running behind it. Esc closes the whole GUI.
 */
public final class KhoaToolsScreen extends Screen {
	private static final int GAP = 6;
	private static final int TAB_Y = 12;
	private static final int TAB_H = 20;
	private static final int TAB_W = 112;

	private static final int COL_TEXT = 0xFFFFFFFF;
	private static final int COL_DIM = 0xFFAAAAAA;
	private static final int COL_GREEN = 0xFF55FF77;
	private static final int BG_NORMAL = 0x90101010;
	private static final int BG_HOVER = 0xB0303030;
	private static final int BG_ACTIVE = 0xA02E9E4A;
	private static final int BG_ACTIVE_HOVER = 0xC03BBF5C;
	private static final int BORDER = 0xFF555555;

	private enum Tab {
		CLIENT("ClientsideTools");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	/** One entry in the grid, built from either kind of feature. */
	private record Card(String id, String name, String description, List<Setting> settings, Runnable click, BooleanSupplier active) {
		boolean matches(String query) {
			return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)
				|| id.toLowerCase(Locale.ROOT).contains(query) || description.toLowerCase(Locale.ROOT).contains(query);
		}
	}

	private record Row(Setting setting, int y, int height, EditBox box, EditBox box2) {
	}

	private Tab tab = Tab.CLIENT;
	private String searchText = "";
	private double scroll;
	private Card configCard;
	/** Config screen: the Keybind button was clicked and the next key press is the new keybind (Esc = None). */
	private boolean waitingKey;
	private int keybindY;
	/** Config screen: Command row (extra /command for this feature). pendingCommand != null = typed but not applied yet. */
	private int commandY;
	private EditBox commandBox;
	private String pendingCommand;
	private int pendingTicks;

	private EditBox search;
	private final List<Row> rows = new ArrayList<>();

	/** Number boxes apply their value when they lose focus (click away, Enter, tab change or closing), not on every keystroke. */
	private static final class IntField {
		final EditBox box;
		final Setting setting;
		String text;
		boolean dirty;

		IntField(EditBox box, Setting setting) {
			this.box = box;
			this.setting = setting;
		}
	}

	private final List<IntField> intFields = new ArrayList<>();

	private void commitInts(boolean force) {
		for (IntField f : intFields) {
			if (f.dirty && (force || !f.box.isFocused())) {
				f.dirty = false;
				try {
					f.setting.setInt(Integer.parseInt(f.text));
				} catch (NumberFormatException ignored) {
					// empty, just "-" or too long: keep the previous value
				}
			}
		}
	}

	private int frameW;
	private int frameX;
	private int listTop;
	private int listBottom;

	public KhoaToolsScreen() {
		super(Component.literal("KhoaTools"));
	}

	// ------------------------------------------------------------------ data

	private List<Card> cards() {
		List<Card> out = new ArrayList<>();
		String query = searchText.trim().toLowerCase(Locale.ROOT);
		for (KhoaFeature f : KhoaFeatures.all()) {
			Card card = new Card(f.id(), f.displayName(), f.description(), f.settings(), () -> f.activate(this.minecraft), f::isActive);
			if (card.matches(query)) {
				out.add(card);
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ layout

	private int cardW() {
		return (frameW - GAP) / 2;
	}

	/** Height = 20% of the card width (never smaller than 28 so the text and the button fit). */
	private int cardH() {
		return Math.max(28, Math.round(cardW() * 0.2F));
	}

	private void layout() {
		frameW = Math.min(this.width - 24, 520);
		frameX = (this.width - frameW) / 2;
		listTop = TAB_Y + TAB_H + 8;
		listBottom = this.height - 12;
	}

	private int contentHeight(int count) {
		int rowsCount = (count + 1) / 2;
		return rowsCount == 0 ? 0 : rowsCount * (cardH() + GAP) - GAP;
	}

	private double maxScroll(int count) {
		return Math.max(0, contentHeight(count) - (listBottom - listTop));
	}

	// ------------------------------------------------------------------ widgets

	@Override
	protected void init() {
		commitCommand(); // typed but not applied yet (window resized)
		this.clearWidgets();
		commitInts(true); // a tab change must not lose a number that is still being edited
		this.intFields.clear();
		this.rows.clear();
		layout();
		commandBox = null;
		if (configCard == null) {
			int searchX = frameX + 2 * TAB_W + 2 * GAP;
			int searchW = Math.max(60, frameX + frameW - searchX);
			search = new EditBox(this.font, searchX, TAB_Y, searchW, TAB_H, Component.literal("Search"));
			search.setHint(Component.literal("Search..."));
			search.setMaxLength(48);
			search.setValue(searchText);
			search.setResponder(value -> {
				searchText = value;
				scroll = 0;
			});
			this.addRenderableWidget(search);
		} else {
			buildConfigRows();
		}
	}

	private void buildConfigRows() {
		int descLines = this.font.split(Component.literal(configCard.description()), frameW).size();
		int y = TAB_Y + TAB_H + 12 + descLines * 11 + 14;
		int controlW = 120;
		int controlX = frameX + frameW - controlW - 8;
		keybindY = y;
		for (Setting setting : configCard.settings()) {
			int labelW = controlX - frameX - 24;
			int lines = Math.max(1, Math.min(3, this.font.split(Component.literal(setting.description), labelW).size()));
			int h = Math.max(30, 14 + lines * 11 + 6);
			int textBoxY = y + 14 + lines * 11 + 4;
			if (setting.type == Setting.Type.TEXT) {
				h = 14 + lines * 11 + 4 + 70 + 6; // title + description + a 70px box
			}
			if (setting.type == Setting.Type.PAIR) {
				h = Math.max(h, 42); // room for the Yaw/Pitch captions above the two boxes
			}
			EditBox box = null;
			EditBox box2 = null;
			if (setting.type == Setting.Type.INT || setting.type == Setting.Type.STRING) {
				box = new EditBox(this.font, controlX, y + (h - 18) / 2, controlW, 18, Component.literal(setting.label));
				final EditBox field = box;
				if (setting.type == Setting.Type.INT) {
					boolean negative = setting.min < 0;
					field.setMaxLength(9);
					field.setValue(String.valueOf(setting.getInt()));
					final IntField intField = new IntField(field, setting);
					this.intFields.add(intField);
					field.setResponder(value -> {
						String clean = digitsOnly(value, negative);
						if (!clean.equals(value)) {
							field.setValue(clean); // calls this responder again with the clean text
							return;
						}
						intField.text = clean;
						intField.dirty = !clean.isEmpty() && !clean.equals("-");
					});
				} else {
					field.setMaxLength(64);
					field.setValue(setting.getString());
					field.setResponder(setting::setString);
				}
				this.addRenderableWidget(field);
			} else if (setting.type == Setting.Type.DECIMAL) {
				box = decimalBox(controlX, y + (h - 18) / 2, controlW, setting.dmin < 0, setting.getDecimal(), setting::setDecimal, setting.label);
			} else if (setting.type == Setting.Type.PAIR) {
				box = decimalBox(controlX, y + 18, 56, true, setting.getDecimal(), setting::setDecimal, setting.labelA);
				box2 = decimalBox(controlX + 64, y + 18, 56, true, setting.getDecimal2(), setting::setDecimal2, setting.labelB);
			} else if (setting.type == Setting.Type.TEXT) {
				MultiLineEditBox area = MultiLineEditBox.builder().setX(frameX + 8).setY(textBoxY)
					.setPlaceholder(Component.literal("Type here...")).build(this.font, frameW - 16, 70, Component.literal(setting.label));
				area.setCharacterLimit(1000);
				area.setValue(setting.getString());
				area.setValueListener(setting::setString);
				this.addRenderableWidget(area);
			} else if (setting.type == Setting.Type.SLIDER) {
				this.addRenderableWidget(new IntSlider(controlX, y + (h - 20) / 2, controlW, 20, setting));
			}
			rows.add(new Row(setting, y, h, box, box2));
			y += h + 4;
		}
		keybindY = y; // Keybind row sits under the last setting
		commandY = keybindY + 34; // Command row sits under the Keybind row
		commandBox = new EditBox(this.font, controlX, commandY + 6, controlW, 18, Component.literal("Command"));
		commandBox.setMaxLength(48);
		commandBox.setHint(Component.literal("e.g. " + configCard.id()));
		commandBox.setValue(KhoaCommandBinds.get(configCard.id()));
		commandBox.setResponder(value -> {
			pendingCommand = value; // applied after a short pause, on Enter, on Back or when the screen closes
			pendingTicks = 0;
		});
		pendingCommand = null;
		this.addRenderableWidget(commandBox);
	}

	/** Saves the typed command names and creates / hides the aliases right now. */
	private void commitCommand() {
		if (pendingCommand == null || configCard == null || commandBox == null) {
			return;
		}
		String text = pendingCommand;
		pendingCommand = null;
		KhoaCommandBinds.tell(KhoaCommandBinds.set(configCard.id(), text));
		String clean = KhoaCommandBinds.get(configCard.id());
		if (!clean.equals(commandBox.getValue()) && !commandBox.isFocused()) {
			commandBox.setValue(clean); // show what was really saved (lower case, valid names only)
			pendingCommand = null;
		}
	}

	@Override
	public void tick() {
		super.tick();
		commitInts(false);
		if (pendingCommand != null && ++pendingTicks >= 40) {
			commitCommand();
		}
	}

	@Override
	public void removed() {
		commitInts(true);
		commitCommand();
		super.removed();
	}

	private EditBox decimalBox(int x, int y, int w, boolean allowNegative, double initial, DoubleConsumer set, String name) {
		EditBox field = new EditBox(this.font, x, y, w, 18, Component.literal(name));
		field.setMaxLength(12);
		field.setValue(BigDecimal.valueOf(initial).stripTrailingZeros().toPlainString());
		field.setResponder(value -> {
			String clean = decimalOnly(value, allowNegative);
			if (!clean.equals(value)) {
				field.setValue(clean); // calls this responder again with the clean text
				return;
			}
			try {
				set.accept(Double.parseDouble(clean));
			} catch (NumberFormatException ignored) {
				// "", "-" or "." while typing: keep the previous value
			}
		});
		this.addRenderableWidget(field);
		return field;
	}

	/** Digits, at most one '.', and one leading '-' when allowed. */
	private static String decimalOnly(String value, boolean allowNegative) {
		StringBuilder sb = new StringBuilder();
		boolean dot = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9' || (c == '.' && !dot) || (allowNegative && c == '-' && sb.isEmpty())) {
				dot |= c == '.';
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/** Vanilla slider widget bound to an INT/SLIDER setting (min..max, whole numbers). */
	private static final class IntSlider extends AbstractSliderButton {
		private final Setting setting;

		IntSlider(int x, int y, int w, int h, Setting setting) {
			super(x, y, w, h, Component.empty(), (setting.getInt() - setting.min) / (double) Math.max(1, setting.max - setting.min));
			this.setting = setting;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (setting != null) {
				setMessage(Component.literal(String.valueOf(setting.getInt())));
			}
		}

		@Override
		protected void applyValue() {
			int v = setting.min + (int) Math.round(this.value * (setting.max - setting.min));
			setting.setInt(v);
			this.value = (v - setting.min) / (double) Math.max(1, setting.max - setting.min); // snap to whole numbers
		}
	}

	/** Number boxes cannot take letters: everything but digits (and one leading '-' when allowed) is dropped. */
	private static String digitsOnly(String value, boolean allowNegative) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9' || (allowNegative && c == '-' && sb.isEmpty())) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	// ------------------------------------------------------------------ render

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (configCard == null) {
			renderList(graphics, mouseX, mouseY);
		} else {
			renderConfig(graphics, mouseX, mouseY);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick); // the EditBoxes
	}

	private void renderList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		for (Tab t : Tab.values()) {
			int x = tabX(t);
			boolean selected = t == tab;
			boolean hover = inside(mouseX, mouseY, x, TAB_Y, TAB_W, TAB_H);
			g.fill(x, TAB_Y, x + TAB_W, TAB_Y + TAB_H, selected ? 0xB0000000 : (hover ? 0x90303030 : 0x70000000));
			if (selected) {
				g.fill(x, TAB_Y + TAB_H - 2, x + TAB_W, TAB_Y + TAB_H, COL_GREEN);
			}
			g.centeredText(this.font, t.label, x + TAB_W / 2, TAB_Y + (TAB_H - 8) / 2, selected ? COL_TEXT : COL_DIM);
		}

		List<Card> cards = cards();
		scroll = Math.max(0, Math.min(scroll, maxScroll(cards.size())));
		if (cards.isEmpty()) {
			String message = "Nothing found.";
			g.centeredText(this.font, message, this.width / 2, listTop + 20, COL_DIM);
			return;
		}

		g.enableScissor(frameX, listTop, frameX + frameW, listBottom);
		int cw = cardW();
		int ch = cardH();
		for (int i = 0; i < cards.size(); i++) {
			Card card = cards.get(i);
			int x = frameX + (i % 2) * (cw + GAP);
			int y = listTop - (int) scroll + (i / 2) * (ch + GAP);
			if (y + ch < listTop || y > listBottom) {
				continue;
			}
			boolean active = card.active().getAsBoolean();
			boolean inList = mouseY >= listTop && mouseY < listBottom;
			boolean hover = inList && inside(mouseX, mouseY, x, y, cw, ch);
			g.fill(x, y, x + cw, y + ch, active ? (hover ? BG_ACTIVE_HOVER : BG_ACTIVE) : (hover ? BG_HOVER : BG_NORMAL));
			border(g, x, y, x + cw, y + ch, active ? COL_GREEN : BORDER);

			int bw = configButtonW(cw);
			int bh = Math.min(ch - 10, 20);
			int bx = x + cw - bw - 5;
			int by = y + (ch - bh) / 2;
			boolean overButton = inList && inside(mouseX, mouseY, bx, by, bw, bh);
			g.fill(bx, by, bx + bw, by + bh, overButton ? 0xC0505050 : 0xA0202020);
			border(g, bx, by, bx + bw, by + bh, overButton ? COL_TEXT : 0xFF777777);
			g.centeredText(this.font, "Config", bx + bw / 2, by + (bh - 8) / 2, COL_TEXT);

			String name = this.font.plainSubstrByWidth(card.name(), bx - x - 14);
			g.text(this.font, name, x + 8, y + (ch - 8) / 2, COL_TEXT);
		}
		g.disableScissor();
	}

	private void renderConfig(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean overBack = inside(mouseX, mouseY, frameX, TAB_Y, 60, TAB_H);
		g.fill(frameX, TAB_Y, frameX + 60, TAB_Y + TAB_H, overBack ? 0xC0505050 : 0xA0202020);
		border(g, frameX, TAB_Y, frameX + 60, TAB_Y + TAB_H, overBack ? COL_TEXT : 0xFF777777);
		g.centeredText(this.font, "< Back", frameX + 30, TAB_Y + (TAB_H - 8) / 2, COL_TEXT);
		g.text(this.font, configCard.name(), frameX + 70, TAB_Y + (TAB_H - 8) / 2, configCard.active().getAsBoolean() ? COL_GREEN : COL_TEXT);

		int y = TAB_Y + TAB_H + 12;
		for (FormattedCharSequence line : this.font.split(Component.literal(configCard.description()), frameW)) {
			g.text(this.font, line, frameX, y, COL_DIM);
			y += 11;
		}

		for (Row row : rows) {
			Setting s = row.setting();
			g.fill(frameX, row.y(), frameX + frameW, row.y() + row.height(), 0x90101010);
			border(g, frameX, row.y(), frameX + frameW, row.y() + row.height(), BORDER);
			g.text(this.font, s.label, frameX + 8, row.y() + 6, COL_TEXT);
			int controlX = frameX + frameW - 128;
			int labelW = controlX - frameX - 24;
			int dy = row.y() + 18;
			int shown = 0;
			for (FormattedCharSequence line : this.font.split(Component.literal(s.description), labelW)) {
				if (shown++ == 3) {
					break;
				}
				g.text(this.font, line, frameX + 8, dy, COL_DIM);
				dy += 11;
			}
			if (s.type == Setting.Type.BOOL) {
				int bx = frameX + frameW - 8 - 16;
				int by = row.y() + (row.height() - 16) / 2;
				boolean on = s.getBool();
				g.fill(bx, by, bx + 16, by + 16, 0xA0000000);
				border(g, bx, by, bx + 16, by + 16, on ? COL_GREEN : 0xFF999999);
				if (on) {
					g.fill(bx + 4, by + 4, bx + 12, by + 12, COL_GREEN);
				}
			} else if (s.type == Setting.Type.PAIR) {
				g.text(this.font, s.labelA, controlX, row.y() + 6, COL_DIM);
				g.text(this.font, s.labelB, controlX + 64, row.y() + 6, COL_DIM);
			} else if (s.type == Setting.Type.ACTION || s.type == Setting.Type.CHOICE) {
				int bx = frameX + frameW - 128;
				int by = row.y() + (row.height() - 18) / 2;
				boolean hover = inside(mouseX, mouseY, bx, by, 120, 18);
				g.fill(bx, by, bx + 120, by + 18, hover ? BG_HOVER : BG_NORMAL);
				border(g, bx, by, bx + 120, by + 18, BORDER);
				String text = s.type == Setting.Type.ACTION ? s.getString() : s.options.get(s.getChoice());
				g.text(this.font, text, bx + (120 - this.font.width(text)) / 2, by + 5, COL_TEXT);
			}
		}
		renderKeybindRow(g, mouseX, mouseY);
		renderCommandRow(g);
	}

	private void renderCommandRow(GuiGraphicsExtractor g) {
		int h = 30;
		g.fill(frameX, commandY, frameX + frameW, commandY + h, 0x90101010);
		border(g, frameX, commandY, frameX + frameW, commandY + h, BORDER);
		g.text(this.font, "Command", frameX + 8, commandY + 6, COL_TEXT);
		g.text(this.font, "Extra /command, same arguments. Several: separate with spaces.", frameX + 8, commandY + 18, COL_DIM);
	}

	private void renderKeybindRow(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int h = 30;
		g.fill(frameX, keybindY, frameX + frameW, keybindY + h, 0x90101010);
		border(g, frameX, keybindY, frameX + frameW, keybindY + h, BORDER);
		g.text(this.font, "Keybind", frameX + 8, keybindY + 6, COL_TEXT);
		g.text(this.font, "Key that runs / toggles this feature. Esc while waiting = None.", frameX + 8, keybindY + 18, COL_DIM);
		int bx = frameX + frameW - 128;
		int by = keybindY + (h - 18) / 2;
		boolean hover = inside(mouseX, mouseY, bx, by, 120, 18);
		g.fill(bx, by, bx + 120, by + 18, waitingKey ? 0xA0806010 : (hover ? BG_HOVER : BG_NORMAL));
		border(g, bx, by, bx + 120, by + 18, waitingKey ? 0xFFFFCC33 : BORDER);
		String text = waitingKey ? "[ Waiting ]" : "[ " + KhoaKeybinds.keyName(KhoaKeybinds.get(configCard.id())) + " ]";
		g.text(this.font, text, bx + (120 - this.font.width(text)) / 2, by + 5, COL_TEXT);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) { // an EditBox took it (focus + caret)
			return true;
		}
		this.setFocused((GuiEventListener) null);
		if (event.button() != 0) {
			return false;
		}
		double mx = event.x();
		double my = event.y();
		return configCard == null ? clickList(mx, my) : clickConfig(mx, my);
	}

	private boolean clickList(double mx, double my) {
		for (Tab t : Tab.values()) {
			if (inside(mx, my, tabX(t), TAB_Y, TAB_W, TAB_H)) {
				if (tab != t) {
					tab = t;
					scroll = 0;
				}
				return true;
			}
		}
		if (my < listTop || my >= listBottom) {
			return false;
		}
		List<Card> cards = cards();
		int cw = cardW();
		int ch = cardH();
		for (int i = 0; i < cards.size(); i++) {
			int x = frameX + (i % 2) * (cw + GAP);
			int y = listTop - (int) scroll + (i / 2) * (ch + GAP);
			if (!inside(mx, my, x, y, cw, ch)) {
				continue;
			}
			int bw = configButtonW(cw);
			int bh = Math.min(ch - 10, 20);
			if (inside(mx, my, x + cw - bw - 5, y + (ch - bh) / 2, bw, bh)) {
				configCard = cards.get(i);
				waitingKey = false;
				this.rebuildWidgets();
			} else {
				try {
					cards.get(i).click().run();
				} catch (RuntimeException e) {
					KhoaTools.LOGGER.error("Feature {} failed", cards.get(i).id(), e);
				}
			}
			return true;
		}
		return false;
	}

	private boolean clickConfig(double mx, double my) {
		if (inside(mx, my, frameX + frameW - 128, keybindY + (30 - 18) / 2, 120, 18)) {
			waitingKey = !waitingKey;
			return true;
		}
		waitingKey = false; // any other click cancels waiting
		if (inside(mx, my, frameX, TAB_Y, 60, TAB_H)) {
			commitCommand();
			configCard = null;
			this.rebuildWidgets();
			return true;
		}
		for (Row row : rows) {
			if ((row.setting().type == Setting.Type.ACTION || row.setting().type == Setting.Type.CHOICE)
				&& inside(mx, my, frameX + frameW - 128, row.y() + (row.height() - 18) / 2, 120, 18)) {
				if (row.setting().type == Setting.Type.ACTION) {
					row.setting().run();
				} else {
					row.setting().nextChoice();
				}
				return true;
			}
			if (row.setting().type == Setting.Type.BOOL
				&& inside(mx, my, frameX, row.y(), frameW, row.height())) {
				row.setting().setBool(!row.setting().getBool());
				return true;
			}
		}
		return false;
	}

	/** While the Keybind button says [ Waiting ]: Esc = None, any other key = the new keybind. Handled before Screen closes on Esc. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			for (IntField f : intFields) {
				if (f.box.isFocused()) {
					commitInts(true);
					return true;
				}
			}
		}
		if (configCard != null && commandBox != null && commandBox.isFocused()
			&& (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			commitCommand();
			return true;
		}
		if (configCard != null && waitingKey) {
			int key = event.key();
			if (key == InputConstants.KEY_ESCAPE) {
				KhoaKeybinds.set(configCard.id(), KhoaKeybinds.NONE);
				waitingKey = false;
				return true;
			}
			if (key != InputConstants.UNKNOWN.getValue()) {
				KhoaKeybinds.set(configCard.id(), key);
				waitingKey = false;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (configCard == null) {
			scroll = Math.max(0, Math.min(scroll - scrollY * 24, maxScroll(cards().size())));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	// ------------------------------------------------------------------ misc

	private int tabX(Tab t) {
		return frameX + t.ordinal() * (TAB_W + GAP);
	}

	private static int configButtonW(int cardWidth) {
		return Math.max(40, Math.min(56, cardWidth / 3));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1, x1 + 1, y2, color);
		g.fill(x2 - 1, y1, x2, y2, color);
	}

	/** The world stays visible and keeps running: no dim, no blur, no menu background. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Esc closes the whole GUI (Screen.keyPressed handles the key before anything else, so no pause menu). */
	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
