package util;

import model.Entity;
import model.EntityType;
import model.Vec2Int;

public class StrategyHelper {
    public static boolean isUnit(Entity entity) {
        return entity.getEntityType() == EntityType.RANGED_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT
                || entity.getEntityType() == EntityType.BUILDER_UNIT;
    }

    public static boolean isWarrior(Entity entity) {
        return entity.getEntityType() == EntityType.RANGED_UNIT || entity.getEntityType() == EntityType.MELEE_UNIT;
    }

    public static boolean isBase(Entity entity) {
        return entity.getEntityType() == EntityType.RANGED_BASE || entity.getEntityType() == EntityType.MELEE_BASE
                || entity.getEntityType() == EntityType.BUILDER_BASE;
    }

    public static int getEntitiesCount(Entity[] entities, int playerId, EntityType entityType) {
        int count = 0;
        for (Entity entity : entities) {
            if (entity.getPlayerId() != null && entity.getPlayerId() == playerId && entity.getEntityType() == entityType) {
                count++;
            }
        }
        return count;
    }

    public static boolean isIn(Vec2Int p, int size, int x, int y) {
        return (x >= p.getX() && y >= p.getY() && x < p.getX() + size && y < p.getY() + size);
    }

    public static boolean isIn(Vec2Int p, int size, Vec2Int pos) {
        return isIn(p, size, pos.getX(), pos.getY());
    }

    public static boolean isEqual(Integer a, Integer b) {
        if (a == null && b == null)
            return true;
        return a != null && a.equals(b);
    }
}
