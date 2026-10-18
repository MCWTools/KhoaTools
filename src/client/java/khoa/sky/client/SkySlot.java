package khoa.sky.client;

/**
 * One customisable thing. Each slot maps to a sub-folder:
 * <pre>.minecraft/skyboxes/&lt;dimension&gt;/&lt;kind&gt;/*.png</pre>
 */
public enum SkySlot {
	OVERWORLD_SKYBOX("overworld", "skybox", "Skybox"),
	OVERWORLD_SUN("overworld", "sun", "Sun"),
	OVERWORLD_MOON("overworld", "moon", "Moon"),
	OVERWORLD_CLOUDS("overworld", "clouds", "Clouds"),
	END_SKYBOX("the_end", "skybox", "Skybox");

	public final String dimensionDir;
	public final String kindDir;
	public final String label;

	SkySlot(String dimensionDir, String kindDir, String label) {
		this.dimensionDir = dimensionDir;
		this.kindDir = kindDir;
		this.label = label;
	}

	/** Key used inside config/skychanger.json. */
	public String key() {
		return dimensionDir + "/" + kindDir;
	}
}
