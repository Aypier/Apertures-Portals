package com.aypier.aperturesportals.block;

import com.aypier.aperturesportals.item.PortalGunItem;
import com.aypier.aperturesportals.portal.PortalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PortalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // Only meaningful when FACING is UP or DOWN: a wall portal's horizontal orientation is already
    // fully determined by FACING itself, but a floor/ceiling portal needs a second axis to know
    // which way it's "pointing".
    public static final DirectionProperty ROTATION = DirectionProperty.create("rotation", Direction.Plane.HORIZONTAL);

    // Which of the portal's two cells this state represents. For walls the two cells really are
    // stacked lower/upper; for floor/ceiling portals they sit side by side instead - either way,
    // this lets each cell use a different half of the portal texture instead of both cells
    // independently rendering the full thing (which is what caused the "doubled" look).
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    // How far past the exit portal's surface to push an entity, so they land clear of the block
    // that would otherwise immediately re-trigger this same method.
    private static final double EXIT_PUSH = 0.6;

    // Small dead zone around the portal's surface plane, so floating-point jitter while resting
    // exactly on the boundary can't register as a spurious crossing.
    private static final double PLANE_EPSILON = 0.02;

    // Per-entity signed distance from this portal's surface plane, measured on the last tick they
    // were observed overlapping this block. Used to detect the moment an entity actually crosses
    // through, rather than firing on every tick they happen to be standing inside the block's
    // (collision-less) volume.
    private final Map<UUID, Double> lastSignedDistance = new HashMap<>();

    private final int color;

    public PortalBlock(Properties properties, int color) {
        super(properties);
        this.color = color;
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROTATION, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        PortalSavedData data = PortalSavedData.get(serverLevel);
        PortalSavedData.PortalEntry from = data.get(color);
        if (from == null) {
            return; // this portal isn't linked yet - only one color has been placed
        }

        Vec3 n1 = basisNormal(from.facing());
        double signedDist = entity.position().subtract(from.surfacePoint()).dot(n1);
        Double last = lastSignedDistance.put(entity.getUUID(), signedDist);

        // Trigger either when the entity crosses from clearly in front of the surface to
        // clearly behind it (walking or falling through normally), or, if this is the very
        // first tick we've seen them here, when they're already at or past the surface - this
        // covers walking straight onto a floor/ceiling portal with no vertical motion at all,
        // e.g. stepping onto it from level ground, rather than only working when falling in.
        boolean crossed = last != null && last > PLANE_EPSILON && signedDist <= PLANE_EPSILON;
        boolean arrivedInside = last == null && signedDist <= PLANE_EPSILON;
        if (!crossed && !arrivedInside) {
            return;
        }

        int otherColor = (color == PortalGunItem.COLOR_ORANGE) ? PortalGunItem.COLOR_BLUE : PortalGunItem.COLOR_ORANGE;
        PortalSavedData.PortalEntry to = data.get(otherColor);
        if (to == null) {
            return; // not linked yet - only this color has been placed
        }

        teleport(entity, from, to);
        lastSignedDistance.remove(entity.getUUID());
    }

    private static Vec3 basisNormal(Direction facing) {
        return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private static Vec3 basisUp(Direction facing, Direction rotation) {
        // Walls: "up" in the portal's local frame is always world-up.
        // Floors/ceilings: the model is laid flat, so its local "up" is a horizontal
        // direction instead - whichever way ROTATION points.
        return facing.getAxis().isVertical()
                ? new Vec3(rotation.getStepX(), rotation.getStepY(), rotation.getStepZ())
                : new Vec3(0, 1, 0);
    }

    // Converts a normalized look-direction vector back into yaw/pitch, using the exact inverse
    // of Entity.calculateViewVector - so this stays consistent with vanilla's own convention.
    private static float lookToYaw(Vec3 look) {
        return (float) Math.toDegrees(Math.atan2(-look.x, look.z));
    }

    private static float lookToPitch(Vec3 look) {
        return (float) Math.toDegrees(-Math.asin(Mth.clamp(look.y, -1.0, 1.0)));
    }

    private static void teleport(Entity entity, PortalSavedData.PortalEntry from, PortalSavedData.PortalEntry to) {
        Vec3 n1 = basisNormal(from.facing());
        Vec3 u1 = basisUp(from.facing(), from.rotation());
        Vec3 r1 = u1.cross(n1).normalize();

        Vec3 n2 = basisNormal(to.facing());
        Vec3 u2 = basisUp(to.facing(), to.rotation());
        Vec3 r2 = u2.cross(n2).normalize();

        Vec3 center1 = from.center();
        Vec3 center2 = to.center();

        // Position
        Vec3 relPos = entity.position().subtract(center1);
        double r = relPos.dot(r1);
        double u = relPos.dot(u1);
        double n = relPos.dot(n1);
        // The normal component flips: entering means moving against the entry portal's
        // outward normal, so exiting should move WITH the exit portal's outward normal.
        Vec3 newRelPos = r2.scale(r).add(u2.scale(u)).add(n2.scale(-n + EXIT_PUSH));
        Vec3 newPos = center2.add(newRelPos);

        // Velocity - same basis transform as position, applied to the movement vector.
        Vec3 vel = entity.getDeltaMovement();
        double vr = vel.dot(r1);
        double vu = vel.dot(u1);
        double vn = vel.dot(n1);
        Vec3 newVel = r2.scale(vr).add(u2.scale(vu)).add(n2.scale(-vn));

        // Look direction - same basis transform again, so the camera turns to match exactly
        // how position and velocity were redirected.
        Vec3 look = entity.getLookAngle();
        double lr = look.dot(r1);
        double lu = look.dot(u1);
        double ln = look.dot(n1);
        Vec3 newLook = r2.scale(lr).add(u2.scale(lu)).add(n2.scale(-ln)).normalize();
        float newYaw = lookToYaw(newLook);
        float newPitch = lookToPitch(newLook);

        if (entity instanceof ServerPlayer serverPlayer) {
            // Players drive their own camera client-side, so a plain position teleport won't
            // turn their view - this explicitly tells the client to snap to the given yaw/pitch
            // along with the new position.
            serverPlayer.connection.teleport(newPos.x, newPos.y, newPos.z, newYaw, newPitch, Set.of());
        } else {
            entity.teleportTo(newPos.x, newPos.y, newPos.z);
            entity.setYRot(newYaw);
            entity.setXRot(newPitch);
            entity.setYHeadRot(newYaw);
            entity.yRotO = newYaw;
            entity.xRotO = newPitch;
        }

        entity.setDeltaMovement(newVel);
        entity.hurtMarked = true; // forces the velocity change to sync to clients
    }
}