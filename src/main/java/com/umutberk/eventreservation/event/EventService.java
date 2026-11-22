package com.umutberk.eventreservation.event;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.umutberk.eventreservation.event.dto.CreateEventRequest;
import com.umutberk.eventreservation.event.dto.EventResponse;

/** Etkinlik is mantigi. Controller ile veritabani arasindaki katman. */
@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = new Event(request.name().trim(), request.startsAt(), request.capacity());
        return EventResponse.from(eventRepository.saveAndFlush(event));
    }

    public List<EventResponse> findAll() {
        return eventRepository.findAllByOrderByStartsAtAscIdAsc().stream()
                .map(EventResponse::from)
                .toList();
    }

    public EventResponse findById(long id) {
        return eventRepository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
