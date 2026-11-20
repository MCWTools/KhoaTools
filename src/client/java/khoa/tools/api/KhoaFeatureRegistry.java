package khoa.tools.api;
import khoa.tools.client.feature.KhoaFeature;
import khoa.tools.client.feature.KhoaFeatures;
public final class KhoaFeatureRegistry {
	public void register(KhoaFeature feature) { KhoaFeatures.register(feature); }
}
