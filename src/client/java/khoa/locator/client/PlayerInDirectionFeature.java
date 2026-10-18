package khoa.locator.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;

import khoa.tools.client.feature.KhoaFeature;

/** /khoatoolsclient playerindirection - one-shot: prints the players in the direction you are looking. */
public final class PlayerInDirectionFeature implements KhoaFeature {
	@Override
	public String id() {
		return "playerindirection";
	}

	@Override
	public String displayName() {
		return "Player In Direction";
	}

	@Override
	public String description() {
		return "Prints in chat which players are in the direction you are looking at. One click = one scan, it turns itself off. No settings.";
	}

	@Override
	public void activate(Minecraft mc) {
		if (mc.player != null) {
			mc.player.sendSystemMessage(PlayerInDirection.run(mc));
		}
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root, CommandBuildContext context) {
		root.then(ClientCommands.literal("playerindirection").executes(ctx -> {
			activate(ctx.getSource().getClient());
			return 1;
		}));
	}
}
