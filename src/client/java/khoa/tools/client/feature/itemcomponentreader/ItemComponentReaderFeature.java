package khoa.tools.client.feature.itemcomponentreader;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import khoa.tools.client.feature.KhoaFeature;

/** Opens the held item editor. The editor uses the vanilla item argument syntax. */
public final class ItemComponentReaderFeature implements KhoaFeature {
	/** Set by the commands; the screen opens on the next tick, otherwise the chat screen closes it again right after the command runs. */
	private static volatile boolean openNextTick;

	@Override public String id() { return "itemcomponentreader"; }
	@Override public String displayName() { return "Item Component Reader"; }
	@Override public String description() {
		return "Edit the item in your main hand with vanilla item components. The item argument accepts component values and removed components.";
	}
	@Override public List<khoa.tools.setting.Setting> settings() { return List.of(); }

	@Override public void onInit() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openNextTick) {
				openNextTick = false;
				activate(client);
			}
		});
	}

	@Override public void activate(Minecraft mc) { mc.setScreen(new ItemComponentReaderScreen(mc, mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem().copy())); }

	@Override public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal(id()).executes(ctx -> { openNextTick = true; return 1; }));
		root.then(ClientCommands.literal("reader").executes(ctx -> { openNextTick = true; return 1; }));
	}
}
