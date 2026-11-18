package com.pedidos360.pagos.repository;

import com.pedidos360.pagos.model.AlertaFraude;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaFraudeRepository extends JpaRepository<AlertaFraude, Long> {
    List<AlertaFraude> findTop50ByOrderByFechaDesc();
}
