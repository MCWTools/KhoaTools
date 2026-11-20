package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
	@Accessor("selectedTab")
	static CreativeModeTab khoa$getSelectedTab() {
		throw new AssertionError();
	}

	@Accessor("selectedTab")
	static void khoa$setSelectedTab(CreativeModeTab tab) {
		throw new AssertionError();
	}

	/** Reselects a tab on this screen: reloads the item grid from the tab's current contents. */
	@Invoker("selectTab")
	void khoa$selectTab(CreativeModeTab tab);
}
