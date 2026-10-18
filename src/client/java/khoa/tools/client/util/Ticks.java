package khoa.tools.client.util;

/** Seconds to ticks. */
public final class Ticks {
	private Ticks() {
	}

	/** ceil(seconds * 20); the tiny epsilon stops 0.1 * 20 = 2.0000000000000004 from becoming 3. */
	public static int ofSeconds(double seconds) {
		return seconds <= 0 ? 0 : (int) Math.ceil(seconds * 20 - 1e-9);
	}
}
