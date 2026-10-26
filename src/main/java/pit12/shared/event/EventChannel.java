package pit12.shared.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Dispatch keeps the current listener list even when a callback changes subscriptions. */
public final class EventChannel<T> {
    private volatile List<T> listeners = Collections.emptyList();

    public synchronized void add(T listener) {
        if (listeners.contains(listener))
            return;
        List<T> updated = new ArrayList<>(listeners);
        updated.add(listener);
        listeners = updated;
    }

    public synchronized void remove(T listener) {
        if (!listeners.contains(listener))
            return;
        List<T> updated = new ArrayList<>(listeners);
        updated.remove(listener);
        listeners = updated;
    }

    public boolean listening() {
        return !listeners.isEmpty();
    }

    public void publish(Consumer<T> callback) {
        List<T> current = listeners;
        for (int index = 0; index < current.size(); index++) {
            callback.accept(current.get(index));
        }
    }
}
