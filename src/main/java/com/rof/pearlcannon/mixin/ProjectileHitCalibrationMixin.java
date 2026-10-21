package com.rof.pearlcannon.mixin;

import com.rof.pearlcannon.calibration.PearlCalibration;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 标定用的珍珠/雪球命中时直接消失，不触发传送或雪球效果。 */
@Mixin(value = { ThrownEnderpearl.class, Snowball.class }, priority = 900)
public abstract class ProjectileHitCalibrationMixin
{
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void rofCalibrationOnHit(HitResult hitResult, CallbackInfo ci)
    {
        if (PearlCalibration.onHit((ThrowableItemProjectile) (Object) this))
        {
            ci.cancel();
        }
    }
}
