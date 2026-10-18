package khoa.tools.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * A server-side tool. Its commands live under /khoatools, which only operators can use. /khoatools has no GUI.
 */
public interface KhoaServerFeature {
	String id();

	default String displayName() {
		return id();
	}

	default String description() {
		return "";
	}

	void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context);
}
