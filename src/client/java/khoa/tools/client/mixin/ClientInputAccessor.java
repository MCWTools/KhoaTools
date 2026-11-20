package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
	@Accessor("moveVector")
	void khoatools$setMoveVector(Vec2 moveVector);
}
