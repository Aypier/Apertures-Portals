package com.aypier.aperturesportals.portal;

import com.aypier.aperturesportals.item.PortalGunItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PortalSavedData extends SavedData {
    private static final String ID = "apertures_portals_portals";

    private PortalEntry blue;
    private PortalEntry orange;

    public static PortalSavedData create() {
        return new PortalSavedData();
    }

    public static PortalSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PortalSavedData data = new PortalSavedData();
        if (tag.contains("blue")) {
            data.blue = PortalEntry.load(tag.getCompound("blue"));
        }
        if (tag.contains("orange")) {
            data.orange = PortalEntry.load(tag.getCompound("orange"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (blue != null) {
            tag.put("blue", blue.save());
        }
        if (orange != null) {
            tag.put("orange", orange.save());
        }
        return tag;
    }

    public static PortalSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PortalSavedData::create, PortalSavedData::load, DataFixTypes.LEVEL),
                ID);
    }

    @Nullable
    public PortalEntry get(int color) {
        return color == PortalGunItem.COLOR_ORANGE ? orange : blue;
    }

    public void set(int color, PortalEntry entry) {
        if (color == PortalGunItem.COLOR_ORANGE) {
            orange = entry;
        } else {
            blue = entry;
        }
        setDirty();
    }

    public record PortalEntry(BlockPos pos, Direction facing, Direction rotation) {
        // For a wall portal (FACING is horizontal), the model always extends straight up.
        // For a floor/ceiling portal (FACING is UP or DOWN), it extends horizontally instead,
        // in whichever direction ROTATION points. Mirrors the logic in PortalGunItem.firePortal.
        public Direction extendDirection() {
            return facing.getAxis().isVertical() ? rotation : Direction.UP;
        }

        // The portal spans two blocks (pos, and one more in extendDirection()), so its true
        // volumetric center sits half a block further along that axis than pos's own center.
        // Used for positioning an entity across the width/height of the portal on exit.
        public Vec3 center() {
            Vec3 lower = Vec3.atCenterOf(pos);
            Direction extend = extendDirection();
            return lower.add(extend.getStepX() * 0.5, extend.getStepY() * 0.5, extend.getStepZ() * 0.5);
        }

        // The point on the actual backing surface (wall/floor/ceiling) that the portal sits on -
        // half a block behind pos's own center, opposite the outward-facing normal. This is the
        // correct plane to measure crossing against (not center(), which sits half a block out
        // into the room and would sit above a standing entity's feet for a floor portal).
        public Vec3 surfacePoint() {
            Vec3 base = Vec3.atCenterOf(pos);
            Vec3 normal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            return base.subtract(normal.scale(0.5));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            tag.putString("facing", facing.getSerializedName());
            tag.putString("rotation", rotation.getSerializedName());
            return tag;
        }

        public static PortalEntry load(CompoundTag tag) {
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            Direction facing = Direction.byName(tag.getString("facing"));
            Direction rotation = Direction.byName(tag.getString("rotation"));
            return new PortalEntry(pos, facing, rotation);
        }
    }
}