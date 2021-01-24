import model.*;
import util.QueueHelper;

import java.util.*;

import static util.StrategyHelper.*;

public class MyStrategy {
    private Random rand = new Random(System.currentTimeMillis());

    private boolean isInitialized = false;
    private int myId, mapSize, tickNum = 0, populationUse, populationProvide, resource, reservedResource = 0;
    private MyVec2Int myLoc, lb, lt, rb, rt, ct, myDirection, attackAngle;


    private double populationProps[] = new double[] {0, 0.9, 0.1};
    private double earlyProps[] = new double[] {0, 0, 1};
    private double basesResourceProps = 0.6;
    private int REPAIR_INTENSITY = 5;
    private double BUILDER_REPAIR = 0.4;
    private double BUILDER_RESOURCES_FROM_WITHOUT_REPAIR = 0.7;
    private double DEFENCE_RATE = 0.25;
    private double DEFENCE_RADIUS = 0.33;
    private double TURRET_RADIUS = 0.35;
    private int TURRET_TICK = 80;
    private int MAX_TURRETS_COUNT = 20;

    EntityType[] builderTargets = new EntityType[]{EntityType.RESOURCE};
    EntityType[] otherTargets = new EntityType[]{EntityType.MELEE_UNIT, EntityType.BUILDER_UNIT, EntityType.RANGED_UNIT,
        EntityType.MELEE_BASE, EntityType.RANGED_BASE, EntityType.BUILDER_BASE, EntityType.TURRET, EntityType.WALL, EntityType.HOUSE};
    private Map<EntityType, Integer> myEntitiesCount = new HashMap<>();
    private Map<EntityType, Integer> myActiveEntitiesCount = new HashMap<>();
    private List<Entity> playerEntities = new ArrayList<>();
    private Map<Integer, WarriorRole> warriorRoles = new HashMap<>();
    private Map<Integer, EntityAction> warriorActions = new HashMap<>();
    private Map<Integer, BuildingTask> buildingTasks = new HashMap<>();
    private int[][] map, marks;
    private EntityType[][] entityTypesMap;
    private final int EMPTY = 1234567;

    private boolean resourcesEnough;
    private boolean populationEnough;

    private Throwable lastThrowable;

    private QueueHelper queueHelper;

    private long timeOfWork = 0;
    private long timeOfBuilders = 0;
    private long timeOfWarriors = 0;
    private long timeOfBases = 0;

    public Action getAction(PlayerView playerView, DebugInterface debugInterface) {
        long startTime = System.currentTimeMillis();
        tickNum = playerView.getCurrentTick();
        System.out.println(tickNum);
        Map<Integer, EntityAction> entityActions = new HashMap<>();
        Action action = new Action(entityActions);
        try {
            if (!isInitialized) {
                initialize(playerView);
            }
            //noinspection OptionalGetWithoutIsPresent
            Player me = Arrays.stream(playerView.getPlayers()).filter((p) -> p.getId() == myId).findFirst().get();

            for (int i = 0; i < mapSize; i++)
                for (int j = 0; j < mapSize; j++)
                    map[i][j] = EMPTY;

            myEntitiesCount.clear();
            myActiveEntitiesCount.clear();
            populationUse = 0;
            populationProvide = 0;
            playerEntities.clear();
            for (Entity entity : playerView.getEntities()) {
                if (entity.getEntityType() != EntityType.RESOURCE) {
                    playerEntities.add(entity);
                }
                if (isEqual(entity.getPlayerId(), myId)) {
                    Integer count = myEntitiesCount.get(entity.getEntityType());
                    if (count == null)
                        count = 1;
                    else
                        count++;
                    myEntitiesCount.put(entity.getEntityType(), count);

                    count = myActiveEntitiesCount.get(entity.getEntityType());
                    if (count == null)
                        count = 0;
                    if (entity.isActive())
                        count++;
                    myActiveEntitiesCount.put(entity.getEntityType(), count);

                    populationUse += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationUse();
                    if (entity.isActive()) {
                        populationProvide += playerView.getEntityProperties().get(entity.getEntityType()).getPopulationProvide();
                    }
                }
                Vec2Int pos = entity.getPosition();
                int size = playerView.getEntityProperties().get(entity.getEntityType()).getSize();
                for (int i = pos.getX(); i < pos.getX() + size; i++)
                    for (int j = pos.getY(); j < pos.getY() + size; j++) {
                        map[i][j] = entity.getId();
                        entityTypesMap[i][j] = entity.getEntityType();
                    }
            }

            for (BuildingTask task : buildingTasks.values()) {
                Vec2Int pos = task.pos;
                int size = playerView.getEntityProperties().get(task.buildingEntity).getSize();
                int label = rand.nextInt();
                for (int i1 = pos.getX(); i1 < pos.getX() + size; i1++)
                    for (int j1 = pos.getY(); j1 < pos.getY() + size; j1++) {
                        map[i1][j1] = label;
                        entityTypesMap[i1][j1] = task.buildingEntity;
                    }
            }

            resource = me.getResource();

            long startTimeOfBases = System.currentTimeMillis();
            int resourceSpent = (int) ((resource - reservedResource) * basesResourceProps);
            int rangedBasesCount = myActiveEntitiesCount.getOrDefault(EntityType.RANGED_BASE, 0);
            if (tickNum < 55 || tickNum % 3 == 0 || rangedBasesCount > 0) {
                resourceSpent = addBasesActions(playerView, (int) ((resource)), entityActions);
            } else {
                for (Entity entity : playerView.getEntities()) {
                    if (isEqual(entity.getPlayerId(), myId) && isBase(entity)) {
                        EntityAction entityAction = new EntityAction();
                        entityActions.put(entity.getId(), entityAction);
                    }
                }
            }
            resource -= resourceSpent;
            if (tickNum % 7 != 0)
                resource = 0;
            timeOfBases += System.currentTimeMillis() - startTimeOfBases;

            long startTimeOfBuilders = System.currentTimeMillis();
            addBuildersActions(playerView, entityActions);
            timeOfBuilders += System.currentTimeMillis() - startTimeOfBuilders;
            //System.out.println(timeOfBuilders);

            long startTimeOfWarriors = System.currentTimeMillis();
            addWarriorsActions(playerView, entityActions);
            timeOfWarriors += System.currentTimeMillis() - startTimeOfWarriors;
            //System.out.println(timeOfWarriors);

            for (Entity entity : playerEntities) {
                if (isEqual(entity.getPlayerId(), myId)) {
                    EntityProperties properties = playerView.getEntityProperties().get(entity.getEntityType());

                    EntityAction entityAction = new EntityAction();
                    // TURRETS
                    if (entity.getEntityType() == EntityType.TURRET) {
                        entityAction.setAttackAction(new AttackAction(null,
                                new AutoAttack(mapSize, otherTargets)));
                        entityActions.put(entity.getId(), entityAction);
                    }
                }
            }
        } catch (Throwable e) {
            System.out.println(e.getMessage() + " " + Arrays.toString(e.getStackTrace()));
            lastThrowable = e;
        }
        timeOfWork += System.currentTimeMillis() - startTime;
//        System.out.println(timeOfWork + " " + (double)timeOfBuilders / timeOfWork + " " + (double)timeOfWarriors / timeOfWork +
//                " " + (double)timeOfBases / timeOfWork + " " + (double)(timeOfWork - timeOfWarriors - timeOfBuilders - timeOfBases) / timeOfWork);
        System.out.println(timeOfWork + " " + queueHelper.getTimeOfGetDistance());
        return action;
    }


