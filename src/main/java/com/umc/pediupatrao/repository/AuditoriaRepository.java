package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Auditoria;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Estende Repository (e nao MongoRepository) para expor somente inclusao e consulta.
 * Assim nao ha delete/deleteAll disponiveis para os registros de auditoria.
 */
public interface AuditoriaRepository extends Repository<Auditoria, String> {

    <S extends Auditoria> S insert(S auditoria);

    List<Auditoria> findAllByOrderByDataHoraDesc();
}
