package util;

import model.Cell;
import model.EntityType;
import model.Vec2Int;

import java.util.*;

import static util.StrategyHelper.isIn;

public class QueueHelper {
    private int[][] map, marks;
    private EntityType[][] entityTypesMap;
    private final int EMPTY = 1234567;
    private int mapSize;

    private int result = -1;

    private long timeOfGetDistance = 0;

    public QueueHelper(int[][] map, int[][] marks, EntityType[][] entityTypesMap, int mapSize) {
        this.map = map;
        this.marks = marks;
        this.entityTypesMap = entityTypesMap;
        this.mapSize = mapSize;
    }

    private void add(Queue<Cell> q, Cell cell, Vec2Int bp, int sizeB, List<EntityType> breakThrough) {
        if (cell.x < 0 || cell.x >= mapSize || cell.y < 0 || cell.y >= mapSize)
            return;
        if (isIn(bp, sizeB, cell.x, cell.y)) {
            result = cell.dist;
            return;
        }
        boolean canPass = true;
        try {
            canPass = (map[cell.x][cell.y] == EMPTY || breakThrough.contains(entityTypesMap[cell.x][cell.y]));
        } catch (Throwable e) {
            System.out.println(e);
        }
        if (!canPass || marks[cell.x][cell.y] > -1 && marks[cell.x][cell.y] <= cell.dist)
            return;
        // TODO: think if we want to penalize for resource destruction.
//        if (map[cell.x][cell.y] != EMPTY)
//            cell.dist++;

        q.add(cell);
        marks[cell.x][cell.y] = cell.dist;
    }

    public Cell getDistance(Vec2Int ap, int sizeA, Vec2Int bp, int sizeB, List<EntityType> breakThrough) {
        long startTime = System.nanoTime();
        result = -1;
        for (int i = 0; i < mapSize; i++)
            for (int j = 0; j < mapSize; j++)
                marks[i][j] = -1;
        Queue<Cell> q = new LinkedList<>();
        for (int i = ap.getX(); i < ap.getX() + sizeA; i++)
            for (int j = ap.getY(); j < ap.getY() + sizeA; j++) {
                marks[i][j] = 0;
                q.add(new Cell(i, j, 0));
            }
        while (!q.isEmpty()) {
            Cell cell = q.poll();
            add(q, new Cell(cell.x + 1, cell.y, cell.dist + 1), bp, sizeB, breakThrough);
            add(q, new Cell(cell.x - 1, cell.y, cell.dist + 1), bp, sizeB, breakThrough);
            add(q, new Cell(cell.x, cell.y + 1, cell.dist + 1), bp, sizeB, breakThrough);
            add(q, new Cell(cell.x, cell.y - 1, cell.dist + 1), bp, sizeB, breakThrough);
            if (result > -1)
                return cell;
        }
        timeOfGetDistance += System.nanoTime() - startTime;
        return new Cell(0, 0, -1);
    }

    private void add(Queue<Cell> q, Cell cell, List<EntityType> breakThrough) {
        if (cell.x < 0 || cell.x >= mapSize || cell.y < 0 || cell.y >= mapSize)
            return;
        boolean canPass = true;
        try {
            canPass = (map[cell.x][cell.y] == EMPTY || breakThrough.contains(entityTypesMap[cell.x][cell.y]));
        } catch (Throwable e) {
            System.out.println(e);
        }
        if (!canPass || marks[cell.x][cell.y] > -1 && marks[cell.x][cell.y] <= cell.dist)
            return;
        // TODO: think if we want to penalize for resource destruction.
//        if (map[cell.x][cell.y] != EMPTY)
//            cell.dist++;

        q.add(cell);
        marks[cell.x][cell.y] = cell.dist;
    }

    public void findDistances(Vec2Int ap, int sizeA, List<EntityType> breakThrough) {
        for (int i = 0; i < mapSize; i++)
            for (int j = 0; j < mapSize; j++)
                marks[i][j] = -1;
        Queue<Cell> q = new LinkedList<>();
        for (int i = ap.getX(); i < ap.getX() + sizeA; i++)
            for (int j = ap.getY(); j < ap.getY() + sizeA; j++) {
                marks[i][j] = 0;
                q.add(new Cell(i, j, 0));
            }
        while (!q.isEmpty()) {
            Cell cell = q.poll();
            add(q, new Cell(cell.x + 1, cell.y, cell.dist + 1), breakThrough);
            add(q, new Cell(cell.x - 1, cell.y, cell.dist + 1), breakThrough);
            add(q, new Cell(cell.x, cell.y + 1, cell.dist + 1), breakThrough);
            add(q, new Cell(cell.x, cell.y - 1, cell.dist + 1), breakThrough);
        }
    }

    private void check(int x, int y, Cell ans) {
        if (x < 0 || x >= mapSize || y < 0 || y >= mapSize || marks[x][y] == -1)
            return;
        if (ans.dist == -1 || ans.dist > marks[x][y]) {
            ans.x = x;
            ans.y = y;
            ans.dist = marks[x][y];
        }
    }

    public Cell findDistance(Vec2Int p, int size) {
        Cell ans = new Cell(0, 0, -1);
        for (int i = p.getX(); i < p.getX() + size; i++)
            for (int j = p.getY(); j < p.getY() + size; j++) {
                check(i + 1, j, ans);
                check(i - 1, j, ans);
                check(i, j + 1, ans);
                check(i, j - 1, ans);
            }
        return ans;
    }

    public long getTimeOfGetDistance() {
        return timeOfGetDistance / 1000;
    }
}
