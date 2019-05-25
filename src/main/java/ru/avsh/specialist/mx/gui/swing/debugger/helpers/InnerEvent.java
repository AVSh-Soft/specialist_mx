package ru.avsh.specialist.mx.gui.swing.debugger.helpers;

import ru.avsh.specialist.mx.gui.swing.debugger.types.EventType;

import java.util.Objects;

/**
 * Класс "Внутреннее событие".
 */
public class InnerEvent {
    private EventType type  ;
    private Object    detail;

    public InnerEvent(EventType type, Object detail) {
        this.type   = type  ;
        this.detail = detail;
    }

    public EventType getType() {
        return type;
    }

    public Object getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        InnerEvent event = (InnerEvent) o;
        return (type == event.type) && Objects.equals(detail, event.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, detail);
    }
}
