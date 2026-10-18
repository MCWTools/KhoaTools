package khoa.tools.client.feature.betterchat;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.setting.Setting;
import khoa.tools.client.chat.ChatComponentExt;

/**
 * /khoatoolsclient betterchat length &lt;n&gt;   - how many messages are kept before the oldest is deleted (0 = vanilla default)
 * /khoatoolsclient betterchat cache &lt;true|false&gt; - save chat per server to disk and show it again on the next join
 */
public final class BetterChatFeature implements KhoaFeature {
	@Override
	public String id() {
		return BetterChatConfig.SECTION;
	}

	@Override
	public String displayName() {
		return "Better Chat";
	}

	@Override
	public String description() {
		return "Keeps more chat history and can save server chat per server (.minecraft/khoatools/betterchat/<server>.json) "
			+ "and show it again on join. Click the card to turn Cache chat on/off.";
	}

	@Override
	public List<Setting> settings() {
		return List.of(
			Setting.integer("length", "Messages kept", "How many messages are kept before the oldest is deleted. 0 = vanilla default (100).",
				0, BetterChatConfig.MAX_LENGTH, BetterChatConfig::length, value -> applyLength(Minecraft.getInstance(), value)),
			Setting.bool("cache", "Cache chat", "Save server chat to disk and show it again on the next join.",
				BetterChatConfig::cache, value -> applyCache(Minecraft.getInstance(), value)));
	}

	@Override
	public boolean isActive() {
		return BetterChatConfig.cache();
	}

	@Override
	public void activate(Minecraft mc) {
		applyCache(mc, !BetterChatConfig.cache());
	}

	private static void applyLength(Minecraft mc, int amount) {
		BetterChatConfig.setLength(amount);
		if (mc.gui != null) {
			((ChatComponentExt) mc.gui.getChat()).khoatools$trimToLength();
		}
		ChatCache.onLengthChanged();
	}

	private static void applyCache(Minecraft mc, boolean enabled) {
		if (!enabled) {
			ChatCache.onCacheToggled(mc, false); // flush to disk while the option is still on
		}
		BetterChatConfig.setCache(enabled);
		if (enabled) {
			ChatCache.onCacheToggled(mc, true);
		}
	}

	@Override
	public void onInit() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChatCache.onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatCache.onDisconnect());
		ClientTickEvents.END_CLIENT_TICK.register(client -> ChatCache.tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ChatCache.flush(false));
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("betterchat")
			.executes(this::status)
			.then(ClientCommands.literal("length")
				.then(ClientCommands.argument("amount", IntegerArgumentType.integer(0, BetterChatConfig.MAX_LENGTH))
					.executes(this::setLength)))
			.then(ClientCommands.literal("cache")
				.then(ClientCommands.argument("enabled", BoolArgumentType.bool())
					.executes(this::setCache))));
	}

	private int status(CommandContext<FabricClientCommandSource> ctx) {
		int length = BetterChatConfig.length();
		ctx.getSource().sendFeedback(Component.literal("BetterChat: length = "
			+ (length == 0 ? "0 (default, " + BetterChatConfig.VANILLA_LENGTH + ")" : String.valueOf(length))
			+ ", cache = " + BetterChatConfig.cache()).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private int setLength(CommandContext<FabricClientCommandSource> ctx) {
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		applyLength(ctx.getSource().getClient(), amount);
		ctx.getSource().sendFeedback(Component.literal("BetterChat: length = "
			+ (amount == 0 ? "0 (default " + BetterChatConfig.VANILLA_LENGTH + ")" : amount + " messages"))
			.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private int setCache(CommandContext<FabricClientCommandSource> ctx) {
		boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
		applyCache(ctx.getSource().getClient(), enabled);
		ctx.getSource().sendFeedback(Component.literal("BetterChat: cache = " + enabled)
			.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
		return 1;
	}
}
