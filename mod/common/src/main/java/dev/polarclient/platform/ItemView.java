package dev.polarclient.platform;

/** An item stack as the HUD sees it; {@code handle} is the version's own ItemStack, for drawing. */
public final class ItemView {
	public final Object handle;
	public final boolean damageable;
	public final int maxDamage;
	public final int damage;
	public final int count;

	public ItemView(Object handle, boolean damageable, int maxDamage, int damage, int count) {
		this.handle = handle;
		this.damageable = damageable;
		this.maxDamage = maxDamage;
		this.damage = damage;
		this.count = count;
	}
}