    public void debugUpdate(PlayerView playerView, DebugInterface debugInterface) {
//        if (lastThrowable != null) {
//            DebugCommand command = new DebugCommand() {
//                @Override
//                public void writeTo(OutputStream stream) throws IOException {
//                    stream.write(lastThrowable.toString().getBytes());
//                }
//            };
//            debugInterface.send(command);
//            lastThrowable = null;
//        }
//        debugInterface.getState();
    }

    private int addBasesActions(PlayerView playerView, int resource, Map<Integer, EntityAction> entityActions) {
        resourcesEnough = true;
        populationEnough = true;
        int resourceSpent = 0;
        double rangedResources = getProps(resource, EntityType.RANGED_UNIT);
        double builderResources = getProps(resource, EntityType.BUILDER_UNIT);
        double meleeResources = getProps(resource, EntityType.MELEE_UNIT);

        int populationFree = populationProvide - populationUse;
        double rangedPop = getProps(populationFree, EntityType.RANGED_UNIT);
        double builderPop = getProps(populationFree, EntityType.BUILDER_UNIT);
        double meleePop = getProps(populationFree, EntityType.MELEE_UNIT);
        EntityProperties rangedProps = playerView.getEntityProperties().get(EntityType.RANGED_UNIT);
        EntityProperties builderProps = playerView.getEntityProperties().get(EntityType.BUILDER_UNIT);
        EntityProperties meleeProps = playerView.getEntityProperties().get(EntityType.MELEE_UNIT);

        if (myActiveEntitiesCount.getOrDefault(EntityType.RANGED_BASE, 0) == 0) {
            builderPop = populationFree;
            rangedPop = 0;
            meleePop = 0;
            builderResources = resource;
            rangedResources = 0;
            meleeResources = 0;
        }

        if (rangedResources < rangedProps.getInitialCost() + myEntitiesCount.getOrDefault(EntityType.RANGED_UNIT, 0)
            || builderResources < builderProps.getInitialCost() + myEntitiesCount.getOrDefault(EntityType.BUILDER_UNIT, 0)) {
            resourcesEnough = false;
            double randNum = rand.nextDouble();
            if ((tickNum > 500 || randNum < getPopulationProps(EntityType.RANGED_UNIT)) && myActiveEntitiesCount.getOrDefault(EntityType.RANGED_BASE, 0) > 0) {
                rangedResources = resource;
                builderResources = 0;
                meleeResources = 0;
                rangedPop = populationFree;
                builderPop = 0;
                meleePop = 0;
            } else {
                rangedResources = 0;
                builderResources = resource;
                meleeResources = 0;
                rangedPop = 0;
                builderPop = populationFree;
                meleePop = 0;
            }
        }
        //////////////////////////////////////////////////////
        for (Entity entity : playerEntities) {
            if (isEqual(entity.getPlayerId(), myId) && entity.getEntityType() == EntityType.RANGED_BASE) {
                int resourcesOnOne = rangedProps.getInitialCost() + myEntitiesCount.getOrDefault(EntityType.RANGED_UNIT, 0);
                if (resourceSpent + resourcesOnOne > rangedResources) {
                    resourcesEnough = false;
                    break;
                }
                if (rangedProps.getPopulationUse() > rangedPop) {
                    populationEnough = false;
                    break;
                }
                resourceSpent += resourcesOnOne;
                populationFree -= rangedProps.getPopulationUse();
                Integer count = myEntitiesCount.getOrDefault(EntityType.RANGED_UNIT, 0);
                if (count == null)
                    count = 1;
                else
                    count++;
                myEntitiesCount.put(EntityType.RANGED_UNIT, count);
                EntityAction entityAction = new EntityAction();
                int xOffset = rand.nextInt(2) - 1;
                int yOffset = 0;
                if (xOffset == 0) {
                    yOffset = -1;
                }
                entityAction.setBuildAction(new BuildAction(EntityType.RANGED_UNIT,
                        MyVec2Int.add(entity.getPosition(), new Vec2Int(xOffset, yOffset))));
                entityActions.put(entity.getId(), entityAction);
            }
        }
        //////////////////////////////////////////////////////
        for (Entity entity : playerEntities) {
            if (isEqual(entity.getPlayerId(), myId) && entity.getEntityType() == EntityType.BUILDER_BASE) {
                int resourcesOnOne = builderProps.getInitialCost() + myEntitiesCount.getOrDefault(EntityType.BUILDER_UNIT, 0);
                if (resourceSpent + resourcesOnOne > builderResources) {
                    resourcesEnough = false;
                    break;
                }
                if (builderProps.getPopulationUse() > builderPop) {
                    populationEnough = false;
                    break;
                }
                resourceSpent += resourcesOnOne;
                populationFree -= builderProps.getPopulationUse();
                Integer count = myEntitiesCount.getOrDefault(EntityType.BUILDER_UNIT, 0);
                if (count == null)
                    count = 1;
                else
                    count++;
                myEntitiesCount.put(EntityType.BUILDER_UNIT, count);
                EntityAction entityAction = new EntityAction();
                int xOffset = rand.nextInt(2) - 1;
                int yOffset = 0;
                if (xOffset == 0) {
                    yOffset = -1;
                }
                entityAction.setBuildAction(new BuildAction(EntityType.BUILDER_UNIT,
                        MyVec2Int.add(entity.getPosition(), new Vec2Int(xOffset, yOffset))));
                entityActions.put(entity.getId(), entityAction);
            }
        }
        //////////////////////////////////////////////////////
        for (Entity entity : playerEntities) {
            if (isEqual(entity.getPlayerId(), myId) && entity.getEntityType() == EntityType.MELEE_BASE) {
                int resourcesOnOne = meleeProps.getInitialCost() + myEntitiesCount.getOrDefault(EntityType.MELEE_UNIT, 0);
                if (resourceSpent + resourcesOnOne > meleeResources) {
                    resourcesEnough = false;
                    break;
                }
                if (meleeProps.getPopulationUse() > meleePop) {
                    populationEnough = false;
                    break;
                }
                resourceSpent += resourcesOnOne;
                populationFree -= meleeProps.getPopulationUse();
                Integer count = myEntitiesCount.getOrDefault(EntityType.MELEE_UNIT, 0);
                if (count == null)
                    count = 1;
                else
                    count++;
                myEntitiesCount.put(EntityType.MELEE_UNIT, count);
                EntityAction entityAction = new EntityAction();
                int xOffset = rand.nextInt(2) - 1;
                int yOffset = 0;
                if (xOffset == 0) {
                    yOffset = -1;
                }
                entityAction.setBuildAction(new BuildAction(EntityType.MELEE_UNIT,
                        MyVec2Int.add(entity.getPosition(), new Vec2Int(xOffset, yOffset))));
                entityActions.put(entity.getId(), entityAction);
            }
        }
        if (populationFree <= 5)
            populationEnough = false;
        return resourceSpent;
    }

