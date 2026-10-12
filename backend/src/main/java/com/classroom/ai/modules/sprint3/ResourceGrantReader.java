package com.classroom.ai.modules.sprint3;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
/** A separate transaction sees committed revocations even after a long conversion. */
@Service @RequiredArgsConstructor
public class ResourceGrantReader {
 private final EntityManager em;
 @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
 public ResourceSeries read(Long id){return em.find(ResourceSeries.class,id);}
}
