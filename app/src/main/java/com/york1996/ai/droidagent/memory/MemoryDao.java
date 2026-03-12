package com.york1996.ai.droidagent.memory;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MemoryDao {

    @Insert
    long insert(MemoryEntity entity);

    @Update
    void update(MemoryEntity entity);

    @Delete
    void delete(MemoryEntity entity);

    @Query("SELECT * FROM long_term_memory ORDER BY created_at DESC LIMIT :limit")
    List<MemoryEntity> getRecent(int limit);

    @Query("SELECT * FROM long_term_memory")
    List<MemoryEntity> getAll();

    @Query("SELECT * FROM long_term_memory WHERE session_id = :sessionId ORDER BY created_at DESC")
    List<MemoryEntity> getBySession(String sessionId);

    @Query("DELETE FROM long_term_memory WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM long_term_memory")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM long_term_memory")
    int count();

    /** 关键词模糊搜索（当 embedding 不可用时的降级方案） */
    @Query("SELECT * FROM long_term_memory WHERE content LIKE '%' || :keyword || '%' LIMIT :limit")
    List<MemoryEntity> searchByKeyword(String keyword, int limit);

    /** 更新访问记录 */
    @Query("UPDATE long_term_memory SET accessed_at = :now, access_count = access_count + 1 WHERE id = :id")
    void recordAccess(long id, long now);
}
