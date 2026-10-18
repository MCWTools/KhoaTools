package khoa.tools.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import java.util.concurrent.atomic.AtomicLong;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import khoa.tools.KhoaTools;

/**
 * /khoatools optimizetnt            = toggle on/off (the player who ran it sees the result).
 * /khoatools optimizetnt &lt;raymultiplier&gt; = 0.05 - 1.00, turns it on and uses that fraction of the normal explosion rays
 * (0.5 = half the rays). Only explosions caused by primed TNT are affected. Fewer rays = less server work and less
 * accurate block damage. State is saved in config/khoatools-server.properties.
 */
public final class OptimizeTntFeature implements KhoaServerFeature {
	private static boolean enabled;
	private static double multiplier = 0.5;
	private static boolean tntExposure;
	private static boolean loaded;

	// timing of primed TNT explosions since the last "stats" (nanoseconds)
	private static final AtomicLong EXPLOSIONS = new AtomicLong();
	private static final AtomicLong TOTAL_NS = new AtomicLong();
	private static final AtomicLong RAYS_NS = new AtomicLong();
	private static final AtomicLong ENTITIES_NS = new AtomicLong();
	private static final AtomicLong OPTIMIZED = new AtomicLong();

	public static void addExplosion(long totalNs) {
		EXPLOSIONS.incrementAndGet();
		TOTAL_NS.addAndGet(totalNs);
	}

	public static void addRays(long ns) {
		RAYS_NS.addAndGet(ns);
	}

	public static void addEntities(long ns) {
		ENTITIES_NS.addAndGet(ns);
	}

	public static void addOptimized() {
		OPTIMIZED.incrementAndGet();
	}

	/** True = TNT entities pushed by an explosion use 1 exposure ray instead of 27 (breaks TNT cannon accuracy). */
	public static synchronized boolean tntExposure() {
		load();
		return enabled && tntExposure;
	}

	private static String takeStats() {
		long n = EXPLOSIONS.getAndSet(0);
		long total = TOTAL_NS.getAndSet(0);
		long rays = RAYS_NS.getAndSet(0);
		long ent = ENTITIES_NS.getAndSet(0);
		long opt = OPTIMIZED.getAndSet(0);
		if (n == 0) {
			return "Optimize TNT stats: no primed TNT explosion since the last call.";
		}
		double ms = total / 1_000_000.0;
		double raysMs = rays / 1_000_000.0;
		double entMs = ent / 1_000_000.0;
		return String.format(java.util.Locale.ROOT,
			"Optimize TNT stats: %d TNT explosions (%d used fewer rays), %.1f ms total on the server: rays %.1f ms, entities %.1f ms, blocks/other %.1f ms. Average %.3f ms per explosion.",
			n, opt, ms, raysMs, entMs, Math.max(0.0, ms - raysMs - entMs), ms / n);
	}

	public static synchronized boolean enabled() {
		load();
		return enabled;
	}

	public static synchronized double multiplier() {
		load();
		return multiplier;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("khoatools-server.properties");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (InputStream in = Files.newInputStream(file)) {
			Properties p = new Properties();
			p.load(in);
			enabled = Boolean.parseBoolean(p.getProperty("optimizetnt.enabled", "false"));
			multiplier = Math.max(0.05, Math.min(1.0, Double.parseDouble(p.getProperty("optimizetnt.raymultiplier", "0.5"))));
			tntExposure = Boolean.parseBoolean(p.getProperty("optimizetnt.tntexposure", "false"));
		} catch (IOException | RuntimeException e) {
			KhoaTools.LOGGER.error("Could not read khoatools-server.properties, using defaults", e);
		}
	}

	private static synchronized void save() {
		Properties p = new Properties();
		p.setProperty("optimizetnt.enabled", Boolean.toString(enabled));
		p.setProperty("optimizetnt.raymultiplier", Double.toString(multiplier));
		p.setProperty("optimizetnt.tntexposure", Boolean.toString(tntExposure));
		try {
			Files.createDirectories(file().getParent());
			try (OutputStream out = Files.newOutputStream(file())) {
				p.store(out, "KhoaTools server features");
			}
		} catch (IOException e) {
			KhoaTools.LOGGER.error("Could not save khoatools-server.properties", e);
		}
	}

	private static String status() {
		return "Optimize TNT: " + (enabled ? "on" : "off") + " (ray multiplier " + String.format(java.util.Locale.ROOT, "%.2f", multiplier) + (tntExposure ? ", cheap TNT exposure" : "") + ")";
	}

	@Override
	public String id() {
		return "optimizetnt";
	}

	@Override
	public String displayName() {
		return "Optimize TNT";
	}

	@Override
	public String description() {
		return "Primed TNT explosions use only a fraction of their rays. Less lag, less accurate block damage.";
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
		root.then(Commands.literal(id())
			.executes(ctx -> {
				synchronized (OptimizeTntFeature.class) {
					load();
					enabled = !enabled;
					save();
				}
				ctx.getSource().sendSuccess(() -> Component.literal(status()), false);
				return 1;
			})
			.then(Commands.literal("stats").executes(ctx -> {
				ctx.getSource().sendSuccess(() -> Component.literal(takeStats()), false);
				return 1;
			}))
			.then(Commands.literal("tntexposure").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
				synchronized (OptimizeTntFeature.class) {
					load();
					tntExposure = BoolArgumentType.getBool(ctx, "value");
					save();
				}
				ctx.getSource().sendSuccess(() -> Component.literal(status() + (tntExposure ? "" : " - exact TNT exposure")), false);
				return 1;
			})))
			.then(Commands.argument("raymultiplier", DoubleArgumentType.doubleArg(0.05, 1.00))
				.executes(ctx -> {
					synchronized (OptimizeTntFeature.class) {
						load();
						multiplier = DoubleArgumentType.getDouble(ctx, "raymultiplier");
						enabled = true;
						save();
					}
					ctx.getSource().sendSuccess(() -> Component.literal(status()), false);
					return 1;
				})));
	}
}
