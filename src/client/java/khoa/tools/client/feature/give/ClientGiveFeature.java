package khoa.tools.client.feature.give;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

import khoa.tools.client.feature.KhoaFeature;

/** Client only creative give command, with the vanilla ItemArgument parser and suggestions. */
public final class ClientGiveFeature implements KhoaFeature {
	@Override public String id() { return "give"; }
	@Override public String displayName() { return "Client Give"; }
	@Override public String description() { return "Adds /khoatoolsclient give <item[components]> [count]. Works only in creative mode."; }
	@Override public void activate(Minecraft mc) { }

	@Override public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id())
			.then(ClientCommands.argument("item", ItemArgument.item(context))
				.executes(ctx -> give(ctx.getSource().getClient(), ItemArgument.getItem(ctx, "item"), 1))
				.then(ClientCommands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
					.executes(ctx -> give(ctx.getSource().getClient(), ItemArgument.getItem(ctx, "item"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))));
	}

	private static int give(Minecraft mc, ItemInput input, int count) {
		if (mc.player == null || !mc.player.isCreative()) {
			if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Client give is available in creative mode only."));
			return 0;
		}
		try {
			ItemStack stack = input.createItemStack(count);
			int inventorySlot = firstEmptySlot(mc.player);
			if (inventorySlot < 0) inventorySlot = mc.player.getInventory().getSelectedSlot();
			mc.player.getInventory().setItem(inventorySlot, stack);
			// Creative inventory updates are authoritative on the server only after this packet.
			int packetSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
			mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket(packetSlot, stack.copy()));
			return 1;
		} catch (Exception e) {
			mc.player.sendSystemMessage(Component.literal("Could not create that item: " + e.getMessage()));
			return 0;
		}
	}

	private static int firstEmptySlot(net.minecraft.client.player.LocalPlayer player) {
		for (int i = 0; i < 36; i++) {
			if (player.getInventory().getItem(i).isEmpty()) return i;
		}
		return -1;
	}
}
