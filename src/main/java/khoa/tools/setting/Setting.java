package khoa.tools.setting;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One option of a feature, shown as a row in the feature's config screen. Kinds:
 * BOOL (tick box), INT (number box, digits only), DECIMAL (number box with a dot), STRING (text box),
 * TEXT (multi-line text box, uses the string getter/setter), SLIDER (int slider), CHOICE (button that cycles through names), PAIR (two decimal boxes), ACTION (button).
 * The getter/setter decide where it is stored (usually config/khoatools.json).
 */
public final class Setting {
	public enum Type {
		BOOL, INT, STRING, ACTION, DECIMAL, CHOICE, SLIDER, PAIR, TEXT
	}

	public final String key;
	public final String label;
	public final String description;
	public final Type type;
	public int min;
	public int max;
	public double dmin;
	public double dmax;
	public List<String> options = List.of();
	public String labelA = "";
	public String labelB = "";

	private BooleanSupplier boolGet;
	private Consumer<Boolean> boolSet;
	private IntSupplier intGet;
	private IntConsumer intSet;
	private Supplier<String> stringGet;
	private Consumer<String> stringSet;
	private DoubleSupplier decGet;
	private DoubleConsumer decSet;
	private DoubleSupplier decGet2;
	private DoubleConsumer decSet2;
	private Runnable action;

	private Setting(String key, String label, String description, Type type) {
		this.key = key;
		this.label = label;
		this.description = description;
		this.type = type;
	}

	public static Setting bool(String key, String label, String description, BooleanSupplier get, Consumer<Boolean> set) {
		Setting s = new Setting(key, label, description, Type.BOOL);
		s.boolGet = get;
		s.boolSet = set;
		return s;
	}

	public static Setting integer(String key, String label, String description, int min, int max, IntSupplier get, IntConsumer set) {
		return intBased(Type.INT, key, label, description, min, max, get, set);
	}

	/** Same as {@link #integer} but drawn as a slider (min..max should be a small range). */
	public static Setting slider(String key, String label, String description, int min, int max, IntSupplier get, IntConsumer set) {
		return intBased(Type.SLIDER, key, label, description, min, max, get, set);
	}

	private static Setting intBased(Type type, String key, String label, String description, int min, int max, IntSupplier get, IntConsumer set) {
		Setting s = new Setting(key, label, description, type);
		s.min = min;
		s.max = max;
		s.intGet = get;
		s.intSet = set;
		return s;
	}

	public static Setting string(String key, String label, String description, Supplier<String> get, Consumer<String> set) {
		Setting s = new Setting(key, label, description, Type.STRING);
		s.stringGet = get;
		s.stringSet = set;
		return s;
	}

	/** Big multi-line text box under the title (Enter = new line). */
	public static Setting text(String key, String label, String description, Supplier<String> get, Consumer<String> set) {
		Setting s = new Setting(key, label, description, Type.TEXT);
		s.stringGet = get;
		s.stringSet = set;
		return s;
	}

	/** Number box that accepts a dot (negative sign only if min &lt; 0). Value is clamped to [min, max]. */
	public static Setting decimal(String key, String label, String description, double min, double max, DoubleSupplier get, DoubleConsumer set) {
		Setting s = new Setting(key, label, description, Type.DECIMAL);
		s.dmin = min;
		s.dmax = max;
		s.decGet = get;
		s.decSet = set;
		return s;
	}

	/** Two number boxes side by side (e.g. Yaw / Pitch). No clamp here: the setters should normalize. */
	public static Setting pair(String key, String label, String description, String labelA, DoubleSupplier getA, DoubleConsumer setA,
		String labelB, DoubleSupplier getB, DoubleConsumer setB) {
		Setting s = new Setting(key, label, description, Type.PAIR);
		s.dmin = -1_000_000;
		s.dmax = 1_000_000;
		s.labelA = labelA;
		s.labelB = labelB;
		s.decGet = getA;
		s.decSet = setA;
		s.decGet2 = getB;
		s.decSet2 = setB;
		return s;
	}

	/** Button that cycles through {@code options}; the stored value is the index. */
	public static Setting choice(String key, String label, String description, List<String> options, IntSupplier get, IntConsumer set) {
		Setting s = new Setting(key, label, description, Type.CHOICE);
		s.options = options;
		s.intGet = get;
		s.intSet = set;
		return s;
	}

	/** A button row: {@code label} is the row title, {@code buttonText} the text on the button. */
	public static Setting action(String key, String label, String description, String buttonText, Runnable run) {
		Setting s = new Setting(key, label, description, Type.ACTION);
		s.stringGet = () -> buttonText;
		s.action = run;
		return s;
	}

	public void run() {
		if (action != null) {
			action.run();
		}
	}

	public boolean getBool() {
		return boolGet.getAsBoolean();
	}

	public void setBool(boolean value) {
		boolSet.accept(value);
	}

	public int getInt() {
		return intGet.getAsInt();
	}

	/** Clamped to [min, max]. */
	public void setInt(int value) {
		intSet.accept(Math.max(min, Math.min(max, value)));
	}

	public String getString() {
		String s = stringGet.get();
		return s == null ? "" : s;
	}

	public void setString(String value) {
		stringSet.accept(value == null ? "" : value);
	}

	public double getDecimal() {
		return decGet.getAsDouble();
	}

	public void setDecimal(double value) {
		decSet.accept(Math.max(dmin, Math.min(dmax, value)));
	}

	public double getDecimal2() {
		return decGet2.getAsDouble();
	}

	public void setDecimal2(double value) {
		decSet2.accept(Math.max(dmin, Math.min(dmax, value)));
	}

	/** Current index of a CHOICE, always inside the option list. */
	public int getChoice() {
		return Math.floorMod(intGet.getAsInt(), Math.max(1, options.size()));
	}

	public void nextChoice() {
		intSet.accept((getChoice() + 1) % Math.max(1, options.size()));
	}
}
