package khoa.tools.client.util;

import net.minecraft.client.OptionInstance;

import khoa.tools.client.mixin.OptionInstanceAccessor;

public final class OptionUtil {
	private OptionUtil() {
	}

	/** Writes the option value directly, skipping the vanilla range check (gamma &gt; 1, fps 5, simulation distance 2...). */
	public static <T> void force(OptionInstance<T> option, T value) {
		((OptionInstanceAccessor) (Object) option).khoa$setValue(value);
	}
}
