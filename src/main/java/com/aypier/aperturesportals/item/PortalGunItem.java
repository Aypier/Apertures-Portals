package com.aypier.aperturesportals.item;

import com.aypier.aperturesportals.block.PortalBlock;
import com.aypier.aperturesportals.AperturesPortals;
import com.aypier.aperturesportals.registry.ModDataComponents;
import com.aypier.aperturesportals.portal.PortalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PortalGunItem extends Item {
    public static final int COLOR_DEFAULT = 0;
    public static final int COLOR_BLUE = 1;
    public static final int COLOR_ORANGE = 2;

    // Portal games let you fire from well beyond normal block-interact range, so we do our own raycast
    // rather than relying on Minecraft's short-range useOn() targeting.
    private static final double MAX_RANGE = 32.0;

    public PortalGunItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return handleUse(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return handleUse(context.getLevel(), player, context.getHand()).getResult();
    }

    private InteractionResultHolder<ItemStack> handleUse(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int color = player.isShiftKeyDown() ? COLOR_ORANGE : COLOR_BLUE;

        if (!level.isClientSide) {
            stack.set(ModDataComponents.PORTAL_GUN_COLOR.get(), color);
            firePortal(level, player, color);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void firePortal(Level level, Player player, int color) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(MAX_RANGE));

        ClipContext clipContext = new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(clipContext);

        if (hit.getType() != HitResult.Type.BLOCK) {
            return; // nothing solid within range
        }

        BlockPos hitPos = hit.getBlockPos();
        Direction face = hit.getDirection();

        // Walls extend the portal upward (world UP). Floors/ceilings can't extend "up" - the model
        // is laid flat there instead, so it extends horizontally, snapped to whichever way the
        // player is currently facing.
        boolean onFloorOrCeiling = face.getAxis().isVertical();
        Direction extendDir = onFloorOrCeiling ? player.getDirection() : Direction.UP;
        Direction rotation = onFloorOrCeiling ? player.getDirection() : Direction.NORTH; // NORTH = unused placeholder for walls

        BlockPos secondHitPos = hitPos.relative(extendDir);
        if (!isValidPortalSurface(level, hitPos, face) || !isValidPortalSurface(level, secondHitPos, face)) {
            return; // wall doesn't extend far enough to back the full model
        }

        BlockPos placePos = hitPos.relative(face);
        BlockPos clearancePos = placePos.relative(extendDir);
        if (!level.getBlockState(placePos).canBeReplaced() || !level.getBlockState(clearancePos).canBeReplaced()) {
            return; // not enough open space for the model to render into
        }

        Block portalBlock = (color == COLOR_ORANGE) ? AperturesPortals.PORTAL_ORANGE.get() : AperturesPortals.PORTAL_BLUE.get();
        BlockState lowerState = portalBlock.defaultBlockState()
                .setValue(PortalBlock.FACING, face)
                .setValue(PortalBlock.ROTATION, rotation)
                .setValue(PortalBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = lowerState.setValue(PortalBlock.HALF, DoubleBlockHalf.UPPER);

        if (level instanceof ServerLevel serverLevel) {
            PortalSavedData data = PortalSavedData.get(serverLevel);
            PortalSavedData.PortalEntry previous = data.get(color);
            if (previous != null) {
                // Clear both cells of the old portal, not just the anchor - it's a 2-block model.
                level.setBlockAndUpdate(previous.pos(), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(previous.pos().relative(previous.extendDirection()), Blocks.AIR.defaultBlockState());
            }
            // Place both cells of the new portal, each with its own half of the model/texture.
            level.setBlockAndUpdate(placePos, lowerState);
            level.setBlockAndUpdate(clearancePos, upperState);
            data.set(color, new PortalSavedData.PortalEntry(placePos, face, rotation));
        }
    }

    private boolean isValidPortalSurface(Level level, BlockPos pos, Direction face) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, face);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 0;
    }
}