    private void addBuildersActions(PlayerView playerView, Map<Integer, EntityAction> entityActions) {
        List<Entity> inactiveEntities = new ArrayList<>();
        List<Entity> builderEntities = new ArrayList<>();
        List<EntityType> builderBreakThrough = new ArrayList<>();
        builderBreakThrough.add(EntityType.RESOURCE);
        int houseBuildingTasks = 0, basesBuildingTasks = 0;
        int rangedBasesCount = myActiveEntitiesCount.getOrDefault(EntityType.RANGED_BASE, 0);
        for (Entity entity : playerEntities) {
            if (isEqual(entity.getPlayerId(), myId)) {
                if (!entity.isActive())
                    inactiveEntities.add(entity);
                if (entity.getEntityType() == EntityType.BUILDER_UNIT && entity.isActive()) {
                    BuildingTask buildingTask = buildingTasks.getOrDefault(entity.getId(), null);
                    boolean continueTask = false;
                    if (buildingTask != null) {
                        MyVec2Int pos = buildingTask.pos;
                        EntityAction action = new EntityAction();
                        EntityProperties props = playerView.getEntityProperties().get(buildingTask.buildingEntity);
                        if (buildingTask.buildingEntity == EntityType.RANGED_BASE && rangedBasesCount > 0 && resource < 1500) {
                            buildingTasks.remove(entity.getId());
                        } else if (MyVec2Int.calcDist(buildingTask.dest, entity.getPosition()) == 0) {
                            continueTask = true;
                            action.setBuildAction(new BuildAction(buildingTask.buildingEntity, pos));
                            buildingTasks.remove(entity.getId());
                        } else {
                            Cell cell = queueHelper.getDistance(entity.getPosition(), 1, pos, props.getSize(), builderBreakThrough);
                            if (cell.dist > -1) {
                                continueTask = true;
                                action.setMoveAction(new MoveAction(new Vec2Int(cell.x, cell.y), false, true));
                                buildingTask.dest = new MyVec2Int(cell.x, cell.y);
                            } else {
                                buildingTasks.remove(entity.getId());
                            }
                        }
                        if (continueTask) {
                            resource -= props.getInitialCost();
                            entityActions.put(entity.getId(), action);
                            if (buildingTask.buildingEntity == EntityType.HOUSE)
                                houseBuildingTasks++;
                            else
                                basesBuildingTasks++;
                        }
                    }
                    if (!continueTask)
                        builderEntities.add(entity);
                }
            }
        }
        // REPAIR
        inactiveEntities.sort((a, b) -> {
            EntityProperties ap = playerView.getEntityProperties().get(a.getEntityType());
            EntityProperties bp = playerView.getEntityProperties().get(b.getEntityType());
            return Integer.compare(ap.getMaxHealth() - a.getHealth(), bp.getMaxHealth() - b.getHealth());
        });

        EntityProperties builderProps = playerView.getEntityProperties().get(EntityType.BUILDER_UNIT);
        int builderCount = myActiveEntitiesCount.getOrDefault(EntityType.BUILDER_UNIT, 0);
        int freeBuilderCount = (int) (builderCount * BUILDER_REPAIR);
        int maxBuildersPerEntity = Math.max(1, Math.min(REPAIR_INTENSITY,
                inactiveEntities.size() == 0 ? 0 : freeBuilderCount / inactiveEntities.size()));
        // TODO: Like bipartite graph. Hungary algorithm.
        for (Entity entity : inactiveEntities) {
            EntityProperties props = playerView.getEntityProperties().get(entity.getEntityType());
            int healthLack = props.getMaxHealth() - entity.getHealth();
            int buildersPerEntity = Math.max(1, Math.min(maxBuildersPerEntity, healthLack / (builderProps.getRepair().getPower() * 2)));
            queueHelper.findDistances(entity.getPosition(), props.getSize(), builderBreakThrough);
            builderEntities.sort((a, b) -> {
                int sizeA = playerView.getEntityProperties().get(a.getEntityType()).getSize();
                int sizeB = playerView.getEntityProperties().get(b.getEntityType()).getSize();
                Cell ac = queueHelper.findDistance(a.getPosition(), sizeA);
                Cell bc = queueHelper.findDistance(b.getPosition(), sizeB);
                if (ac.dist == -1 && bc.dist == -1)
                    return 0;
                if (ac.dist == -1)
                    return 1;
                if (bc.dist == -1)
                    return -1;
                return Integer.compare(ac.dist, bc.dist);
            });
            int k = 0;
            for (int i = 0; i < builderEntities.size(); i++) {
                Entity builderEntity = builderEntities.get(i);
                Cell cell = queueHelper.getDistance(builderEntity.getPosition(), builderProps.getSize(),
                        entity.getPosition(), props.getSize(), builderBreakThrough);
                if (cell.dist == -1)
                    break;
                if (i >= buildersPerEntity && cell.dist > 0)
                    break;
                k++;
                EntityAction action = new EntityAction();
                if (cell.dist == 0) {
                    action.setRepairAction(new RepairAction(entity.getId()));
                } else {
                    action.setMoveAction(new MoveAction(new Vec2Int(cell.x, cell.y), true, true));
                }
                entityActions.put(builderEntity.getId(), action);
            }
            builderEntities = builderEntities.subList(k, builderEntities.size());
        }

        // COLLECT RESOURCES
        builderCount = builderEntities.size();
        int collectorsCount = Math.max(1, (int) Math.round(builderCount * BUILDER_RESOURCES_FROM_WITHOUT_REPAIR));
        builderEntities.sort((a, b) -> {
            int sizeA = playerView.getEntityProperties().get(a.getEntityType()).getSize();
            int sizeB = playerView.getEntityProperties().get(b.getEntityType()).getSize();
            int ac = MyVec2Int.calcDist(a.getPosition(), ct);
            int bc = MyVec2Int.calcDist(b.getPosition(), ct);
            return Integer.compare(ac, bc);
        });
        for (int i = 0; i < collectorsCount && i < builderCount; i++) {
            Entity builderEntity = builderEntities.get(i);
            EntityAction action = new EntityAction();
            action.setAttackAction(new AttackAction(null, new AutoAttack(mapSize, builderTargets)));
            entityActions.put(builderEntity.getId(), action);
        }
        builderEntities = builderEntities.subList(collectorsCount, builderEntities.size());

        // BUILD

        // TURRETS
        EntityProperties turretProps = playerView.getEntityProperties().get(EntityType.TURRET);
        int turretsCount = myEntitiesCount.getOrDefault(EntityType.TURRET, 0);
        if (builderEntities.size() > 1 && (tickNum >= TURRET_TICK || rangedBasesCount > 0) && resource > 100 && (turretsCount < MAX_TURRETS_COUNT || resource > 1000)) {
            int requiredDist = (int) (mapSize * TURRET_RADIUS);
            double randomAngle = rand.nextDouble() * Math.PI / 2;
            int xd = (int) (Math.cos(randomAngle) * requiredDist * myDirection.getX());
            int yd = (int) (Math.sin(randomAngle) * requiredDist * myDirection.getY());
            Vec2Int pos = findPlaceToBuildTurret(new Vec2Int(xd, yd), turretProps.getSize(), turretProps.getAttack().getAttackRange());
            if (pos != null) {

                Entity builderEntity = null;
                int dist = -1;
                for (Entity entity : builderEntities) {
                    if (builderEntity == null || MyVec2Int.calcDist(entity.getPosition(), pos) < dist) {
                        dist = MyVec2Int.calcDist(entity.getPosition(), pos);
                        builderEntity = entity;
                    }
                }

                if (builderEntity != null) {
                    resource -= turretProps.getInitialCost();
                    EntityAction action = new EntityAction();
                    if (MyVec2Int.calcDist(pos, builderEntity.getPosition()) == 1 && !isIn(pos, turretProps.getSize(), builderEntity.getPosition())) {
                        action.setBuildAction(new BuildAction(EntityType.TURRET, pos));
                    } else {
                        Cell cell = queueHelper.getDistance(builderEntity.getPosition(), 1, pos, turretProps.getSize(), builderBreakThrough);
                        action.setMoveAction(new MoveAction(new Vec2Int(cell.x, cell.y), false, true));
                        buildingTasks.put(builderEntity.getId(), new BuildingTask(builderEntity.getId(), EntityType.TURRET, new MyVec2Int(pos), new MyVec2Int(cell.x, cell.y)));
                    }
                    entityActions.put(builderEntity.getId(), action);
                    int label = rand.nextInt();
                    for (int i1 = pos.getX(); i1 < pos.getX() + turretProps.getSize(); i1++)
                        for (int j1 = pos.getY(); j1 < pos.getY() + turretProps.getSize(); j1++) {
                            map[i1][j1] = label;
                            entityTypesMap[i1][j1] = EntityType.TURRET;
                        }

                    builderEntities.remove(builderEntity);
                }
            }
        }

        if (!resourcesEnough) {
            builderCount = builderEntities.size();
            collectorsCount = (int) (builderCount * 0.49);
            builderEntities.sort((a, b) -> {
                int sizeA = playerView.getEntityProperties().get(a.getEntityType()).getSize();
                int sizeB = playerView.getEntityProperties().get(b.getEntityType()).getSize();
                int ac = MyVec2Int.calcDist(a.getPosition(), ct);
                int bc = MyVec2Int.calcDist(b.getPosition(), ct);
                return Integer.compare(ac, bc);
            });
            for (int i = 0; i < collectorsCount && i < builderCount; i++) {
                Entity builderEntity = builderEntities.get(i);
                EntityAction action = new EntityAction();
                action.setAttackAction(new AttackAction(null, new AutoAttack(mapSize, builderTargets)));
                entityActions.put(builderEntity.getId(), action);
            }
            builderEntities = builderEntities.subList(collectorsCount, builderEntities.size());
        }
        builderCount = builderEntities.size();
        rangedBasesCount = myEntitiesCount.getOrDefault(EntityType.RANGED_BASE, 0);
        int housesCount = myEntitiesCount.getOrDefault(EntityType.HOUSE, 0);
        Vec2Int rangedBasePos = findPlaceToBuild(myLoc, playerView.getEntityProperties().get(EntityType.RANGED_BASE).getSize());
        if (!populationEnough && (rangedBasesCount > 0 || rangedBasePos == null) || housesCount < 2 || rand.nextInt(3) > 0) {
            // BUILD HOUSES
            int k = 0;
            EntityProperties houseProps = playerView.getEntityProperties().get(EntityType.HOUSE);
            Collections.shuffle(builderEntities);
            for (int i = 0; i < builderCount - houseBuildingTasks; i++) {
                Entity entity = builderEntities.get(i);
                if (resource < houseProps.getInitialCost()) {
                    break;
                }
                Vec2Int pos = findPlaceToBuild(entity.getPosition(), houseProps.getSize());
                if (pos == null)
                    break;
                k++;
                resource -= houseProps.getInitialCost();
                EntityAction action = new EntityAction();
                if (MyVec2Int.calcDist(pos, entity.getPosition()) == 1 && !isIn(pos, houseProps.getSize(), entity.getPosition())) {
                    action.setBuildAction(new BuildAction(EntityType.HOUSE, pos));
                } else {
                    Cell cell = queueHelper.getDistance(entity.getPosition(), 1, pos, houseProps.getSize(), builderBreakThrough);
                    action.setMoveAction(new MoveAction(new Vec2Int(cell.x, cell.y), false, true));
                    buildingTasks.put(entity.getId(), new BuildingTask(entity.getId(), EntityType.HOUSE, new MyVec2Int(pos), new MyVec2Int(cell.x, cell.y)));
                }
                entityActions.put(entity.getId(), action);
                int label = rand.nextInt();
                for (int i1 = pos.getX(); i1 < pos.getX() + houseProps.getSize(); i1++)
                    for (int j1 = pos.getY(); j1 < pos.getY() + houseProps.getSize(); j1++) {
                        map[i1][j1] = label;
                        entityTypesMap[i1][j1] = EntityType.HOUSE;
                    }
            }
            builderEntities = builderEntities.subList(k, builderEntities.size());
        } else {
            // BUILD BASES
            if (resource > 1500 || rangedBasesCount == 0) {
                int k = 0;
                Collections.shuffle(builderEntities);
                EntityProperties mbProps = playerView.getEntityProperties().get(EntityType.MELEE_BASE);
                EntityProperties rbProps = playerView.getEntityProperties().get(EntityType.RANGED_BASE);
                EntityProperties bbProps = playerView.getEntityProperties().get(EntityType.BUILDER_BASE);
                for (int i = 0; i < builderCount - basesBuildingTasks; i++) {
                    Entity entity = builderEntities.get(i);
                    EntityType type;
                    EntityProperties props;
                    if (rangedBasesCount == 0) {
                        type = EntityType.RANGED_BASE;
                        props = rbProps;
                    } else {
                        double randNum = rand.nextDouble();
                        if (randNum < getPopulationProps(EntityType.MELEE_UNIT)) {
                            type = EntityType.MELEE_BASE;
                            props = mbProps;
                        } else if (randNum > 1 - getPopulationProps(EntityType.BUILDER_UNIT)) {
                            type = EntityType.BUILDER_BASE;
                            props = bbProps;
                        } else {
                            type = EntityType.RANGED_BASE;
                            props = rbProps;
                        }
                    }
                    Vec2Int pos = findPlaceToBuild(entity.getPosition(), props.getSize());
                    if (pos == null)
                        break;
                    k++;
                    resource -= props.getInitialCost();
                    EntityAction action = new EntityAction();
                    if (MyVec2Int.calcDist(pos, entity.getPosition()) == 1 && !isIn(pos, props.getSize(), entity.getPosition())) {
                        action.setBuildAction(new BuildAction(type, pos));
                    } else {
                        Cell cell = queueHelper.getDistance(entity.getPosition(), 1, pos, props.getSize(), builderBreakThrough);
                        action.setMoveAction(new MoveAction(new Vec2Int(cell.x, cell.y), false, true));
                        buildingTasks.put(entity.getId(), new BuildingTask(entity.getId(), type, new MyVec2Int(pos), new MyVec2Int(cell.x, cell.y)));
                    }
                    entityActions.put(entity.getId(), action);
                    int label = rand.nextInt();
                    for (int i1 = pos.getX(); i1 < pos.getX() + props.getSize(); i1++)
                        for (int j1 = pos.getY(); j1 < pos.getY() + props.getSize(); j1++) {
                            map[i1][j1] = label;
                            entityTypesMap[i1][j1] = type;
                        }
                }
                builderEntities = builderEntities.subList(k, builderEntities.size());
            }
        }
        builderCount = builderEntities.size();
        for (int i = 0; i < builderCount; i++) {
            Entity builderEntity = builderEntities.get(i);
            EntityAction action = new EntityAction();
            action.setAttackAction(new AttackAction(null, new AutoAttack(mapSize, builderTargets)));
            entityActions.put(builderEntity.getId(), action);
        }
    }

