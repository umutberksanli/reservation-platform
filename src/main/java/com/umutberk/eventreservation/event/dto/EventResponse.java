package com.umutberk.eventreservation.event.dto;

import java.time.Instant;

import com.umutberk.eventreservation.event.Event;

/** API'nin dondurdugu etkinlik gorunumu. Entity disariya dogrudan acilmaz. */
public record EventResponse(
        Long id,
        String name,
        Instant startsAt,
        int capacity,
        Instant createdAt) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getStartsAt(),
                event.getCapacity(),
                event.getCreatedAt());
    }
}
