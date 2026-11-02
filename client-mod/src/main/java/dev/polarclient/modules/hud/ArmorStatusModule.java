package dev.polarclient.modules.hud;

import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ArmorStatusModule extends HudModule {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final ModeSetting layout = add(new ModeSetting("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
	private final ModeSetting durability = add(new ModeSetting("durability", "Durability", "Percent", "Percent", "Value", "Hidden"));
	private final BooleanSetting mainHand = add(new BooleanSetting("main_hand", "Include held item", true));
	private final BooleanSetting colorCoded = add(new BooleanSetting("color_coded", "Color by durability", true));

	public ArmorStatusModule() {
		super("armor_status", "Armor Status", "Your armor and held item with durability.", true, 440, 100);
	}

	private List<ItemStack> items(boolean preview) {
		List<ItemStack> out = new ArrayList<>();
		Player p = Minecraft.getInstance().player;
		if (p != null) {
			for (EquipmentSlot slot : ARMOR) {
				ItemStack s = p.getItemBySlot(slot);
				if (!s.isEmpty()) out.add(s);
			}
			if (mainHand.on() && !p.getMainHandItem().isEmpty()) out.add(p.getMainHandItem());
		}
		if (out.isEmpty() && preview) {
			out.add(new ItemStack(Items.DIAMOND_HELMET));
			out.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			out.add(new ItemStack(Items.DIAMOND_LEGGINGS));
			out.add(new ItemStack(Items.DIAMOND_BOOTS));
			if (mainHand.on()) out.add(new ItemStack(Items.DIAMOND_SWORD));
		}
		return out;
	}

	@Override
	public boolean hasContent() {
		return !items(false).isEmpty();
	}

	private String label(ItemStack s) {
		if (durability.is("Hidden")) return "";
		if (s.isDamageableItem()) {
			int left = s.getMaxDamage() - s.getDamageValue();
			return durability.is("Percent") ? (left * 100 / Math.max(1, s.getMaxDamage())) + "%" : String.valueOf(left);
		}
		return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
	}

	private int labelColor(ItemStack s) {
		if (!colorCoded.on() || !s.isDamageableItem()) return 0xFFFFFFFF;
		float f = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
		return f > 0.6f ? 0xFF6EE7A0 : f > 0.3f ? 0xFFFFE066 : 0xFFFF6B6B;
	}

	private int cell(boolean preview) {
		int w = 0;
		for (ItemStack s : items(preview)) w = Math.max(w, Draw.font().width(label(s)));
		return 16 + (w > 0 ? 3 + w : 0);
	}

	@Override
	public int width(boolean preview) {
		int n = Math.max(1, items(preview).size());
		return layout.is("Vertical") ? cell(preview) : n * cell(preview) + (n - 1) * 4;
	}

	@Override
	public int height(boolean preview) {
		int n = Math.max(1, items(preview).size());
		return layout.is("Vertical") ? n * 17 : 16;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		int x = 0, y = 0, cell = cell(preview);
		for (ItemStack s : items(preview)) {
			g.item(s, x, y);
			g.itemDecorations(Draw.font(), s, x, y, "");
			String l = label(s);
			if (!l.isEmpty()) g.text(Draw.font(), l, x + 19, y + 4, labelColor(s), true);
			if (layout.is("Vertical")) y += 17;
			else x += cell + 4;
		}
	}
}
