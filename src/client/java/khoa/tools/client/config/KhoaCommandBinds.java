package khoa.tools.client.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.Minecraft;

import khoa.tools.KhoaTools;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.KhoaFeatures;

/**
 * Extra command names for a feature (config/khoatools.json -> commandbinds.<feature id> = "chatsearch cs").
 * Typing /chatsearch abc does exactly what /khoatoolsclient chatsearch abc does: the alias runs the same command and shares the
 * same argument nodes, so arguments and suggestions are identical. Binds are applied live (no restart); removing one hides it
 * at once; moving a name from one feature to another needs a rejoin.
 */
public final class KhoaCommandBinds {
	private static final String SECTION = "commandbinds";
	private static final String ROOT = "khoatoolsclient";
	private static final Pattern VALID = Pattern.compile("[a-z0-9_]{1,32}");

	/** alias -> feature id, what the config says right now (an alias node only works while this still says so). */
	private static final Map<String, String> BOUND = new HashMap<>();
	/** alias -> feature id, the alias nodes that exist in {@link #liveDispatcher}. */
	private static final Map<String, String> REGISTERED = new HashMap<>();
	private static CommandDispatcher<FabricClientCommandSource> liveDispatcher;

	private KhoaCommandBinds() {
	}

	/** The saved text for the config screen, e.g. "chatsearch cs". */
	public static String get(String featureId) {
		return KhoaConfig.getString(SECTION, featureId, "");
	}

	/** Valid, lower-case, distinct names from free text (a leading / is ignored). */
	static List<String> parse(String text) {
		List<String> names = new ArrayList<>();
		for (String word : text.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
			String name = word.startsWith("/") ? word.substring(1) : word;
			if (VALID.matcher(name).matches() && !names.contains(name)) {
				names.add(name);
			}
		}
		return names;
	}

	/** Called from the command registration event, after every feature registered its commands: (re)creates all saved aliases. */
	public static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		liveDispatcher = dispatcher;
		REGISTERED.clear();
		BOUND.clear();
		for (KhoaFeature feature : KhoaFeatures.all()) {
			for (String name : parse(get(feature.id()))) {
				BOUND.put(name, feature.id());
				String problem = register(dispatcher, name, feature.id());
				if (problem != null) {
					KhoaTools.LOGGER.warn("Command bind /{} -> {}: {}", name, feature.id(), problem);
				}
			}
		}
	}

	/**
	 * Saves the binds of one feature and applies them to the running game.
	 * Returns a short message for the player when something could not be applied, else null.
	 */
	public static String set(String featureId, String text) {
		List<String> names = parse(text);
		KhoaConfig.put(SECTION, featureId, String.join(" ", names));
		BOUND.values().removeIf(featureId::equals);
		for (String name : names) {
			BOUND.put(name, featureId);
		}

		CommandDispatcher<FabricClientCommandSource> dispatcher = ClientCommands.getActiveDispatcher();
		if (dispatcher == null) {
			return null; // not in a world yet: they are created when the commands are registered
		}
		if (dispatcher != liveDispatcher) {
			liveDispatcher = dispatcher;
			REGISTERED.clear();
		}
		List<String> problems = new ArrayList<>();
		for (String name : names) {
			String problem = register(dispatcher, name, featureId);
			if (problem != null) {
				problems.add("/" + name + ": " + problem);
				BOUND.remove(name, featureId);
			}
		}
		try {
			ClientCommands.refreshCommandCompletions();
		} catch (RuntimeException e) {
			KhoaTools.LOGGER.warn("Could not refresh command completions", e);
		}
		return problems.isEmpty() ? null : "Command bind: " + String.join("; ", problems);
	}

	/** Creates /name as a copy of /khoatoolsclient featureId (same command, same argument nodes). Null = ok, else the reason. */
	private static String register(CommandDispatcher<FabricClientCommandSource> dispatcher, String name, String featureId) {
		CommandNode<FabricClientCommandSource> root = dispatcher.getRoot().getChild(ROOT);
		CommandNode<FabricClientCommandSource> target = root == null ? null : root.getChild(featureId);
		if (target == null) {
			return "this feature has no /" + ROOT + " " + featureId + " command to point to";
		}
		CommandNode<FabricClientCommandSource> existing = dispatcher.getRoot().getChild(name);
		if (existing != null) {
			String owner = REGISTERED.get(name);
			if (owner == null) {
				return "that name is already a command";
			}
			return owner.equals(featureId) ? null : "was bound to another feature, rejoin the world to move it";
		}

		var builder = ClientCommands.literal(name)
			.requires(source -> featureId.equals(BOUND.get(name)) && target.canUse(source));
		if (target.getCommand() != null) {
			builder.executes(target.getCommand());
		}
		LiteralCommandNode<FabricClientCommandSource> node = builder.build();
		for (CommandNode<FabricClientCommandSource> child : target.getChildren()) {
			node.addChild(child); // the very same argument nodes: same arguments, same suggestions, same code
		}
		dispatcher.getRoot().addChild(node);
		REGISTERED.put(name, featureId);
		return null;
	}

	/** Shown in chat by the config screen. */
	public static void tell(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (message != null) {
			KhoaFeature.say(mc, message);
		}
	}
}
