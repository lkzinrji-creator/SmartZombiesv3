package com.lkzin.smartzombies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SmartZombieGoal extends Goal {
    private final Zombie mob;
    private Player target;

    private final List<ItemStack> bag = new ArrayList<>();
    private BlockPos breaking;
    private int breakStart = -1;
    private int breakTime = 0;
    private int cooldown = 0;

    public SmartZombieGoal(Zombie mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        target = nearestSurvivalPlayer();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive()
                && !target.isCreative() && !target.isSpectator()
                && mob.distanceToSqr(target) < 48 * 48;
    }

    @Override
    public void stop() {
        target = null;
        breaking = null;
        breakStart = -1;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;

        if (target == null || !target.isAlive()
                || target.isCreative() || target.isSpectator()) {
            target = nearestSurvivalPlayer();
            return;
        }

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        collectItems(level);
        craftBasicTools();
        equipUsefulItem();

        if (cooldown > 0) cooldown--;

        double d2 = mob.distanceToSqr(target);

        if (d2 < 3.0 * 3.0) {
            mob.getNavigation().moveTo(target, 1.15D);
            return;
        }

        if (cooldown > 0) {
            mob.getNavigation().moveTo(target, 1.0D);
            return;
        }

        BlockPos obstacle = obstacleAhead(level);

        if (obstacle != null) {
            if (tryBuild(level)) return;
            mine(level, obstacle);
            return;
        }

        mob.getNavigation().moveTo(target, 1.08D);

        if (d2 > 10 * 10 && tryBuild(level)) {
            cooldown = 8;
        }
    }

    private Player nearestSurvivalPlayer() {
        Player result = null;
        double best = Double.MAX_VALUE;

        for (Player p : mob.level().getEntitiesOfClass(
                Player.class, mob.getBoundingBox().inflate(40),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {

            double d = mob.distanceToSqr(p);
            if (d < best) {
                best = d;
                result = p;
            }
        }
        return result;
    }

    private void collectItems(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class, mob.getBoundingBox().inflate(3.0D))) {

            if (!item.isAlive()) continue;

            ItemStack stack = item.getItem().copy();
            if (put(stack)) item.discard();
        }
    }

    private boolean put(ItemStack incoming) {
        if (incoming.isEmpty()) return true;

        for (ItemStack s : bag) {
            if (ItemStack.isSameItemSameTags(s, incoming)
                    && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(
                        incoming.getCount(),
                        s.getMaxStackSize() - s.getCount()
                );
                s.grow(move);
                incoming.shrink(move);
                if (incoming.isEmpty()) return true;
            }
        }

        while (!incoming.isEmpty() && bag.size() < 27) {
            bag.add(incoming.split(
                    Math.min(incoming.getMaxStackSize(), incoming.getCount())));
        }

        return incoming.isEmpty();
    }

    private int count(Item item) {
        int n = 0;
        for (ItemStack s : bag) if (s.is(item)) n += s.getCount();
        return n;
    }

    private void take(Item item, int amount) {
        Iterator<ItemStack> it = bag.iterator();

        while (it.hasNext() && amount > 0) {
            ItemStack s = it.next();
            if (!s.is(item)) continue;

            int n = Math.min(amount, s.getCount());
            s.shrink(n);
            amount -= n;

            if (s.isEmpty()) it.remove();
        }
    }

    private void craftBasicTools() {
        if (count(Items.OAK_LOG) > 0 && count(Items.OAK_PLANKS) < 4) {
            take(Items.OAK_LOG, 1);
            put(new ItemStack(Items.OAK_PLANKS, 4));
        }

        if (count(Items.OAK_PLANKS) >= 2 && count(Items.STICK) < 4) {
            take(Items.OAK_PLANKS, 2);
            put(new ItemStack(Items.STICK, 4));
        }

        if (count(Items.OAK_PLANKS) >= 3
                && count(Items.STICK) >= 2
                && count(Items.WOODEN_PICKAXE) == 0) {
            take(Items.OAK_PLANKS, 3);
            take(Items.STICK, 2);
            put(new ItemStack(Items.WOODEN_PICKAXE));
        }

        if (count(Items.OAK_PLANKS) >= 4
                && count(Items.CRAFTING_TABLE) == 0) {
            take(Items.OAK_PLANKS, 4);
            put(new ItemStack(Items.CRAFTING_TABLE));
        }
    }

    private void equipUsefulItem() {
        ItemStack best = ItemStack.EMPTY;
        int score = 0;

        for (ItemStack s : bag) {
            int value = 0;

            if (s.getItem() instanceof PickaxeItem) value = 100;
            else if (s.getItem() instanceof AxeItem) value = 90;
            else if (s.getItem() instanceof TieredItem) value = 50;
            else if (s.getItem() instanceof SwordItem) value = 120;

            if (value > score) {
                score = value;
                best = s;
            }
        }

        if (!best.isEmpty() && mob.getMainHandItem().isEmpty()) {
            mob.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    best.copyWithCount(1)
            );
            best.shrink(1);
        }
    }

    private BlockPos obstacleAhead(ServerLevel level) {
        Vec3 delta = target.position().subtract(mob.position());
        Direction dir = Math.abs(delta.x) > Math.abs(delta.z)
                ? (delta.x >= 0 ? Direction.EAST : Direction.WEST)
                : (delta.z >= 0 ? Direction.SOUTH : Direction.NORTH);

        BlockPos base = mob.blockPosition();

        for (int i = 1; i <= 2; i++) {
            BlockPos p = base.relative(dir, i);
            BlockState state = level.getBlockState(p);

            if (!state.isAir() && state.getDestroySpeed(level, p) >= 0
                    && !state.getFluidState().isSource()) {
                return p;
            }
        }

        return null;
    }

    private void mine(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            breaking = null;
            return;
        }

        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(
                pos.getX() + .5D, pos.getY() + .5D,
                pos.getZ() + .5D, 30.0F, 30.0F);

        if (!pos.equals(breaking)) {
            breaking = pos;
            breakStart = mob.tickCount;
            breakTime = miningTicks(state, pos, level);
        }

        int elapsed = mob.tickCount - breakStart;

        if ((mob.tickCount & 1) == 0) {
            int progress = Math.min(9, elapsed * 10 / Math.max(1, breakTime));
            level.destroyBlockProgress(mob.getId(), pos, progress);
        }

        if (elapsed >= breakTime) {
            level.destroyBlockProgress(mob.getId(), pos, -1);

            if (level.destroyBlock(pos, true, mob)) {
                Item item = state.getBlock().asItem();
                if (item != Items.AIR) put(new ItemStack(item));
            }

            breaking = null;
            breakStart = -1;
            cooldown = 8;
        }
    }

    private int miningTicks(BlockState state, BlockPos pos, ServerLevel level) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 999999;

        float speed = Math.max(1.0F,
                mob.getMainHandItem().getDestroySpeed(state));

        return Math.max(6, Math.min(160,
                (int) (hardness * 28.0F / speed)));
    }

    private boolean tryBuild(ServerLevel level) {
        ItemStack block = firstBlock();

        if (block.isEmpty()) return false;

        Vec3 delta = target.position().subtract(mob.position());
        Direction dir = Math.abs(delta.x) > Math.abs(delta.z)
                ? (delta.x >= 0 ? Direction.EAST : Direction.WEST)
                : (delta.z >= 0 ? Direction.SOUTH : Direction.NORTH);

        BlockPos base = mob.blockPosition();
        BlockPos place = base.relative(dir);

        if (!level.getBlockState(place).isAir()) {
            place = base.above();
        }

        if (!level.getBlockState(place).isAir()) return false;

        if (!(block.getItem() instanceof BlockItem bi)) return false;

        level.setBlock(place, bi.getBlock().defaultBlockState(), 3);
        block.shrink(1);
        cooldown = 10;
        return true;
    }

    private ItemStack firstBlock() {
        for (ItemStack s : bag) {
            if (s.getItem() instanceof BlockItem
                    && !s.is(Items.CRAFTING_TABLE)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