    private Vec2Int findPlaceToBuild(Vec2Int point, int size) {
        Vec2Int nearestAngle = rt;
        if (MyVec2Int.calcDist(nearestAngle, point) > MyVec2Int.calcDist(rb, point))
            nearestAngle = rb;
        if (MyVec2Int.calcDist(nearestAngle, point) > MyVec2Int.calcDist(lb, point))
            nearestAngle = lb;
        if (MyVec2Int.calcDist(nearestAngle, point) > MyVec2Int.calcDist(lt, point))
            nearestAngle = lt;
        MyVec2Int bestCell = new MyVec2Int(1000000, 1000000);
        MyVec2Int tempVec = new MyVec2Int(0, 0);
        for (int i = 1; i < mapSize - size; i++)
            for (int j = 1; j < mapSize - size; j++) {
                tempVec.setX(i);
                tempVec.setY(j);
                if (MyVec2Int.calcDist(bestCell, nearestAngle) > MyVec2Int.calcDist(tempVec, nearestAngle)) {
                    boolean f = true;
                    for (int i1 = i - 1; i1 <= i + size && i1 < mapSize && f; i1++)
                        for (int j1 = j - 1; j1 <= j + size && j1 < mapSize; j1++)
                            if (map[i1][j1] != EMPTY) {
                                f = false;
                                break;
                            }
                    if (f) {
                        bestCell.setX(i);
                        bestCell.setY(j);
                    }
                }
            }
        return bestCell.getX() == 1000000 ? null : bestCell;
    }

