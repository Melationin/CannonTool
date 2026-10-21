package com.rof.pearlcannon.mixin;

import com.rof.pearlcannon.calibration.PearlCalibration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 珍珠炮标定采样：在珍珠自身的 tick 边界记录动量，Δv 由上一 tick 末尾与本 tick 开头作差得到。
 * <p>不修改珍珠运动；只在到达基准 tick 末时清理标定珍珠。</p>
 */
@Mixin(value = ThrownEnderpearl.class, priority = 900)
public abstract class ThrownEnderpearlCalibrationMixin extends ThrowableItemProjectile
{
    @Unique
    private PearlCalibration.Trace rofCalibrationTrace;

    @Unique
    private boolean rofCalibrationChecked;

    @Unique
    private int rofCalibrationCheckTicks;

    // 玩家投掷珍珠那一刻的位置，作为配置原点
    @Unique
    private Vec3 rofCalibrationThrowOrigin;

    protected ThrownEnderpearlCalibrationMixin(EntityType<? extends ThrowableItemProjectile> entityType, Level level)
    {
        super(entityType, level);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void rofCalibrationOnConstruct(Level level, LivingEntity owner, ItemStack stack, CallbackInfo ci)
    {
        if (owner != null) this.rofCalibrationThrowOrigin = owner.position();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void rofCalibrationTickHead(CallbackInfo ci)
    {
        if (!(this.level() instanceof ServerLevel)) return;

        if (rofCalibrationTrace == null)
        {
            if (rofCalibrationChecked) return;

            ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
            rofCalibrationTrace = PearlCalibration.begin(pearl, this.rofCalibrationThrowOrigin);
            if (rofCalibrationTrace == null)
            {
                // 名称可能在生成后几 tick 才写入（例如 /data merge）；已有名称仍不匹配就不再重试
                if (PearlCalibration.hasName(pearl) || ++rofCalibrationCheckTicks > 5)
                {
                    rofCalibrationChecked = true;
                }
                return;
            }
        }

        rofCalibrationTrace.onTickHead(this.position(), this.getDeltaMovement());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void rofCalibrationTickReturn(CallbackInfo ci)
    {
        if (rofCalibrationTrace == null) return;
        if (rofCalibrationTrace.onTickEnd(this.getDeltaMovement()))
        {
            ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
            if (!pearl.isRemoved()) pearl.discard();
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void rofCalibrationOnHit(HitResult hitResult, CallbackInfo ci)
    {
        // 标定珍珠命中时直接消失，不把投掷者传送过去
        if (rofCalibrationTrace == null) return;
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        if (pearl.isRemoved()) return;
        pearl.discard();
        ci.cancel();
    }

    @Inject(method = "onRemoval", at = @At("HEAD"))
    private void rofCalibrationOnRemoval(Entity.RemovalReason reason, CallbackInfo ci)
    {
        if (rofCalibrationTrace != null) rofCalibrationTrace.finish();
    }
}
