package khoa.tools.client.feature;

import java.util.ArrayList;
import java.util.List;

import khoa.locator.client.LocatorFinderClient;
import khoa.locator.client.LocatorTraceFeature;
import khoa.locator.client.PlayerInDirectionFeature;
import khoa.logoutghost.client.ghost.LogoutGhostFeature;
import khoa.sky.client.SkyChangerFeature;
import khoa.tools.client.feature.afk.AfkModeFeature;
import khoa.tools.client.feature.autoclicker.AutoClickerFeature;
import khoa.tools.client.feature.autoreconnect.AutoReconnectFeature;
import khoa.tools.client.feature.kickmyself.KickMyselfFeature;
import khoa.tools.client.feature.fullbright.FullbrightFeature;
import khoa.tools.client.feature.findleak.FindLeakFeature;
import khoa.tools.client.feature.visiblebarrier.VisibleBarrierFeature;
import khoa.tools.client.feature.betterchat.BetterChatFeature;
import khoa.tools.client.feature.afkalert.AfkAlertFeature;
import khoa.tools.client.feature.chathighlight.ChatHighlightFeature;
import khoa.tools.client.feature.chatsearch.ChatSearchFeature;
import khoa.tools.client.feature.chattranslator.ChatTranslatorFeature;
import khoa.tools.client.feature.downloadchunk.DownloadChunkFeature;
import khoa.tools.client.feature.downloadchunk.LoadDownloadedChunkFeature;
import khoa.tools.client.feature.ipfinder.IpFinderFeature;
import khoa.tools.client.feature.bettertnt.BetterTntFeature;
import khoa.tools.client.feature.optimizefps.OptimizeFpsFeature;
import khoa.tools.client.feature.playlist.PlaylistFeature;
import khoa.tools.client.feature.drawshape.DrawShapeFeature;
import khoa.tools.client.feature.khoacopy.KhoaCopyFeature;
import khoa.tools.client.feature.lightlevel.LightLevelOverlayFeature;
import khoa.tools.client.feature.tnthitray.TntHitRayFeature;
import khoa.tools.client.feature.ruler.RulerFeature;
import khoa.tools.client.feature.itemcounter.ItemCounterFeature;
import khoa.tools.client.feature.sneak.SneakFeature;
import khoa.tools.client.feature.blocklog.BlockLogFeature;
import khoa.tools.client.feature.armormaterial.ArmorMaterialFeature;
import khoa.tools.client.feature.handsize.HandSizeFeature;
import khoa.tools.client.feature.totemtweaks.TotemTweaksFeature;
import khoa.tools.client.feature.inventorytweak.InventoryTweakFeature;
import khoa.tools.client.feature.give.ClientGiveFeature;
import khoa.tools.client.feature.itemcomponentreader.ItemComponentReaderFeature;
import khoa.tools.client.feature.customitemtab.CustomItemTabFeature;
import khoa.tools.client.feature.camera.CameraFeature;

public final class KhoaFeatures {
	private static final List<KhoaFeature> ALL = new ArrayList<>(List.of(
		new DownloadChunkFeature(),
		new LoadDownloadedChunkFeature(),
		new IpFinderFeature(),
		new BetterChatFeature(),
		new LocatorFinderClient(),
		new LocatorTraceFeature(),
		new PlayerInDirectionFeature(),
		new OptimizeFpsFeature(),
		new BetterTntFeature(),
		new LogoutGhostFeature(),
		new SkyChangerFeature(),
		new AutoClickerFeature(),
		new AutoReconnectFeature(),
		new AfkModeFeature(),
		new AfkAlertFeature(),
		new FullbrightFeature(),
		new VisibleBarrierFeature(),
		new FindLeakFeature(),
		new KickMyselfFeature(),
		new PlaylistFeature(),
		new TntHitRayFeature(),
		new ChatTranslatorFeature(),
		new ChatHighlightFeature(),
		new ChatSearchFeature(),
		new RulerFeature(),
		new ItemCounterFeature(),
		new DrawShapeFeature(),
		new LightLevelOverlayFeature(),
		new KhoaCopyFeature(),
		new SneakFeature(),
		new BlockLogFeature(),
		new ArmorMaterialFeature(),
		new HandSizeFeature(),
		new TotemTweaksFeature(),
		new ClientGiveFeature(),
		new ItemComponentReaderFeature(),
		new CustomItemTabFeature(),
		new CameraFeature(),
		new InventoryTweakFeature()
		// new SimulateFeature() (hidden for now)
		// add new features here
	));

	private KhoaFeatures() {
	}

	public static List<KhoaFeature> all() {
		return List.copyOf(ALL);
	}
	public static void register(KhoaFeature feature) {
		if (feature != null && ALL.stream().noneMatch(existing -> existing.id().equals(feature.id()))) ALL.add(feature);
	}
}