    private Vec2Int findPlaceToBuildTurret(Vec2Int point, int size, int range) {
        MyVec2Int bestCell = new MyVec2Int(1000000, 1000000);
        MyVec2Int tempVec = new MyVec2Int(0, 0);
        for (int i = 1; i < mapSize - size; i++)
            for (int j = 1; j < mapSize - size; j++) {
                tempVec.setX(i);
                tempVec.setY(j);
                if (MyVec2Int.calcDist(bestCell, point) > MyVec2Int.calcDist(tempVec, point)) {
                    boolean f = true;
                    for (int i1 = i - 1; i1 <= i + size && i1 < mapSize && f; i1++)
                        for (int j1 = j - 1; j1 <= j + size && j1 < mapSize; j1++)
                            if (map[i1][j1] != EMPTY) {
                                f = false;
                                break;
                            }
                    for (int i1 = Math.max(0, i - range + 3); i1 < i + range - 3 && i1 < mapSize && f; i1++)
                        for (int j1 = Math.max(0, j - range + 3); j1 < j + range - 3 && j1 < mapSize; j1++)
                            if (entityTypesMap[i1][j1] == EntityType.TURRET) {
                                f = false;
                                break;
                            }
                    if (f) {
                        bestCell.setX(i);
                        bestCell.setY(j);
                    }
                }
            }
        return bestCell.getX() == 1000000 ? null : bestCell;
    }

