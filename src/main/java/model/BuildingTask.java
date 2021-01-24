package model;

public class BuildingTask {
    public int builderId;
    public EntityType buildingEntity;
    public MyVec2Int pos;
    public MyVec2Int dest;

    public BuildingTask(int builderId, EntityType buildingEntity, MyVec2Int pos, MyVec2Int dest) {
        this.builderId = builderId;
        this.buildingEntity = buildingEntity;
        this.pos = pos;
        this.dest = dest;
    }
}
