package com.rof.pearlcannon.mixin;

import com.rof.pearlcannon.calibration.PearlCalibration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 投掷物标定采样：珍珠和雪球共用，在投掷物自身 tick 的边界记录动量。
 * <p>不修改投掷物运动；只在到达基准 tick 末时清理标定投掷物。</p>
 */
@Mixin(value = ThrowableProjectile.class, priority = 900)
public abstract class ThrowableProjectileCalibrationMixin
{
    @Unique
    private boolean rofCalibrationChecked;

    @Unique
    private int rofCalibrationCheckTicks;

    @Inject(method = "tick", at = @At("HEAD"))
    private void rofCalibrationTickHead(CallbackInfo ci)
    {
        ThrowableProjectile projectile = (ThrowableProjectile) (Object) this;
        if (!(projectile.level() instanceof ServerLevel)) return;

        if (!PearlCalibration.hasTrace(projectile))
        {
            if (rofCalibrationChecked) return;

            if (PearlCalibration.begin(projectile) == null)
            {
                // 名称可能在生成后几 tick 才写入（例如 /data merge）；已有名称仍不匹配就不再重试
                if (PearlCalibration.hasName(projectile) || ++rofCalibrationCheckTicks > 5)
                {
                    rofCalibrationChecked = true;
                }
                return;
            }
        }

        PearlCalibration.tickHead(projectile, projectile.position(), projectile.getDeltaMovement());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rofCalibrationTickReturn(CallbackInfo ci)
    {
        ThrowableProjectile projectile = (ThrowableProjectile) (Object) this;
        if (PearlCalibration.tickEnd(projectile, projectile.getDeltaMovement()) && !projectile.isRemoved())
        {
            projectile.discard();
        }
    }
}