    private void addWarriorsActions(PlayerView playerView, Map<Integer, EntityAction> entityActions) {
        List<Entity> warriors = new ArrayList<>();
        List<Entity> newWarriors = new ArrayList<>();
        List<Entity> opponents = new ArrayList<>();
        int rangedCount = 0, meleeCount = 0;
        int defendersCount = 0, attackersCount = 0;
        Map<Integer, WarriorRole> tempWarriorRoles = new HashMap<>();
        for (Entity entity : playerEntities) {
            if (isEqual(entity.getPlayerId(), myId) && isWarrior(entity) && entity.isActive()) {
                warriors.add(entity);
                if (entity.getEntityType() == EntityType.MELEE_UNIT) {
                    meleeCount++;
                } else {
                    rangedCount++;
                }
                WarriorRole role = warriorRoles.getOrDefault(entity.getId(), null);
                if (role != null) {
                    tempWarriorRoles.put(entity.getId(), role);
                    if (role == WarriorRole.DEFENDER)
                        defendersCount++;
                    else
                        attackersCount++;
                } else {
                    newWarriors.add(entity);
                }
            }

            if (!isEqual(entity.getPlayerId(), myId) && isWarrior(entity)) {
                opponents.add(entity);
            }
        }
        warriorRoles = tempWarriorRoles;
        int requiredDefendersCount = Math.max(3, (int) (warriors.size() * DEFENCE_RATE));
        for (Entity entity : newWarriors) {
            if (defendersCount < requiredDefendersCount) {
                warriorRoles.put(entity.getId(), WarriorRole.DEFENDER);
                defendersCount++;
                int distFromAngle = MyVec2Int.calcDirectDist(myLoc, entity.getPosition());
                int requiredDist = (int) (mapSize * DEFENCE_RADIUS);
                double randomAngle = rand.nextDouble() * Math.PI / 2;
                int xd = (int) (Math.cos(randomAngle) * requiredDist * myDirection.getX());
                int yd = (int) (Math.sin(randomAngle) * requiredDist * myDirection.getY());
                EntityAction action = new EntityAction();
                action.setMoveAction(new MoveAction(new Vec2Int(xd, yd), true, true));
                entityActions.put(entity.getId(), action);
            } else {
                warriorRoles.put(entity.getId(), WarriorRole.ATTACKER);
                attackersCount++;
            }
        }

        long startTime = System.currentTimeMillis();
        for (Entity entity : warriors) {
            EntityProperties props = playerView.getEntityProperties().get(entity.getEntityType());
            if (warriorRoles.get(entity.getId()) == WarriorRole.ATTACKER) {
                Cell opponent = findClosestOpponent(playerView, entity.getPosition());
                if (opponent != null) {
                    EntityAction action = new EntityAction();
                    if (opponent.dist <= props.getAttack().getAttackRange() + 2) {
                        action.setAttackAction(new AttackAction(null,
                                new AutoAttack(mapSize * mapSize, otherTargets)));
                    } else {
                        int warriorsNearby = getWarriorsCount(playerView, myId, entity.getPosition(), null, 4);
                        int warriorsToWait = getWarriorsCount(playerView, myId, entity.getPosition(), myLoc, mapSize / 8);
                        int waitParam = opponent.dist < props.getAttack().getAttackRange() * 2 ? 4 : 2;
                        if (newWarriors.contains(entity) || warriorsNearby == warriorsToWait || warriorsNearby > 3 || tickNum % 2 == 0)
                            action.setMoveAction(new MoveAction(new Vec2Int(opponent.x, opponent.y), true, true));
                    }
                    entityActions.put(entity.getId(), action);
                } else {
                    EntityAction action = new EntityAction();
                    int warriorsNearby = getWarriorsCount(playerView, myId, entity.getPosition(), null, 4);
                    int warriorsToWait = getWarriorsCount(playerView, myId, entity.getPosition(), myLoc, mapSize / 8);
                    //if (newWarriors.contains(entity) || warriorsNearby == warriorsToWait || warriorsNearby > 3 || tickNum % 2 == 0)
                    if (playerView.getPlayers().length > 2) {
                        if (MyVec2Int.calcDist(attackAngle, entity.getPosition()) < 3) {
                            if (attackAngle.isEqual(rb))
                                attackAngle = lt;
                            else
                                attackAngle = rt;
                        }
                    }
                    action.setMoveAction(new MoveAction(attackAngle, true, true));
                    entityActions.put(entity.getId(), action);
                }
            } else {
                boolean shouldAttack = false;
                Collections.shuffle(opponents, rand);
                for (Entity opponent : opponents) {
                    EntityProperties opponentProps = playerView.getEntityProperties().get(opponent.getEntityType());
                    int dist = MyVec2Int.calcDist(opponent.getPosition(), entity.getPosition());
                    if (dist <= props.getAttack().getAttackRange() * 3
                            || dist <= opponentProps.getAttack().getAttackRange() * 3) {
                        EntityAction action = new EntityAction();
                        action.setAttackAction(new AttackAction(opponent.getId(), null));
                        entityActions.put(entity.getId(), action);
                        shouldAttack = true;
                        break;
                    }
                }
                // TODO
            }
        }
        System.out.println(System.currentTimeMillis() - startTime);
    }

