package khoa.tools.client.feature.customitemtab;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import khoa.tools.client.feature.KhoaFeature;

/**
 * /khoatoolsclient customitemtab create|edit|save|delete [name]: your own creative inventory tabs. While a tab is being
 * edited, hold the "Add item to customtab" key (Controls, KhoaTools; default Left Alt) and click an item in the creative inventory.
 */
public final class CustomItemTabFeature implements KhoaFeature {
	@Override public String id() { return "customitemtab"; }
	@Override public String displayName() { return "Custom Item Tab"; }
	@Override public String description() {
		return "Your own creative inventory tabs (on the next page of the creative inventory). Commands: /khoatoolsclient customitemtab create <name> | edit <name> | save | list [name] | refresh | rename <old> <new> | icon <name> <item> | delete <name>. "
			+ "While editing, hold the 'Add item to customtab' key (Controls > KhoaTools, default Left Alt) and click items in the creative inventory. Inside one of your tabs the same key removes the clicked item. Edit mode stays on after closing the inventory until save, leaving the world or closing the game; create turns it on automatically.";
	}

	@Override public void activate(Minecraft mc) {
		KhoaFeature.say(mc, "[CustomItemTab] /khoatoolsclient customitemtab <create|edit|save|delete> <name>");
	}

	@Override public void onInit() {
		KeyMapping.Category category = khoa.tools.client.util.KeyCategory.get();
		CustomTabs.addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.khoatools.customtab_add", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, category));

		CustomTabs.load();

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof CreativeModeInventoryScreen) {
				CustomTabs.onCreativeScreenOpened();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CustomTabs.onDisconnect());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> CustomTabs.save());
	}

	@Override public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.executes(ctx -> {
				activate(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("create")
				.then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
					String name = StringArgumentType.getString(ctx, "name");
					return reply(ctx, CustomTabs.create(name), "Created tab '" + name + "' (shown as " + CustomTabs.displayName(name) + "). Edit mode is ON: open the creative inventory, hold "
						+ CustomTabs.addKeyName() + " and click items to add them (the tab is on the next page). customitemtab save when done.");
				})))
			.then(ClientCommands.literal("edit")
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "name");
						return reply(ctx, CustomTabs.edit(name), "Editing '" + name + "': open the creative inventory, hold " + CustomTabs.addKeyName()
							+ " and click items. customitemtab save stops edit mode.");
					})))
			.then(ClientCommands.literal("save")
				.executes(ctx -> save(ctx))
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.executes(ctx -> save(ctx))))
			.then(ClientCommands.literal("list")
				.executes(ctx -> {
					List<String> tabs = CustomTabs.summary();
					Minecraft mc = ctx.getSource().getClient();
					KhoaFeature.say(mc, tabs.isEmpty() ? "[CustomItemTab] No custom tabs yet." : "[CustomItemTab] Tabs: " + String.join(", ", tabs));
					return 1;
				})
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "name");
						List<String> items = CustomTabs.itemNames(name);
						Minecraft mc = ctx.getSource().getClient();
						KhoaFeature.say(mc, items == null ? "[CustomItemTab] No custom tab named '" + name + "'"
							: "[CustomItemTab] '" + name + "' (" + items.size() + "): " + (items.isEmpty() ? "empty" : String.join(", ", items)));
						return items == null ? 0 : 1;
					})))
			.then(ClientCommands.literal("refresh").executes(ctx -> {
				int n = CustomTabs.forceRefresh();
				KhoaFeature.say(ctx.getSource().getClient(), "[CustomItemTab] Rebuilt " + n + " custom tab(s) and redrew the creative inventory.");
				return 1;
			}))
			.then(ClientCommands.literal("rename")
				.then(ClientCommands.argument("oldname", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.then(ClientCommands.argument("newname", StringArgumentType.word()).executes(ctx -> {
						String from = StringArgumentType.getString(ctx, "oldname");
						String to = StringArgumentType.getString(ctx, "newname");
						return reply(ctx, CustomTabs.rename(from, to), "Renamed '" + from + "' to '" + to + "' (shown as " + CustomTabs.displayName(to) + ").");
					}))))
			.then(ClientCommands.literal("icon")
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.then(ClientCommands.argument("item", IdentifierArgument.id())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
						.executes(ctx -> {
							String name = StringArgumentType.getString(ctx, "name");
							Identifier item = ctx.getArgument("item", Identifier.class);
							return reply(ctx, CustomTabs.icon(name, item), "Icon of '" + name + "' is now " + item + ".");
						}))))
			.then(ClientCommands.literal("delete")
				.then(ClientCommands.argument("name", StringArgumentType.word())
					.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(CustomTabs.names(), builder))
					.executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "name");
						return reply(ctx, CustomTabs.delete(name), "Deleted tab '" + name + "'.");
					}))));
	}

	private static int save(CommandContext<FabricClientCommandSource> ctx) {
		String was = CustomTabs.save();
		KhoaFeature.say(ctx.getSource().getClient(), was == null ? "[CustomItemTab] Nothing to save: not in edit mode." : "[CustomItemTab] Saved '" + was + "'. Edit mode is off.");
		return was == null ? 0 : 1;
	}

	private static int reply(CommandContext<FabricClientCommandSource> ctx, String error, String ok) {
		Minecraft mc = ctx.getSource().getClient();
		KhoaFeature.say(mc, "[CustomItemTab] " + (error != null ? error : ok));
		return error == null ? 1 : 0;
	}
}
