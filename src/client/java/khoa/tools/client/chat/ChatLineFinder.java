package khoa.tools.client.chat;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/** Fed to ChatComponent.captureClickableText: remembers the chat line drawn under a given mouse Y. */
public final class ChatLineFinder implements ActiveTextCollector {
	private static final int LINE_HEIGHT = 9;

	private final int mouseY;
	private Parameters parameters = new Parameters(new Matrix3x2f());
	private FormattedCharSequence result;

	public ChatLineFinder(int mouseY) {
		this.mouseY = mouseY;
	}

	public FormattedCharSequence result() {
		return result;
	}

	@Override
	public Parameters defaultParameters() {
		return parameters;
	}

	@Override
	public void defaultParameters(Parameters parameters) {
		this.parameters = parameters;
	}

	@Override
	public void accept(TextAlignment alignment, int x, int y, Parameters parameters, FormattedCharSequence text) {
		// chat is drawn scaled and offset through the pose, the mouse is in screen coordinates
		Vector2f top = parameters.pose().transformPosition(x, y, new Vector2f());
		Vector2f bottom = parameters.pose().transformPosition(x, y + LINE_HEIGHT, new Vector2f());
		if (mouseY >= top.y && mouseY < bottom.y) {
			result = text;
		}
	}

	@Override
	public void acceptScrolling(Component text, int centerX, int left, int right, int top, int bottom, Parameters parameters) {
	}
}