    private int getWarriorsCount(PlayerView playerView, Integer playerId, Vec2Int pos, Vec2Int angle, int radius) {
        int count = 0;
        int dist = 0;
        if (angle != null)
            dist = MyVec2Int.calcDist(pos, angle);
        List<EntityType> warriorBreakThrough = new ArrayList<>(Arrays.asList(otherTargets));
        for (Entity entity : playerEntities) {
            if (isWarrior(entity) && (playerId == null || isEqual(playerId, entity.getPlayerId()))) {
                if (angle == null || MyVec2Int.calcDist(entity.getPosition(), angle) <= dist) {
//                    Cell cell = queueHelper.getDistance(pos, 1, entity.getPosition(), 1, warriorBreakThrough);
//                    if (cell.dist != -1 && cell.dist <= radius) {
//                        count++;
//                    }
                    int curDist = MyVec2Int.calcDist(pos, entity.getPosition());
                    if (curDist <= radius)
                        count++;
                }
            }
        }
        return count;
    }

    private Cell findClosestOpponent(PlayerView playerView, Vec2Int pos) {
        long startTime = System.currentTimeMillis();
        Cell res = null;
        List<EntityType> warriorBreakThrough = new ArrayList<>(Arrays.asList(otherTargets));
        queueHelper.findDistances(pos, 1, warriorBreakThrough);
        for (Entity entity : playerEntities) {
            if (!isEqual(entity.getPlayerId(), myId)) {
                Cell cell = queueHelper.findDistance(entity.getPosition(),
                        playerView.getEntityProperties().get(entity.getEntityType()).getSize());
                if (cell.dist != -1) {
                    if (res == null || cell.dist < res.dist) {
                        res = cell;
                    }
                }
            }
        }
        return res;
    }

