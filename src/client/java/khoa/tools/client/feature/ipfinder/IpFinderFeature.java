package khoa.tools.client.feature.ipfinder;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

import khoa.tools.client.feature.KhoaFeature;

/**
 * Prints the real address (ip:port) the client is connected to when joining, which also covers
 * server transfers (a transfer reconnects, so JOIN fires again). /khoatoolsclient getip prints it again.
 */
public final class IpFinderFeature implements KhoaFeature {
	private String last;
	private boolean printPending;

	@Override
	public String id() {
		return "getip";
	}

	@Override
	public String displayName() {
		return "Server IP";
	}

	@Override
	public String description() {
		return "Prints the real ip:port you are connected to. It is also printed automatically on every join (and after a server transfer). "
			+ "Behind a proxy (Velocity/Bungee) you only see the proxy's address. Click the card to print it once; it turns itself off.";
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (last == null) {
			mc.player.sendSystemMessage(Component.literal("Not connected to a server yet").withStyle(ChatFormatting.RED));
			return;
		}
		print(mc);
	}

	@Override
	public void onInit() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			last = describe(handler.getConnection().getRemoteAddress());
			printPending = true;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> printPending = false);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (printPending && mc.player != null) {
				printPending = false;
				print(mc);
			}
		});
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("getip").executes(ctx -> {
			if (last == null) {
				ctx.getSource().sendError(Component.literal("Not connected to a server yet"));
				return 0;
			}
			print(ctx.getSource().getClient());
			return 1;
		}));
	}

	private void print(Minecraft mc) {
		mc.player.sendSystemMessage(Component.literal("Server address: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(last).withStyle(ChatFormatting.AQUA)));
	}

	private static String describe(SocketAddress address) {
		if (address instanceof InetSocketAddress inet) {
			String ip = inet.getAddress() != null ? inet.getAddress().getHostAddress() : inet.getHostString();
			String host = inet.getHostString();
			String shown = ip.equals(host) ? ip : host + " (" + ip + ")";
			return shown + ":" + inet.getPort();
		}
		return "singleplayer / local (" + address + ")";
	}
}
