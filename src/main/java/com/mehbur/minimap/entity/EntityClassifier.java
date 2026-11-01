package com.mehbur.minimap.entity;

import com.mehbur.minimap.mixin.AbstractArrowAccessor;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

/** Sorts entities into dot types using the game's own classes, with fallbacks for mobs added by other mods. */
public final class EntityClassifier {
	/** Spiders only attack below this light level, where the game's spider AI sees a brightness under 0.5. */
	private static final int SPIDER_CALM_LIGHT_LEVEL = 12;
	/** Modded bosses often carry no tag. No hostile vanilla mob other than the listed bosses reaches this health. */
	private static final float MODDED_BOSS_MIN_HEALTH = 150f;

	private EntityClassifier() {
	}

	/** Returns the dot type, or null for entities that get no dot, such as armor stands, boats and arrows stuck in a block. */
	public static EntityDotType classify(Entity entity, Player viewer) {
		if (entity instanceof Player) {
			return EntityDotType.OTHER_PLAYER;
		}
		if (entity instanceof ItemEntity) {
			return EntityDotType.ITEM;
		}
		if (entity instanceof ThrownEnderpearl || entity instanceof EyeOfEnder) {
			return EntityDotType.ENDER_PEARL;
		}
		if (entity instanceof AbstractArrow) {
			return ((AbstractArrowAccessor) entity).mehburs_minimap$isInGround() ? null : EntityDotType.ARROW;
		}
		if (!(entity instanceof Mob mob)) {
			return null;
		}
		if (isBoss(mob)) {
			return EntityDotType.BOSS;
		}
		if (mob instanceof Spider) {
			BlockPos eye = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
			return mob.level().getMaxLocalRawBrightness(eye) >= SPIDER_CALM_LIGHT_LEVEL ? EntityDotType.NEUTRAL : EntityDotType.HOSTILE;
		}
		if (mob instanceof Piglin) {
			return PiglinAi.isWearingSafeArmor(viewer) ? EntityDotType.NEUTRAL : EntityDotType.HOSTILE;
		}
		// Neutral comes before hostile because endermen and zombified piglins are both.
		if (mob instanceof NeutralMob) {
			return EntityDotType.NEUTRAL;
		}
		if (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER) {
			return EntityDotType.HOSTILE;
		}
		return EntityDotType.PASSIVE;
	}

	private static boolean isBoss(Mob mob) {
		return mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden || mob instanceof ElderGuardian
			|| mob.typeHolder().is(ConventionalEntityTypeTags.BOSSES)
			|| mob instanceof Enemy && mob.getMaxHealth() >= MODDED_BOSS_MIN_HEALTH;
	}
}