    private void initialize(PlayerView playerView) {
        myId = playerView.getMyId();
        mapSize = playerView.getMapSize();
        lb = new MyVec2Int(0, 0);
        lt = new MyVec2Int(0, mapSize - 1);
        rb = new MyVec2Int(mapSize - 1, 0);
        rt = new MyVec2Int(mapSize - 1, mapSize - 1);
        ct = new MyVec2Int(mapSize / 2, mapSize / 2);
        if (playerView.getPlayers().length > 2)
            attackAngle = rb;
        else
            attackAngle = rt;
        map = new int[mapSize][mapSize];
        marks = new int[mapSize][mapSize];
        entityTypesMap = new EntityType[mapSize][mapSize];
        queueHelper = new QueueHelper(map, marks, entityTypesMap, mapSize);

        for (Entity entity : playerView.getEntities()) {
            if (isEqual(entity.getPlayerId(), myId)) {
                Vec2Int pos = entity.getPosition();
                myLoc = rt;
                myDirection = new MyVec2Int(-1, -1);
                if (MyVec2Int.calcDist(myLoc, pos) > MyVec2Int.calcDist(rb, pos)) {
                    myLoc = rb;
                    myDirection = new MyVec2Int(-1, 1);
                }
                if (MyVec2Int.calcDist(myLoc, pos) > MyVec2Int.calcDist(lb, pos)) {
                    myLoc = lb;
                    myDirection = new MyVec2Int(1, 1);
                }
                if (MyVec2Int.calcDist(myLoc, pos) > MyVec2Int.calcDist(lt, pos)) {
                    myLoc = lt;
                    myDirection = new MyVec2Int(1, -1);
                }
                break;
            }
        }
        isInitialized = true;
    }

    private int getProps(int value, EntityType type) {
        return (int) Math.round(value * getPopulationProps(type));
    }

    private double getPopulationProps(EntityType type) {
        if (tickNum < 85) {
            switch (type) {
                case MELEE_UNIT:
                    return earlyProps[0];
                case RANGED_UNIT:
                    return earlyProps[1];
                case BUILDER_UNIT:
                    return earlyProps[2];
                default:
                    return 0;
            }
        }
        switch (type) {
            case MELEE_UNIT:
                return populationProps[0];
            case RANGED_UNIT:
                return populationProps[1];
            case BUILDER_UNIT:
                return populationProps[2];
            default:
                return 0;
        }
    }
}