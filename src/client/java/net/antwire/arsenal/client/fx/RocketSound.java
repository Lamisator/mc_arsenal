package net.antwire.arsenal.client.fx;

import net.antwire.arsenal.entity.RocketEntity;
import net.antwire.arsenal.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The roar of a rocket motor, following the rocket until it burns out or hits. */
public class RocketSound extends AbstractTickableSoundInstance {
	private final RocketEntity rocket;

	public RocketSound(RocketEntity rocket) {
		super(ModSounds.ROCKET_LOOP.value(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.rocket = rocket;
		this.looping = true;
		this.delay = 0;
		this.volume = 2.0F;
		this.x = rocket.getX();
		this.y = rocket.getY();
		this.z = rocket.getZ();
	}

	@Override
	public void tick() {
		if (this.rocket.isRemoved()) {
			this.stop();
			return;
		}
		this.x = this.rocket.getX();
		this.y = this.rocket.getY();
		this.z = this.rocket.getZ();
		this.volume = this.rocket.burning() ? 2.0F : 0.5F;
		this.pitch = this.rocket.burning() ? 1.0F : 0.7F;
	}
}
