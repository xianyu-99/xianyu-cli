package com.yucli.checkpoint;

public class CheckpointEntry {
    private String id;
    private long createdAt;
    private String targetPath;
    private boolean existedBefore;
    private String snapshotFile;
    private long sizeBytes;

    public CheckpointEntry() {
    }

    public CheckpointEntry(String id,
                           long createdAt,
                           String targetPath,
                           boolean existedBefore,
                           String snapshotFile,
                           long sizeBytes) {
        this.id = id;
        this.createdAt = createdAt;
        this.targetPath = targetPath;
        this.existedBefore = existedBefore;
        this.snapshotFile = snapshotFile;
        this.sizeBytes = sizeBytes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public boolean isExistedBefore() {
        return existedBefore;
    }

    public void setExistedBefore(boolean existedBefore) {
        this.existedBefore = existedBefore;
    }

    public String getSnapshotFile() {
        return snapshotFile;
    }

    public void setSnapshotFile(String snapshotFile) {
        this.snapshotFile = snapshotFile;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
