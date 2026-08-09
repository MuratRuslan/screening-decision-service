package kg.tunduk.cvscan.screening.service;

import kg.tunduk.cvscan.screening.exception.BadRequestException;
import kg.tunduk.cvscan.screening.exception.NotFoundException;
import kg.tunduk.cvscan.screening.repository.DecisionAuditRepository;
import kg.tunduk.cvscan.screening.repository.ScreeningDecisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionQueryServiceTest {

    @Mock
    private ScreeningDecisionRepository decisionRepository;

    @Mock
    private DecisionAuditRepository auditRepository;

    private DecisionQueryService service;

    private DecisionQueryService service() {
        return new DecisionQueryService(decisionRepository, auditRepository);
    }

    @Test
    void defaultSortIsDecidedAtDescending() {
        service = service();
        when(decisionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, null, 0, 20, "decidedAt,desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(decisionRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("decidedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void acceptsAllowlistedSortFieldWithExplicitDirection() {
        service = service();
        when(decisionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, null, null, null, 0, 20, "score,asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(decisionRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("score");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsSortFieldNotOnTheAllowlist() {
        service = service();

        assertThatThrownBy(() -> service.list(null, null, null, null, null, 0, 20, "email,asc"))
                .isInstanceOf(BadRequestException.class);
        verify(decisionRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsInvalidSortDirection() {
        service = service();

        assertThatThrownBy(() -> service.list(null, null, null, null, null, 0, 20, "score,sideways"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void auditThrowsNotFoundWhenDecisionDoesNotExist() {
        service = service();
        UUID id = UUID.randomUUID();
        when(decisionRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.audit(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getThrowsNotFoundWhenDecisionDoesNotExist() {
        service = service();
        UUID id = UUID.randomUUID();
        when(decisionRepository.findById(id)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
    }
}
