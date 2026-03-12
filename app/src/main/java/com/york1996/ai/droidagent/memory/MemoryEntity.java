package com.york1996.ai.droidagent.memory;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room 实体：长期记忆条目
 * embedding 序列化为逗号分隔的 float 字符串存储
 */
@Entity(tableName = "long_term_memory")
public class MemoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 记忆内容（原文） */
    @ColumnInfo(name = "content")
    public String content;

    /**
     * 向量嵌入，以逗号分隔的 float 字符串存储
     * 空字符串表示尚未生成 embedding（退化为关键词检索）
     */
    @ColumnInfo(name = "embedding")
    public String embedding = "";

    /** 标签（逗号分隔），可用于过滤 */
    @ColumnInfo(name = "tags")
    public String tags = "";

    /** 所属 Session ID，可用于隔离不同会话的记忆 */
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    /** 创建时间（毫秒时间戳） */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** 最近被访问时间（毫秒时间戳） */
    @ColumnInfo(name = "accessed_at")
    public long accessedAt;

    /** 访问次数（越高代表越重要） */
    @ColumnInfo(name = "access_count")
    public int accessCount = 0;
}
