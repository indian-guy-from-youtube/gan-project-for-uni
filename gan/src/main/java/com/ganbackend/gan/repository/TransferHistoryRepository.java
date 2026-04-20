package com.ganbackend.gan.repository;
 
import com.ganbackend.gan.entity.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import java.util.List;
 
@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {
 
    @Query("SELECT h FROM TransferHistory h ORDER BY h.createdAt DESC LIMIT :n")
    List<TransferHistory> findTopN(@Param("n") int n);
}
 